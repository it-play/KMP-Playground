package com.amond.kmpbook.ui.charts.trading

import kotlinx.serialization.Serializable

@Serializable
internal data class MarketChartPayload(
    val instrumentId: String,
    val symbol: String,
    val resolution: String,
    val timeZone: String,
    val pricePrecision: Int,
    val priceMinMove: Double,
    val rangeKey: String,
    val visibleFrom: Long?,
    val averagePrice: Double?,
    val bars: List<MarketChartBarPayload>,
    val markers: List<MarketChartMarkerPayload>,
)
