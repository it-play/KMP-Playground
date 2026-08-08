package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Currency
import kotlinx.datetime.LocalDate
import kotlin.math.floor

data class DividendTaxRequest(
    val taxClass: DividendTaxClass,
    val grossAmount: MoneyAmount,
    val paidOn: LocalDate,
    /** Won per one unit of the dividend currency. KRW must use 1.0. */
    val taxExchangeRateToKrw: Double = 1.0,
    val w8BenValid: Boolean = true,
    val otherFinancialIncomeGrossKrw: Long = 0L,
    val roundingPolicy: MoneyRoundingPolicy = MoneyRoundingPolicy.TAX_WON_DOWN,
) {
    init {
        require(grossAmount.minorUnits >= 0L) { "A gross dividend cannot be negative." }
        require(taxExchangeRateToKrw > 0.0 && taxExchangeRateToKrw.isFinite()) {
            "A positive tax exchange rate is required."
        }
        require(otherFinancialIncomeGrossKrw >= 0L) { "Other financial income cannot be negative." }
        require(
            (taxClass in KOREAN_DIVIDEND_CLASSES && grossAmount.currency == Currency.KRW) ||
                (taxClass in US_DIVIDEND_CLASSES && grossAmount.currency == Currency.USD),
        ) { "The dividend currency does not match its tax class." }
        require(grossAmount.currency != Currency.KRW || taxExchangeRateToKrw == 1.0) {
            "KRW dividend income must use a 1.0 tax exchange rate."
        }
    }

    private companion object {
        val KOREAN_DIVIDEND_CLASSES = setOf(
            DividendTaxClass.KOREAN_ORDINARY_CASH,
            DividendTaxClass.KOREAN_ETF_DISTRIBUTION,
        )
        val US_DIVIDEND_CLASSES = setOf(
            DividendTaxClass.US_ORDINARY_CORPORATION,
            DividendTaxClass.US_RIC_ETF_DISTRIBUTION,
            DividendTaxClass.US_RIC_CLOSED_END_DISTRIBUTION,
            DividendTaxClass.US_REIT_DISTRIBUTION,
            DividendTaxClass.US_ETN_CONTINGENT_COUPON,
            DividendTaxClass.FOREIGN_ADR_DISTRIBUTION,
        )
    }
}
