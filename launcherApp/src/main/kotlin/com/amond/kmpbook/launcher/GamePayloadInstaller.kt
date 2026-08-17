package com.amond.kmpbook.launcher

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.UUID

internal class GamePayloadInstaller(
    private val paths: LauncherPaths,
    private val extractor: SecureZipExtractor,
    private val inventoryVerifier: GameInventoryVerifier,
    private val cohortVerifier: BuildCohortVerifier,
    private val artifacts: ArtifactStore,
) {
    fun installOrVerify(
        document: VerifiedFeedDocument,
        archive: Path,
        inventory: GameInventory,
    ): Path {
        val directoryName = VersionNaming.directoryName(document.feed)
        val destination = paths.versions.resolve(directoryName)
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            verify(destination, document.feed, inventory)
            return destination
        }
        val staging = paths.staging.resolve("game-$directoryName-${UUID.randomUUID()}")
        try {
            extractor.extract(archive, staging, ZipExtractionLimits.GAME)
            verify(staging, document.feed, inventory)
            SafeFiles.atomicMoveDirectory(staging, destination)
            return destination
        } catch (error: Exception) {
            runCatching { SafeFiles.deleteOwnedTree(staging, paths.staging) }
            if (error is LauncherException && error.diagnosticCode.startsWith("zip-") ||
                error is LauncherException && error.diagnosticCode.startsWith("inventory-") ||
                error is LauncherException && error.diagnosticCode.startsWith("game-cohort")
            ) {
                runCatching { artifacts.quarantine(archive, error.diagnosticCode) }
            }
            if (error is LauncherException) throw error
            throw LauncherException("game-install", "검증된 게임 본체를 설치하지 못했습니다.", error)
        }
    }

    private fun verify(root: Path, feed: StableFeed, inventory: GameInventory) {
        inventoryVerifier.verify(root, inventory)
        cohortVerifier.verifyGame(root, feed.buildCohort)
        val executable = SafePathPolicy.resolve(root, feed.game.entryPoint)
        if (Files.isSymbolicLink(executable) || !Files.isRegularFile(executable, LinkOption.NOFOLLOW_LINKS)) {
            throw LauncherException("game-executable", "게임 payload에 실행 파일이 없습니다.")
        }
    }
}
