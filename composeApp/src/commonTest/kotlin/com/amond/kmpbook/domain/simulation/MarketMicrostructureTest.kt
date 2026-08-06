package com.amond.kmpbook.domain.simulation

import com.amond.kmpbook.domain.model.Market
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MarketMicrostructureTest {
    @Test
    fun krxTickBoundariesFollowUnified2026Table() {
        for (market in listOf(Market.KOSPI, Market.KOSDAQ)) {
            assertEquals(1.0, MarketMicrostructure.tickSize(market, 1_999.0))
            assertEquals(5.0, MarketMicrostructure.tickSize(market, 2_000.0))
            assertEquals(10.0, MarketMicrostructure.tickSize(market, 5_000.0))
            assertEquals(50.0, MarketMicrostructure.tickSize(market, 20_000.0))
            assertEquals(100.0, MarketMicrostructure.tickSize(market, 50_000.0))
            assertEquals(500.0, MarketMicrostructure.tickSize(market, 200_000.0))
            assertEquals(1_000.0, MarketMicrostructure.tickSize(market, 500_000.0))
        }
    }

    @Test
    fun krxDailyLimitsNeverExceedThirtyPercent() {
        val basePrice = 9_940.0
        val limits = assertNotNull(MarketMicrostructure.dailyPriceLimits(Market.KOSPI, basePrice))

        assertTrue(limits.upper <= basePrice * 1.30)
        assertTrue(limits.lower >= basePrice * 0.70)
        assertEquals(limits.upper, MarketMicrostructure.clampToDailyLimits(Market.KOSPI, 1_000_000.0, basePrice))
        assertEquals(limits.lower, MarketMicrostructure.clampToDailyLimits(Market.KOSPI, 0.01, basePrice))
    }

    @Test
    fun usQuotesUsePennyAndSubDollarUnits() {
        assertEquals(0.01, MarketMicrostructure.tickSize(Market.NASDAQ, 150.0))
        assertEquals(0.0001, MarketMicrostructure.tickSize(Market.NYSE, 0.75))
    }
}
