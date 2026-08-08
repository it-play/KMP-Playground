package com.amond.kmpbook.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class MarketTrendVisual(
    val trend: MarketTrend,
    val color: Color,
) {
    val label: String get() = trend.label
    val sign: String get() = trend.sign
}
