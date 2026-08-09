package com.amond.kmpbook.domain.tax.core

import com.amond.kmpbook.domain.model.market.Currency

/** Exact rational rate. 2,000 ppm is 0.20%; 220,000 ppm is 22%. */
data class TaxRate(
    val numerator: Long,
    val denominator: Long = PARTS_PER_MILLION,
) {
    init {
        require(numerator >= 0L) { "A tax rate cannot be negative." }
        require(denominator > 0L) { "A tax-rate denominator must be positive." }
    }

    val fraction: Double get() = numerator.toDouble() / denominator
    val percent: Double get() = fraction * 100.0

    fun apply(
        baseMinorUnits: Long,
        currency: Currency,
        rounding: MoneyRoundingPolicy,
    ): MoneyAmount {
        require(baseMinorUnits >= 0L) { "A tax base cannot be negative." }
        // Splitting the quotient avoids Long overflow for normal portfolio-sized bases.
        val whole = baseMinorUnits / denominator
        val remainder = baseMinorUnits % denominator
        val exact = whole.toDouble() * numerator + remainder.toDouble() * numerator / denominator
        return rounding.roundMinorUnits(exact, currency)
    }

    companion object {
        const val PARTS_PER_MILLION = 1_000_000L

        val ZERO = TaxRate(0L)
        val PERCENT_1_4 = TaxRate(14_000L)
        val PERCENT_2 = TaxRate(20_000L)
        val PERCENT_10 = TaxRate(100_000L)
        val PERCENT_14 = TaxRate(140_000L)
        val PERCENT_15 = TaxRate(150_000L)
        val PERCENT_20 = TaxRate(200_000L)
        val PERCENT_25 = TaxRate(250_000L)
        val PERCENT_30 = TaxRate(300_000L)
    }
}
