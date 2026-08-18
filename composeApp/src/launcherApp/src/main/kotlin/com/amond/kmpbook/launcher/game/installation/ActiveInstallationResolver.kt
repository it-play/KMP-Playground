package com.amond.kmpbook.launcher.game.installation

import com.amond.kmpbook.launcher.filesystem.LauncherPaths
import com.amond.kmpbook.launcher.filesystem.SafePathPolicy
import com.amond.kmpbook.launcher.foundation.LauncherException
import com.amond.kmpbook.launcher.game.inventory.GameInventoryVerifier
import com.amond.kmpbook.launcher.verification.BuildCohortVerifier
import java.nio.file.Files
import java.nio.file.LinkOption

internal class ActiveInstallationResolver(
    private val paths: LauncherPaths,
    private val records: InstallationRecordStore,
    private val inventoryVerifier: GameInventoryVerifier,
    private val cohortVerifier: BuildCohortVerifier,
) {
    fun resolveOrNull(): ActiveInstallation? {
        val directoryName = records.activeDirectoryNameOrNull() ?: return null
        val record = records.load(directoryName)
        val root = paths.versions.resolve(directoryName)
        inventoryVerifier.verify(root, record.inventory)
        cohortVerifier.verifyGame(root, record.document.feed.buildCohort)
        val executable = SafePathPolicy.resolve(root, record.document.feed.game.entryPoint)
        if (Files.isSymbolicLink(executable) || !Files.isRegularFile(executable, LinkOption.NOFOLLOW_LINKS)) {
            throw LauncherException("game-executable", "active 게임 실행 파일이 안전한 일반 파일이 아닙니다.")
        }
        return ActiveInstallation(directoryName, root, executable, record)
    }
}
