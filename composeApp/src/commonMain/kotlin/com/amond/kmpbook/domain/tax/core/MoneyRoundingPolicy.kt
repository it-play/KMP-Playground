package com.amond.kmpbook.domain.tax.core

import com.amond.kmpbook.domain.model.market.Currency
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

/** [minorUnitIncrement] is one won/cent by default, but can express broker-specific 10-won rules. */
data class MoneyRoundingPolicy(
    val id: String,
    val direction: RoundingDirection,
    val minorUnitIncrement: Long = 1L,
) {
    init {
        require(id.isNotBlank()) { "A rounding policy needs an id." }
        require(minorUnitIncrement > 0L) { "The rounding increment must be positive." }
    }

    fun roundMinorUnits(unroundedMinorUnits: Double, currency: Currency): MoneyAmount {
        require(unroundedMinorUnits.isFinite()) { "The amount to round must be finite." }
        val scaled = unroundedMinorUnits / minorUnitIncrement
        val rounded = when (direction) {
            RoundingDirection.DOWN -> if (scaled >= 0.0) floor(scaled) else ceil(scaled)
            RoundingDirection.HALF_UP -> if (scaled >= 0.0) floor(scaled + 0.5) else ceil(scaled - 0.5)
            RoundingDirection.UP -> if (scaled >= 0.0) ceil(scaled) else floor(scaled)
        }
        val integralUnits = CheckedMonetaryArithmetic.roundedToLong(
            rounded,
            "Rounded monetary amount",
        )
        return MoneyAmount(
            CheckedMonetaryArithmetic.multiply(
                integralUnits,
                minorUnitIncrement,
                "Rounded monetary amount",
            ),
            currency,
        )
    }

    fun fromMajorUnits(unroundedAmount: Double, currency: Currency): MoneyAmount =
        roundMinorUnits(unroundedAmount * 10.0.pow(currency.decimalPlaces), currency)

    companion object {
        val TAX_WON_DOWN = MoneyRoundingPolicy("tax-won-down", RoundingDirection.DOWN)
        val MINOR_UNIT_HALF_UP = MoneyRoundingPolicy("minor-unit-half-up", RoundingDirection.HALF_UP)
        val REGULATORY_FEE_UP = MoneyRoundingPolicy("regulatory-fee-up", RoundingDirection.UP)
    }
}
