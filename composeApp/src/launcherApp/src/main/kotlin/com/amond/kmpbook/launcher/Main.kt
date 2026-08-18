package com.amond.kmpbook.launcher

import com.amond.kmpbook.launcher.application.LauncherUpdateService
import com.amond.kmpbook.launcher.debug.installation.DebugBundleInstaller
import com.amond.kmpbook.launcher.diagnostics.LauncherLogger
import com.amond.kmpbook.launcher.filesystem.LauncherPaths
import com.amond.kmpbook.launcher.filesystem.SecureZipExtractor
import com.amond.kmpbook.launcher.foundation.LauncherException
import com.amond.kmpbook.launcher.game.installation.ActiveInstallationResolver
import com.amond.kmpbook.launcher.game.installation.GamePayloadInstaller
import com.amond.kmpbook.launcher.game.installation.InstallationRecordStore
import com.amond.kmpbook.launcher.game.inventory.GameInventoryParser
import com.amond.kmpbook.launcher.game.inventory.GameInventoryVerifier
import com.amond.kmpbook.launcher.game.runtime.GameProcessLauncher
import com.amond.kmpbook.launcher.lifecycle.SingleInstanceLock
import com.amond.kmpbook.launcher.presentation.LauncherController
import com.amond.kmpbook.launcher.presentation.LauncherFrame
import com.amond.kmpbook.launcher.release.artifact.ArtifactStore
import com.amond.kmpbook.launcher.release.feed.FeedSignatureVerifier
import com.amond.kmpbook.launcher.release.feed.ReleaseFloor
import com.amond.kmpbook.launcher.release.feed.StableFeedParser
import com.amond.kmpbook.launcher.release.feed.StableReleaseSource
import com.amond.kmpbook.launcher.verification.BuildCohortVerifier
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import javax.swing.UIManager

fun main() {
    val paths = try {
        LauncherPaths.discover().also(LauncherPaths::createRequiredDirectories)
    } catch (error: Exception) {
        showFatal("게임 데이터 폴더를 만들지 못했습니다.")
        return
    }
    val logger = LauncherLogger(paths.state.resolve("launcher.log"))
    val instanceLock = try {
        SingleInstanceLock.acquire(paths.state.resolve("launcher.lock"))
    } catch (error: LauncherException) {
        logger.error(error)
        showFatal(error.message)
        return
    }
    if (instanceLock == null) {
        showFatal("Market Ledger 2040 Launcher가 이미 실행 중입니다.")
        return
    }

    SwingUtilities.invokeLater {
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
        try {
            val signatureVerifier = FeedSignatureVerifier.fromEmbeddedKey()
            val feedParser = StableFeedParser()
            val releaseSource = StableReleaseSource(signatureVerifier, feedParser)
            val artifactStore = ArtifactStore(paths, logger)
            val inventoryParser = GameInventoryParser()
            val extractor = SecureZipExtractor()
            val inventoryVerifier = GameInventoryVerifier()
            val cohortVerifier = BuildCohortVerifier()
            val records = InstallationRecordStore(paths, releaseSource, inventoryParser)
            val gameInstaller = GamePayloadInstaller(
                paths,
                extractor,
                inventoryVerifier,
                cohortVerifier,
                artifactStore,
            )
            val debugInstaller = DebugBundleInstaller(paths, extractor, cohortVerifier, artifactStore, logger)
            val activeResolver = ActiveInstallationResolver(paths, records, inventoryVerifier, cohortVerifier)
            val updateService = LauncherUpdateService(
                releaseSource,
                artifactStore,
                inventoryParser,
                gameInstaller,
                debugInstaller,
                records,
                activeResolver,
                ReleaseFloor.fromEmbeddedResource(),
                logger,
            )

            lateinit var controller: LauncherController
            val frame = LauncherFrame(
                onRetry = { controller.retry() },
                onPlay = { controller.play() },
                onClosed = { controller.close() },
            )
            controller = LauncherController(
                frame,
                updateService,
                GameProcessLauncher(paths, inventoryVerifier, cohortVerifier),
                instanceLock,
                logger,
            )
            frame.isVisible = true
            controller.start()
        } catch (error: LauncherException) {
            logger.error(error)
            instanceLock.close()
            showFatal(error.message)
        } catch (error: Exception) {
            logger.error(error)
            instanceLock.close()
            showFatal("런처를 초기화하지 못했습니다.")
        }
    }
}

private fun showFatal(message: String) {
    JOptionPane.showMessageDialog(
        null,
        message,
        "Market Ledger 2040 Launcher",
        JOptionPane.ERROR_MESSAGE,
    )
}
