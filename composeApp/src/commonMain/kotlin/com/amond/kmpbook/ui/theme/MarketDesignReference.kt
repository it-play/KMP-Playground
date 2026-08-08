package com.amond.kmpbook.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class MarketDesignReference(
    val title: String,
    val url: String,
) {
    init {
        require(title.isNotBlank())
        require(url.startsWith("https://"))
    }
}
