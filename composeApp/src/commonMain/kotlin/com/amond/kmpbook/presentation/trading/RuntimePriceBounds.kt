package com.amond.kmpbook.presentation.trading

import kotlinx.datetime.minus
import kotlinx.datetime.plus

internal data class RuntimePriceBounds(
    val lower: Double? = null,
    val upper: Double? = null,
) {
    init {
        require(lower == null || lower >= 0.0 && lower.isFinite())
        require(upper == null || upper >= 0.0 && upper.isFinite())
        require(lower == null || upper == null || lower <= upper)
    }

    fun merge(other: RuntimePriceBounds): RuntimePriceBounds = RuntimePriceBounds(
        lower = when {
            lower == null -> other.lower
            other.lower == null -> lower
            else -> maxOf(lower, other.lower)
        },
        upper = when {
            upper == null -> other.upper
            other.upper == null -> upper
            else -> minOf(upper, other.upper)
        },
    )
}
