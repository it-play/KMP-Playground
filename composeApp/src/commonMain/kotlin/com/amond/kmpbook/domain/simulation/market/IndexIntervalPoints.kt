package com.amond.kmpbook.domain.simulation.market

import kotlin.math.max
import kotlin.math.min

internal data class IndexIntervalPoints(
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
) {
    init {
        require(listOf(open, high, low, close).all { it > 0.0 && it.isFinite() })
        require(high >= maxOf(open, close, low))
        require(low <= minOf(open, close, high))
    }

    companion object {
        fun fromEndpoints(open: Double, close: Double): IndexIntervalPoints = IndexIntervalPoints(
            open = open,
            high = max(open, close),
            low = min(open, close),
            close = close,
        )
    }
}
