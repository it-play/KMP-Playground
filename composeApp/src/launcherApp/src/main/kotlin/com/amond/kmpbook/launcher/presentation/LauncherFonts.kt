package com.amond.kmpbook.launcher.presentation

import java.awt.Font

internal object LauncherFonts {
    private val regularFace = load("/launcher/fonts/pretendard_regular.otf", Font.PLAIN)
    private val boldFace = load("/launcher/fonts/pretendard_bold.otf", Font.BOLD)

    fun regular(size: Float): Font = regularFace.deriveFont(size)

    fun bold(size: Float): Font = boldFace.deriveFont(size)

    private fun load(resourcePath: String, fallbackStyle: Int): Font {
        val fallback = Font(Font.SANS_SERIF, fallbackStyle, DEFAULT_SIZE)
        val stream = LauncherFonts::class.java.getResourceAsStream(resourcePath) ?: return fallback
        val loaded = try {
            stream.use { Font.createFont(Font.TRUETYPE_FONT, it) }
        } catch (_: Exception) {
            return fallback
        }
        return if (loaded.canDisplayUpTo(KOREAN_GLYPH_SAMPLE) == -1) loaded else fallback
    }

    private const val DEFAULT_SIZE = 12
    private const val KOREAN_GLYPH_SAMPLE = "확인 중 설치 실행 다시 시도"
}
