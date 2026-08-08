package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Currency
import kotlinx.datetime.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

/**
 * Money is stored in the currency's smallest unit: won for KRW and cents for USD.
 * This prevents UI formatting and floating-point noise from changing a tax result.
 */
data class MoneyAmount(
    val minorUnits: Long,
    val currency: Currency,
) : Comparable<MoneyAmount> {
    val amount: Double
        get() = minorUnits / 10.0.pow(currency.decimalPlaces)

    override fun compareTo(other: MoneyAmount): Int {
        require(currency == other.currency) { "Money with different currencies cannot be compared." }
        return minorUnits.compareTo(other.minorUnits)
    }

    operator fun plus(other: MoneyAmount): MoneyAmount {
        require(currency == other.currency) { "Money with different currencies cannot be added." }
        val result = minorUnits + other.minorUnits
        require(((minorUnits xor result) and (other.minorUnits xor result)) >= 0L) { "Money addition overflow." }
        return copy(minorUnits = result)
    }

    operator fun minus(other: MoneyAmount): MoneyAmount {
        require(currency == other.currency) { "Money with different currencies cannot be subtracted." }
        val result = minorUnits - other.minorUnits
        require(((minorUnits xor other.minorUnits) and (minorUnits xor result)) >= 0L) {
            "Money subtraction overflow."
        }
        return copy(minorUnits = result)
    }

    companion object {
        fun zero(currency: Currency): MoneyAmount = MoneyAmount(0L, currency)
    }
}
