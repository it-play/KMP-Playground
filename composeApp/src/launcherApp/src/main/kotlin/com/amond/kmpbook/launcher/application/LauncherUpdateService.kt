package com.amond.kmpbook.launcher.application

import com.amond.kmpbook.launcher.debug.installation.DebugBundleInstaller
import com.amond.kmpbook.launcher.diagnostics.LauncherLogger
import com.amond.kmpbook.launcher.filesystem.SafePathPolicy
import com.amond.kmpbook.launcher.foundation.DigestUtils
import com.amond.kmpbook.launcher.foundation.LauncherException
import com.amond.kmpbook.launcher.game.installation.ActiveInstallation
import com.amond.kmpbook.launcher.game.installation.ActiveInstallationResolver
import com.amond.kmpbook.launcher.game.installation.GamePayloadInstaller
import com.amond.kmpbook.launcher.game.installation.InstallationRecord
import com.amond.kmpbook.launcher.game.installation.InstallationRecordStore
import com.amond.kmpbook.launcher.game.installation.VersionNaming
import com.amond.kmpbook.launcher.game.inventory.GameInventoryParser
import com.amond.kmpbook.launcher.release.artifact.ArtifactStore
import com.amond.kmpbook.launcher.release.feed.ReleaseFloor
import com.amond.kmpbook.launcher.release.feed.StableReleaseSource
import com.amond.kmpbook.launcher.release.model.StableFeed
import java.nio.file.Files

