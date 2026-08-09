package com.amond.kmpbook.presentation.news


data class NewsStockGroupUi(
    val key: String,
    val stockId: String,
    val label: String,
    val detail: String,
    val count: Int,
    val held: Boolean,
    val watched: Boolean,
    val directTarget: Boolean,
    val specificity: Int,
    val relationKind: NewsStockRelationKind,
)
