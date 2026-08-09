package com.amond.kmpbook.ui.theme

import androidx.compose.runtime.Immutable

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
