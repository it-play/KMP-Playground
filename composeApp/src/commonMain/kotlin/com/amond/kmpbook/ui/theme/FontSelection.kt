package com.amond.kmpbook.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

/** Returns the optional, legally installed desktop family when available. */
@Composable
internal expect fun rememberPlatformPreferredMarketFontFamily(): FontFamily?
