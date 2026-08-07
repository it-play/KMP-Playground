package com.amond.kmpbook.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import java.awt.GraphicsEnvironment

@OptIn(ExperimentalTextApi::class)
internal actual fun platformPreferredMarketFontFamily(): FontFamily? = runCatching {
    val installedName = GraphicsEnvironment
        .getLocalGraphicsEnvironment()
        .availableFontFamilyNames
        .firstOrNull {
            it.equals(MarketDesignSystem.PREFERRED_DESKTOP_FONT_FAMILY, ignoreCase = true)
        }
        ?: return@runCatching null

    FontFamily(installedName)
}.getOrNull()
