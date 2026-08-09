package com.amond.kmpbook.presentation.portfolio

import com.amond.kmpbook.domain.model.market.Currency
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.tax.fee.FeeBreakdown
import com.amond.kmpbook.domain.tax.liability.TaxBreakdown
import kotlin.time.Instant

data class TransactionCostRecord(
    val tradeId: String,
    val stockId: String,
    val market: Market,
    val paidAt: Instant,
    val currency: Currency,
    val commission: Double,
    val saleTax: Double,
    val exchangeRateToKrw: Double,
    val feeBreakdown: FeeBreakdown? = null,
    val taxBreakdown: TaxBreakdown? = null,
) {
    val commissionKrw: Double get() = commission * exchangeRateToKrw
    val saleTaxKrw: Double get() = saleTax * exchangeRateToKrw
}
