package com.amond.kmpbook.domain.simulation.market

data class DailyPriceLimits(
    val lower: Double,
    val upper: Double,
) {
    init {
        require(lower > 0.0) { "Lower price limit must be positive" }
        require(upper >= lower) { "Upper price limit must not be below lower limit" }
    }
}
