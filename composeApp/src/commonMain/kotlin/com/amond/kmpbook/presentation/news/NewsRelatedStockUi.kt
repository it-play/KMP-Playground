package com.amond.kmpbook.presentation.news

import com.amond.kmpbook.domain.model.event.ImpactDirection

data class NewsRelatedStockUi(
    val stockId: String,
    val name: String,
    val symbol: String,
    val direction: ImpactDirection,
    val reason: String,
    val held: Boolean,
    val watched: Boolean,
    val directTarget: Boolean,
    val specificity: Int,
    val relationKind: NewsStockRelationKind,
    val causalTraceLabels: List<String>,
    val relativeSensitivity: Double,
    val confidence: Double,
)
