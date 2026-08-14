package com.amond.kmpbook.ui.charts.trading

import kotlinx.serialization.Serializable

@Serializable
internal data class MarketChartMarkerPayload(
    val time: Long,
    val position: String,
    val shape: String,
    val color: String,
    val id: String,
    val text: String,
    val size: Double = 1.0,
)
