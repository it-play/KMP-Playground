package com.amond.kmpbook.launcher

import java.nio.file.Files
import java.nio.file.LinkOption

internal class GameProcessLauncher(
    private val paths: LauncherPaths,
    private val inventoryVerifier: GameInventoryVerifier,
    private val cohortVerifier: BuildCohortVerifier,
) {
    fun launch(installation: ActiveInstallation): Process {
        inventoryVerifier.verify(installation.root, installation.record.inventory)
        cohortVerifier.verifyGame(
            installation.root,
            installation.record.document.feed.buildCohort,
        )
        val executable = installation.executable
        if (!executable.normalize().startsWith(installation.root.normalize()) ||
            Files.isSymbolicLink(executable) || !Files.isRegularFile(executable, LinkOption.NOFOLLOW_LINKS)
        ) {
            throw LauncherException("launch-executable", "검증된 게임 실행 파일을 찾을 수 없습니다.")
        }
        return try {
            ProcessBuilder(executable.toString())
                .directory(installation.root.toFile())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .apply {
                    environment()["MARKET_LEDGER_USER_DATA_DIR"] = paths.userRoot.toString()
                    environment()["MARKET_LEDGER_LOCAL_DATA_DIR"] = paths.localRoot.toString()
                    environment()["MARKET_LEDGER_BUILD_COHORT"] = installation.record.document.feed.buildCohort
                }
                .start()
        } catch (error: Exception) {
            throw LauncherException("game-start", "게임 프로세스를 시작하지 못했습니다.", error)
        }
    }
}
