package com.amond.kmpbook.ui.theme

import androidx.compose.ui.text.font.FontFamily

/** Returns the optional, legally installed desktop family when available. */
internal expect fun platformPreferredMarketFontFamily(): FontFamily?
