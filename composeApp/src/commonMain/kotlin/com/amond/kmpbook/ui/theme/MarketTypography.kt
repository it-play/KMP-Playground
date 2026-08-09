package com.amond.kmpbook.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle

@Immutable
data class MarketTypography(
    val display: TextStyle,
    val headingLarge: TextStyle,
    val heading: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val number: TextStyle,
    val numberLarge: TextStyle,
)
