package com.amond.kmpbook.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class MarketTrend(val label: String, val sign: String) {
    RISE("상승", "+"),
    FALL("하락", "−"),
    FLAT("보합", "±"),
}
