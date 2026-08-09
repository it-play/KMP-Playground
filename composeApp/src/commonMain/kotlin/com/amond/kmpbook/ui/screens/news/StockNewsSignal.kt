package com.amond.kmpbook.ui.screens.news

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.amond.kmpbook.presentation.news.NewsRelatedStockUi
import com.amond.kmpbook.presentation.news.NewsStoryUi

internal data class StockNewsSignal(
    val story: NewsStoryUi,
    val relation: NewsRelatedStockUi,
    val pathNodes: List<String>,
) {
    val hasCausalTrace: Boolean
        get() = pathNodes.size >= 2

    val causalPath: String
        get() = pathNodes.joinToString(" → ")
}
