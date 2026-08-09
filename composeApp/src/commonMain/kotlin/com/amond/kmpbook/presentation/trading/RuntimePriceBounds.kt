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
        lower = listOfNotNull(lower, other.lower).maxOrNull(),
        upper = listOfNotNull(upper, other.upper).minOrNull(),
    )
}
