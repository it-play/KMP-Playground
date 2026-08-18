package com.amond.kmpbook.launcher.debug.installation

import com.amond.kmpbook.launcher.diagnostics.LauncherLogger
import com.amond.kmpbook.launcher.filesystem.LauncherPaths
import com.amond.kmpbook.launcher.filesystem.SafeFiles
import com.amond.kmpbook.launcher.filesystem.SafePathPolicy
import com.amond.kmpbook.launcher.filesystem.SecureZipExtractor
import com.amond.kmpbook.launcher.filesystem.ZipExtractionLimits
import com.amond.kmpbook.launcher.foundation.LauncherException
import com.amond.kmpbook.launcher.release.artifact.ArtifactStore
import com.amond.kmpbook.launcher.verification.BuildCohortVerifier
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.invariantSeparatorsPathString

internal class DebugBundleInstaller(
    private val paths: LauncherPaths,
    private val extractor: SecureZipExtractor,
    private val cohortVerifier: BuildCohortVerifier,
    private val artifacts: ArtifactStore,
    private val logger: LauncherLogger,
) {
    private val target = paths.mods.resolve(DEBUG_BUNDLE_DIRECTORY)
    private val backup = paths.mods.resolve(DEBUG_BACKUP_DIRECTORY)

    fun recoverInterruptedReplacement(activeCohort: String?) {
        if (Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                SafeFiles.atomicMoveDirectory(backup, target)
            } else if (activeCohort != null && isBundleForCohort(target, activeCohort)) {
                SafeFiles.deleteOwnedTree(backup, paths.mods)
            } else {
                SafeFiles.deleteOwnedTree(target, paths.mods)
                SafeFiles.atomicMoveDirectory(backup, target)
            }
        }
        Files.list(paths.mods).use { entries ->
            entries.filter { it.fileName.toString().startsWith(DEBUG_STAGING_PREFIX) }
                .toList()
                .forEach { stale -> SafeFiles.deleteOwnedTree(stale, paths.mods) }
        }
    }

    private fun isBundleForCohort(root: Path, cohort: String): Boolean = runCatching {
        verifyClosure(root)
        cohortVerifier.verifyDebugBundle(root, cohort)
    }.isSuccess

    fun prepare(archive: Path, expectedCohort: String): PreparedDebugBundle {
        val staging = paths.mods.resolve("$DEBUG_STAGING_PREFIX${UUID.randomUUID()}")
        try {
            extractor.extract(archive, staging, ZipExtractionLimits.DEBUG_BUNDLE)
            verifyClosure(staging)
            cohortVerifier.verifyDebugBundle(staging, expectedCohort)
            return PreparedDebugBundle(staging)
        } catch (error: Exception) {
            runCatching { SafeFiles.deleteOwnedTree(staging, paths.mods) }
            if (error is LauncherException) runCatching { artifacts.quarantine(archive, error.diagnosticCode) }
            if (error is LauncherException) throw error
            throw LauncherException("debug-install", "디버그 번들을 검증하지 못했습니다.", error)
        }
    }

    fun commit(prepared: PreparedDebugBundle): DebugBundleCommit {
        if (prepared.staging.parent.toAbsolutePath().normalize() != paths.mods.toAbsolutePath().normalize()) {
            throw LauncherException("debug-staging-scope", "디버그 번들 staging 경로가 허용 영역을 벗어났습니다.")
        }
        if (Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
            throw LauncherException("debug-backup-exists", "이전 디버그 번들 교체를 복구하지 못했습니다.")
        }
        val hadPrevious = Files.exists(target, LinkOption.NOFOLLOW_LINKS)
        if (hadPrevious) SafeFiles.atomicMoveDirectory(target, backup)
        try {
            SafeFiles.atomicMoveDirectory(prepared.staging, target)
        } catch (error: Exception) {
            if (hadPrevious && Files.exists(backup, LinkOption.NOFOLLOW_LINKS) &&
                !Files.exists(target, LinkOption.NOFOLLOW_LINKS)
            ) {
                runCatching { SafeFiles.atomicMoveDirectory(backup, target) }
            }
            throw error
        }
        return DebugBundleCommit(
            completeAction = {
                if (Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
                    runCatching { SafeFiles.deleteOwnedTree(backup, paths.mods) }
                        .onFailure { logger.info("debug-backup-cleanup-deferred") }
                }
            },
            rollbackAction = {
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    SafeFiles.deleteOwnedTree(target, paths.mods)
                }
                if (hadPrevious && Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
                    SafeFiles.atomicMoveDirectory(backup, target)
                }
            },
        )
    }

    private fun verifyClosure(root: Path) {
        val actualFiles = linkedSetOf<String>()
        Files.walk(root).use { entries ->
            entries.forEach { path ->
                if (path == root) return@forEach
                if (Files.isSymbolicLink(path)) {
                    throw LauncherException("debug-symlink", "디버그 번들에는 심볼릭 링크를 사용할 수 없습니다.")
                }
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    actualFiles += root.relativize(path).invariantSeparatorsPathString
                } else if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw LauncherException("debug-file-type", "디버그 번들에 허용되지 않는 파일 종류가 있습니다.")
                }
            }
        }
        if (actualFiles != EXPECTED_FILES) {
            throw LauncherException("debug-closure", "디버그 번들 파일 집합이 허용 정책과 정확히 일치하지 않습니다.")
        }
        val signature = SafePathPolicy.resolve(root, "META-INF/market-ledger/signature.ed25519")
        if (Files.size(signature) != ED25519_SIGNATURE_BYTES.toLong()) {
            throw LauncherException("debug-signature-size", "디버그 번들 내부 서명 길이가 올바르지 않습니다.")
        }
    }

    private companion object {
        const val DEBUG_BUNDLE_DIRECTORY = "market-ledger.debug"
        const val DEBUG_BACKUP_DIRECTORY = ".market-ledger.debug.backup"
        const val DEBUG_STAGING_PREFIX = ".market-ledger.debug.staging-"
        const val ED25519_SIGNATURE_BYTES = 64
        val EXPECTED_FILES = linkedSetOf(
            "cover.png",
            "lib/market-ledger-debug.jar",
            "manifest.xml",
            "trust/challenge.dat",
            "META-INF/market-ledger/bundle.integrity",
            "META-INF/market-ledger/signature.ed25519",
        )
    }
}
