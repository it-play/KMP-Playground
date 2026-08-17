package com.amond.kmpbook.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import java.awt.GraphicsEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalTextApi::class)
@Composable
internal actual fun rememberPlatformPreferredMarketFontFamily(): FontFamily? {
    val family by produceState<FontFamily?>(initialValue = null) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                val installedName = GraphicsEnvironment
                    .getLocalGraphicsEnvironment()
                    .availableFontFamilyNames
                    .firstOrNull {
                        it.equals(MarketDesignSystem.PREFERRED_DESKTOP_FONT_FAMILY, ignoreCase = true)
                    }
                    ?: return@runCatching null

                FontFamily(installedName)
            }.getOrNull()
        }
    }
    return family
}
