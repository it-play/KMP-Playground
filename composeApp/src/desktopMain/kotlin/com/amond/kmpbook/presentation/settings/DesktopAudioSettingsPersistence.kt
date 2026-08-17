package com.amond.kmpbook.presentation.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class DesktopAudioSettingsPersistence : AutoCloseable {
    private val storage = AppSettingsStorage()
    private val dispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "market-ledger-audio-settings").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val closed = AtomicBoolean(false)
    private val latestSettings = AtomicReference<AudioSettings?>(null)

    private var pendingSave: Job? = null

    suspend fun load(): AudioSettings {
        check(!closed.get()) { "오디오 설정 저장소가 이미 종료되었습니다." }
        return withContext(dispatcher) {
            storage.loadAudioSettings().also(latestSettings::set)
        }
    }

    fun scheduleSave(settings: AudioSettings) {
        if (closed.get()) return
        latestSettings.set(settings)
        pendingSave?.cancel()
        pendingSave = scope.launch {
            delay(SAVE_DEBOUNCE_MILLIS)
            storage.saveAudioSettings(settings)
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            val settingsToSave = latestSettings.get()
            runBlocking {
                withContext(dispatcher) {
                    pendingSave?.cancel()
                    pendingSave = null
                    if (settingsToSave != null) {
                        storage.saveAudioSettings(settingsToSave)
                    }
                }
            }
        } finally {
            scope.cancel()
            dispatcher.close()
        }
    }

    private companion object {
        const val SAVE_DEBOUNCE_MILLIS: Long = 250L
    }
}
