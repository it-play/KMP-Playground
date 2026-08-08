package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

data class UsLuldBands(
    val referencePrice: Double,
    val lower: Double,
    val upper: Double,
    val bandAmount: Double,
    val doubledForClosingWindow: Boolean,
) {
    init {
        require(referencePrice > 0.0 && lower >= 0.0 && upper > referencePrice)
        require(lower < referencePrice && bandAmount > 0.0)
    }

    operator fun contains(price: Double): Boolean = price in lower..upper
}
