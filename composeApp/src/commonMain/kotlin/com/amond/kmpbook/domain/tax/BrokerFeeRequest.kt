package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.OrderSide
import kotlinx.datetime.LocalDate
import kotlin.math.min

data class BrokerFeeRequest(
    val market: Market,
    val side: OrderSide,
    val grossAmount: MoneyAmount,
    val quantity: Double,
    val tradedOn: LocalDate,
    /** Principal actually exchanged in the same currency; null means no FX conversion in this event. */
    val fxConversionAmount: MoneyAmount? = null,
) {
    init {
        require(quantity > 0.0) { "Trade quantity must be positive." }
        require(grossAmount.minorUnits >= 0L) { "Gross trade amount cannot be negative." }
        require(grossAmount.currency == market.currency) { "Gross currency must match the market." }
        require(fxConversionAmount == null || fxConversionAmount.currency == grossAmount.currency) {
            "FX principal must use the trade currency."
        }
        require(fxConversionAmount == null || fxConversionAmount.minorUnits >= 0L) {
            "FX principal cannot be negative."
        }
    }
}
