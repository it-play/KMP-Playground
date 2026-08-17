package com.amond.kmpbook.presentation.market

data class MarketIndexSeries(
    val symbol: String,
    val name: String,
    val points: List<MarketIndexPoint>,
) {
    val isAvailable: Boolean get() = points.size >= 2
    val currentValue: Double? get() = points.lastOrNull()?.close
    val changeRate: Double?
        get() {
            val first = points.firstOrNull()?.close ?: return null
            val last = points.lastOrNull()?.close ?: return null
            return if (first == 0.0) null else last / first - 1.0
        }
}
