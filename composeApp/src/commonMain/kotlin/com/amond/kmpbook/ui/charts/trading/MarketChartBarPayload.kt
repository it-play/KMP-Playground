package com.amond.kmpbook.ui.charts.trading

import kotlinx.serialization.Serializable

@Serializable
internal data class MarketChartBarPayload(
    val time: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
    val volumeText: String,
)
