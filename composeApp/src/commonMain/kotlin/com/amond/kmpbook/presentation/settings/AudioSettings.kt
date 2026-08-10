package com.amond.kmpbook.presentation.settings

data class AudioSettings(
    val masterVolume: Double = DEFAULT_MASTER_VOLUME,
    val musicVolume: Double = DEFAULT_MUSIC_VOLUME,
    val effectsVolume: Double = DEFAULT_EFFECTS_VOLUME,
    val muted: Boolean = false,
) {
    init {
        require(masterVolume.isValidVolume()) { "전체 음량은 0과 1 사이여야 합니다." }
        require(musicVolume.isValidVolume()) { "배경음악 음량은 0과 1 사이여야 합니다." }
        require(effectsVolume.isValidVolume()) { "효과음 음량은 0과 1 사이여야 합니다." }
    }

    companion object {
        const val DEFAULT_MASTER_VOLUME: Double = 0.8
        const val DEFAULT_MUSIC_VOLUME: Double = 0.65
        const val DEFAULT_EFFECTS_VOLUME: Double = 0.8
    }
}

private fun Double.isValidVolume(): Boolean = isFinite() && this in 0.0..1.0
