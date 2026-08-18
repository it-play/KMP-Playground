package com.amond.kmpbook.presentation.settings

expect class AppSettingsStorage() {
    fun loadAudioSettings(): AudioSettings

    fun saveAudioSettings(settings: AudioSettings)

    fun loadWindowDisplayMode(): WindowDisplayMode

    fun saveWindowDisplayMode(mode: WindowDisplayMode)
}
