package com.amond.kmpbook.domain.model.corporateaction

import kotlin.math.abs

object CorporateActionMath {
    private val allowedRatios = listOf(2.0, 3.0, 4.0, 5.0, 10.0, 20.0)

    fun forwardMultiplier(price: Double, targetPrice: Double): Double {
        require(price > targetPrice && targetPrice > 0.0)
        val desired = price / targetPrice
        return allowedRatios.minBy { kotlin.math.abs(it - desired) }
    }

    fun reverseMultiplier(price: Double, targetPrice: Double): Double {
        require(price < targetPrice && price > 0.0)
        val desired = targetPrice / price
        return 1.0 / allowedRatios.minBy { kotlin.math.abs(it - desired) }
    }
}
