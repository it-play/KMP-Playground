package com.amond.kmpbook.domain.model.protection.us

import com.amond.kmpbook.domain.model.protection.us.UsLuldBands

data class UsLuldBands(
    val referencePrice: Double,
    val lower: Double,
    val upper: Double,
    val bandAmount: Double,
    val doubledForClosingWindow: Boolean,
) {
    init {
        require(referencePrice > 0.0 && referencePrice.isFinite())
        require(lower > 0.0 && lower.isFinite() && lower < referencePrice)
        require(upper.isFinite() && upper > referencePrice)
        require(bandAmount > 0.0 && bandAmount.isFinite())
    }

    operator fun contains(price: Double): Boolean = price in lower..upper
}
