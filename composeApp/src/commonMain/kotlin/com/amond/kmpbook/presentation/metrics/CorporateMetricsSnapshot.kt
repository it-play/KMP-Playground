package com.amond.kmpbook.presentation.metrics

import com.amond.kmpbook.domain.model.market.Currency
import kotlin.time.Instant

data class CorporateMetricsSnapshot(
    override val stockId: String,
    override val asOf: Instant,
    override val currency: Currency,
    override val marketCapitalization: Double,
    val sharesOutstanding: Double,
    val ttmRevenue: Double,
    val ttmNetIncome: Double,
    val bookEquity: Double,
    val earningsPerShare: Double?,
    val priceEarningsRatio: Double?,
    val priceSalesRatio: Double?,
    val returnOnEquity: Double?,
    val priceToBookRatio: Double?,
) : InstrumentMetricsSnapshot {
    init {
        require(stockId.isNotBlank())
        require(marketCapitalization.isFinite() && marketCapitalization >= 0.0)
        require(sharesOutstanding.isFinite() && sharesOutstanding > 0.0)
        require(ttmRevenue.isFinite() && ttmRevenue >= 0.0)
        require(ttmNetIncome.isFinite())
        require(bookEquity.isFinite() && bookEquity > 0.0)
        require(
            listOf(
                earningsPerShare,
                priceEarningsRatio,
                priceSalesRatio,
                returnOnEquity,
                priceToBookRatio,
            ).all { it == null || it.isFinite() },
        )
    }
}
