package com.amond.kmpbook.domain.simulation.market

internal data class IndexIntervalCalculation(
    val openFactor: Double,
    val highFactor: Double,
    val lowFactor: Double,
    val closeFactor: Double,
    val constituentCount: Int,
) {
    init {
        require(openFactor > 0.0 && highFactor > 0.0 && lowFactor > 0.0 && closeFactor > 0.0)
        require(listOf(openFactor, highFactor, lowFactor, closeFactor).all(Double::isFinite))
        require(highFactor >= maxOf(openFactor, closeFactor, lowFactor))
        require(lowFactor <= minOf(openFactor, closeFactor, highFactor))
    }

    fun scaled(fraction: Double): IndexIntervalCalculation = copy(
        openFactor = scaleFactor(openFactor, fraction),
        highFactor = scaleFactor(highFactor, fraction),
        lowFactor = scaleFactor(lowFactor, fraction),
        closeFactor = scaleFactor(closeFactor, fraction),
    )

    companion object {
        fun neutral(): IndexIntervalCalculation = IndexIntervalCalculation(
            openFactor = 1.0,
            highFactor = 1.0,
            lowFactor = 1.0,
            closeFactor = 1.0,
            constituentCount = 0,
        )

        private fun scaleFactor(factor: Double, fraction: Double): Double =
            (1.0 + (factor - 1.0) * fraction).coerceIn(
                1.0 + MarketIndexEngine.MINIMUM_HOURLY_RETURN,
                1.0 + MarketIndexEngine.MAXIMUM_HOURLY_RETURN,
            )
    }
}
