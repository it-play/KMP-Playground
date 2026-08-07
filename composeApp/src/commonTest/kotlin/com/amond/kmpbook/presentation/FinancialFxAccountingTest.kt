package com.amond.kmpbook.presentation

import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.Market
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class FinancialFxAccountingTest {
    @Test
    fun realizedKrwGainUsesAcquisitionAndSaleDateFxAmounts() {
        val record = RealizedGainRecord(
            tradeId = "trade-1",
            stockId = "NYSE:TEST",
            market = Market.NYSE,
            soldAt = Instant.parse("2026-08-07T03:00:00Z"),
            settlementDate = LocalDate(2026, 8, 11),
            quantity = 1.0,
            proceeds = 100.0,
            costBasis = 100.0,
            commission = 0.0,
            saleTax = 0.0,
            currency = Currency.USD,
            exchangeRateToKrw = 1_100.0,
            taxGrossProceedsKrw = 110_000L,
            taxCostBasisKrw = 100_000L,
            taxDirectSellingCostsKrw = 0L,
            taxGainKrw = 10_000L,
        )

        assertEquals(0.0, record.gain)
        assertEquals(10_000.0, record.gainKrw)
    }
}
