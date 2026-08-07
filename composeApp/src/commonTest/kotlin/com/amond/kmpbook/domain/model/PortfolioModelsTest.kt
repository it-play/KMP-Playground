package com.amond.kmpbook.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class PortfolioModelsTest {
    @Test
    fun unrealizedProfitKeepsHistoricalAcquisitionFxRate() {
        val holding = Holding(
            stockId = "NYSE:TEST",
            quantity = 1.0,
            averagePrice = 100.0,
            currentPrice = 100.0,
            currency = Currency.USD,
        )
        val snapshot = PortfolioSnapshot(
            timestamp = Instant.parse("2026-08-07T03:00:00Z"),
            cashByCurrency = mapOf(Currency.KRW to 0.0, Currency.USD to 0.0),
            holdings = listOf(holding),
            exchangeRatesToKrw = mapOf(Currency.USD to 1_100.0),
            initialCapitalKrw = 100_000.0,
            holdingCostBasisKrw = mapOf(holding.stockId to 100_000.0),
        )

        assertEquals(110_000.0, snapshot.stockValueKrw)
        assertEquals(10_000.0, snapshot.unrealizedProfitKrw)
        assertEquals(10_000.0, snapshot.holdingUnrealizedProfitKrw(holding))
        assertEquals(0.10, snapshot.holdingReturnRateKrw(holding), 1e-12)
    }
}
