package com.amond.kmpbook.launcher.presentation

import com.amond.kmpbook.launcher.application.LauncherUpdateService
import com.amond.kmpbook.launcher.application.PreparedLaunch
import com.amond.kmpbook.launcher.application.ProgressUpdate
import com.amond.kmpbook.launcher.diagnostics.LauncherLogger
import com.amond.kmpbook.launcher.foundation.LauncherException
import com.amond.kmpbook.launcher.game.runtime.GameProcessLauncher
import com.amond.kmpbook.launcher.lifecycle.SingleInstanceLock
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal class LauncherController(
    private val frame: LauncherFrame,
    private val updateService: LauncherUpdateService,
    private val gameLauncher: GameProcessLauncher,
    private val instanceLock: SingleInstanceLock,
    private val logger: LauncherLogger,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val working = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "market-ledger-launcher-worker").apply { isDaemon = true }
    }
    @Volatile
    private var prepared: PreparedLaunch? = null

    fun start() = runPreparation()

    fun retry() = runPreparation()

    fun play() {
        val ready = prepared ?: return
        frame.setPlayEnabled(false)
        executor.execute {
            try {
                gameLauncher.launch(ready.installation)
                logger.info("game-started:${ready.installation.directoryName}")
                javax.swing.SwingUtilities.invokeLater { frame.dispose() }
            } catch (error: LauncherException) {
                logger.error(error)
                frame.showLaunchError()
            }
        }
    }

    private fun runPreparation() {
        if (closed.get() || !working.compareAndSet(false, true)) return
        prepared = null
        frame.showWorking(ProgressUpdate("확인 중"))
        executor.execute {
            try {
                val result = updateService.prepare(frame::showWorking)
                prepared = result
                frame.showReady(result)
            } catch (error: LauncherException) {
                logger.error(error)
                frame.showError()
            } catch (error: Exception) {
                logger.error(error)
                frame.showError()
            } finally {
                working.set(false)
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        executor.shutdownNow()
        instanceLock.close()
    }
}