internal class LauncherUpdateService(
    private val releaseSource: StableReleaseSource,
    private val artifacts: ArtifactStore,
    private val inventoryParser: GameInventoryParser,
    private val gameInstaller: GamePayloadInstaller,
    private val debugInstaller: DebugBundleInstaller,
    private val records: InstallationRecordStore,
    private val activeResolver: ActiveInstallationResolver,
    private val releaseFloor: ReleaseFloor,
    private val logger: LauncherLogger,
) {
    fun prepare(progress: ProgressSink): PreparedLaunch {
        progress.report(ProgressUpdate("확인 중"))
        val activeFeed = loadTrustedActiveFeedOrNull()
        debugInstaller.recoverInterruptedReplacement(activeFeed?.buildCohort)
        return try {
            PreparedLaunch(update(progress, activeFeed))
        } catch (rawUpdateError: Exception) {
            val updateError = rawUpdateError as? LauncherException
                ?: LauncherException("update-io", "업데이트 중 안전하게 처리할 수 없는 I/O 오류가 발생했습니다.", rawUpdateError)
            logger.error(updateError)
            progress.report(ProgressUpdate("확인 중"))
            val active = try {
                activeResolver.resolveOrNull()
            } catch (activeError: LauncherException) {
                logger.error(activeError)
                throw LauncherException(
                    "active-fallback-failed",
                    "업데이트에 실패했고 기존 active 게임도 안전하게 검증하지 못했습니다.",
                    activeError,
                )
            }
            if (active == null) throw updateError
            if (compareVersions(
                    active.record.document.feed.version,
                    releaseFloor.minimumGameVersion,
                ) < 0
            ) {
                throw LauncherException(
                    "active-below-launcher-floor",
                    "기존 active 게임이 이 런처의 최소 허용 버전보다 오래되었습니다.",
                    updateError,
                )
            }
            PreparedLaunch(
                installation = active,
                warning = "업데이트 실패 · 기존 버전 실행 가능",
            )
        }
    }

    private fun update(progress: ProgressSink, activeFeed: StableFeed?): ActiveInstallation {
        progress.report(ProgressUpdate("확인 중", 0.02))
        val document = releaseSource.load()
        enforceMonotonicRelease(document.feed, activeFeed)
        resolveCurrentInstallation(progress, document.feed, activeFeed)?.let { return it }

        progress.report(ProgressUpdate("설치 준비 중", 0.06))
        val inventoryPath = artifacts.obtain(document.feed.game.inventory, "json") { copied, total ->
            progress.report(ProgressUpdate("설치 준비 중", scaled(copied, total, 0.06, 0.10)))
        }
        val inventoryBytes = Files.readAllBytes(inventoryPath)
        if (inventoryBytes.size.toLong() != document.feed.game.inventory.size ||
            !DigestUtils.constantTimeEquals(DigestUtils.sha256(inventoryBytes), document.feed.game.inventory.sha256)
        ) {
            artifacts.quarantine(inventoryPath, "inventory-readback")
            throw LauncherException("inventory-readback", "캐시된 inventory가 읽는 동안 변경되었습니다.")
        }
        val inventory = inventoryParser.parse(inventoryBytes)
        if (inventory.files.none { it.path == document.feed.game.entryPoint }) {
            throw LauncherException("inventory-entrypoint", "게임 실행 파일이 signed inventory에 없습니다.")
        }

        progress.report(ProgressUpdate("설치 준비 중", 0.10))
        val gameArchive = artifacts.obtain(document.feed.game.archive, "zip") { copied, total ->
            progress.report(ProgressUpdate("설치 준비 중", scaled(copied, total, 0.10, 0.67)))
        }
        progress.report(ProgressUpdate("설치 중", 0.68))
        val gameRoot = gameInstaller.installOrVerify(document, gameArchive, inventory)

        progress.report(ProgressUpdate("설치 중", 0.76))
        val debugArchive = artifacts.obtain(document.feed.debugBundle, "zip") { copied, total ->
            progress.report(ProgressUpdate("설치 중", scaled(copied, total, 0.76, 0.88)))
        }
        progress.report(ProgressUpdate("설치 중", 0.89))
        val preparedDebug = debugInstaller.prepare(debugArchive, document.feed.buildCohort)
        val directoryName = VersionNaming.directoryName(document.feed)
        records.save(directoryName, document, inventoryBytes)

        progress.report(ProgressUpdate("마무리 중", 0.96))
        val debugCommit = debugInstaller.commit(preparedDebug)
        try {
            records.activate(directoryName)
        } catch (error: Exception) {
            runCatching { debugCommit.rollback() }
            throw error
        }
        debugCommit.complete()
        logger.info("release-activated:$directoryName")

        val executable = SafePathPolicy.resolve(gameRoot, document.feed.game.entryPoint)
        val record = InstallationRecord(document, inventoryBytes, inventory)
        progress.report(ProgressUpdate("마무리 중", 1.0))
        return ActiveInstallation(directoryName, gameRoot, executable, record)
    }

    private fun resolveCurrentInstallation(
        progress: ProgressSink,
        candidate: StableFeed,
        activeFeed: StableFeed?,
    ): ActiveInstallation? {
        if (candidate != activeFeed) return null

        progress.report(ProgressUpdate("파일 확인 중", 0.08))
        val active = try {
            activeResolver.resolveOrNull()
        } catch (error: LauncherException) {
            logger.error(error)
            return null
        } ?: return null
        if (active.record.document.feed != candidate) return null

        progress.report(ProgressUpdate("파일 확인 중", 0.88))
        val debugArchive = artifacts.obtain(candidate.debugBundle, "zip") { copied, total ->
            progress.report(ProgressUpdate("파일 확인 중", scaled(copied, total, 0.88, 0.96)))
        }
        if (!debugInstaller.isInstalledFrom(debugArchive, candidate.buildCohort)) return null

        logger.info("release-current:${active.directoryName}")
        progress.report(ProgressUpdate("실행 준비 중", 1.0))
        return active
    }

    private fun loadTrustedActiveFeedOrNull(): StableFeed? {
        val activeName = try {
            records.activeDirectoryNameOrNull()
        } catch (error: LauncherException) {
            logger.error(error)
            return null
        } ?: return null
        return try {
            records.load(activeName).document.feed
        } catch (error: LauncherException) {
            logger.error(error)
            null
        }
    }

    private fun enforceMonotonicRelease(candidate: StableFeed, activeFeed: StableFeed?) {
        if (compareVersions(candidate.version, releaseFloor.minimumGameVersion) < 0) {
            throw LauncherException("release-below-launcher-floor", "서명된 feed가 이 런처의 최소 게임 버전보다 오래되었습니다.")
        }
        if (activeFeed == null) return
        val versionOrder = compareVersions(candidate.version, activeFeed.version)
        if (versionOrder < 0 || candidate.publishedAt.isBefore(activeFeed.publishedAt)) {
            throw LauncherException("release-rollback", "서명된 feed가 현재 active 버전보다 오래되었습니다.")
        }
        if (versionOrder == 0 && candidate != activeFeed) {
            throw LauncherException("release-version-reuse", "같은 버전 번호가 서로 다른 signed release에 재사용되었습니다.")
        }
    }

    private fun compareVersions(left: String, right: String): Int {
        val leftParts = left.split('.').map(String::toInt)
        val rightParts = right.split('.').map(String::toInt)
        return leftParts.zip(rightParts).firstOrNull { (a, b) -> a != b }
            ?.let { (a, b) -> a.compareTo(b) }
            ?: 0
    }

    private fun scaled(value: Long, total: Long, start: Double, end: Double): Double {
        if (total <= 0) return start
        return (start + (end - start) * (value.toDouble() / total.toDouble())).coerceIn(start, end)
    }
}
