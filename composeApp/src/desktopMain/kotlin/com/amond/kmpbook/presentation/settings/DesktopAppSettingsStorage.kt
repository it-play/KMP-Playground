package com.amond.kmpbook.presentation.settings

import java.util.prefs.BackingStoreException
import java.util.prefs.Preferences

actual class AppSettingsStorage actual constructor() {
    private val preferences: Preferences? = try {
        Preferences.userRoot().node(PREFERENCES_NODE)
    } catch (_: SecurityException) {
        null
    }

    actual fun loadAudioSettings(): AudioSettings = try {
        AudioSettings(
            masterVolume = readVolume(KEY_MASTER_VOLUME, AudioSettings.DEFAULT_MASTER_VOLUME),
            musicVolume = readVolume(KEY_MUSIC_VOLUME, AudioSettings.DEFAULT_MUSIC_VOLUME),
            effectsVolume = readVolume(KEY_EFFECTS_VOLUME, AudioSettings.DEFAULT_EFFECTS_VOLUME),
            muted = preferences?.getBoolean(KEY_MUTED, false) ?: false,
        )
    } catch (_: SecurityException) {
        AudioSettings()
    }

    actual fun saveAudioSettings(settings: AudioSettings) {
        val preferences = preferences ?: return
        try {
            preferences.putDouble(KEY_MASTER_VOLUME, settings.masterVolume)
            preferences.putDouble(KEY_MUSIC_VOLUME, settings.musicVolume)
            preferences.putDouble(KEY_EFFECTS_VOLUME, settings.effectsVolume)
            preferences.putBoolean(KEY_MUTED, settings.muted)
            preferences.flush()
        } catch (_: SecurityException) {
            // The in-memory app setting remains usable when the OS preference store is unavailable.
        } catch (_: BackingStoreException) {
            // The in-memory app setting remains usable when the OS preference store is unavailable.
        }
    }

    private fun readVolume(key: String, defaultValue: Double): Double =
        preferences
            ?.getDouble(key, defaultValue)
            ?.takeIf { it.isFinite() && it in 0.0..1.0 }
            ?: defaultValue

    private companion object {
        const val PREFERENCES_NODE: String = "com/amond/kmpbook"
        const val KEY_MASTER_VOLUME: String = "audio.masterVolume"
        const val KEY_MUSIC_VOLUME: String = "audio.musicVolume"
        const val KEY_EFFECTS_VOLUME: String = "audio.effectsVolume"
        const val KEY_MUTED: String = "audio.muted"
    }
}
