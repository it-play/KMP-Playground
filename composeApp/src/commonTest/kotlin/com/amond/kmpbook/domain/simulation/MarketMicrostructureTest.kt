package com.amond.kmpbook.domain.simulation

import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.EtfAssetClass
import com.amond.kmpbook.domain.model.EtfExposureRegion
import com.amond.kmpbook.domain.model.EtfProfile
import com.amond.kmpbook.domain.model.EtfTaxCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MarketMicrostructureTest {
    @Test
    fun krxEquityTickBoundariesFollowMarketSpecific2026Tables() {
        for (market in listOf(Market.KOSPI, Market.KOSDAQ)) {
            assertEquals(1.0, MarketMicrostructure.tickSize(market, 999.0))
            assertEquals(5.0, MarketMicrostructure.tickSize(market, 1_000.0))
            assertEquals(10.0, MarketMicrostructure.tickSize(market, 5_000.0))
            assertEquals(50.0, MarketMicrostructure.tickSize(market, 10_000.0))
        }
        assertEquals(100.0, MarketMicrostructure.tickSize(Market.KOSPI, 50_000.0))
        assertEquals(500.0, MarketMicrostructure.tickSize(Market.KOSPI, 100_000.0))
        assertEquals(1_000.0, MarketMicrostructure.tickSize(Market.KOSPI, 500_000.0))
        assertEquals(100.0, MarketMicrostructure.tickSize(Market.KOSDAQ, 50_000.0))
        assertEquals(100.0, MarketMicrostructure.tickSize(Market.KOSDAQ, 500_000.0))
    }

    @Test
    fun krxListedEtfUsesFiveWonQuotationUnitAtEveryPrice() {
        val etf = testStock(symbol = "ETF", market = Market.KOSPI).copy(
            etfProfile = EtfProfile(
                benchmark = "테스트 지수",
                assetClass = EtfAssetClass.BROAD_EQUITY,
                taxCategory = EtfTaxCategory.KOREAN_DOMESTIC_EQUITY,
                annualExpenseRatio = 0.001,
                exposureRegion = EtfExposureRegion.KOREA,
            ),
        )

        assertEquals(5.0, MarketMicrostructure.tickSize(etf, 500.0))
        assertEquals(5.0, MarketMicrostructure.tickSize(etf, 750_000.0))
        assertEquals(10_005.0, MarketMicrostructure.roundNearest(etf, 10_003.0))
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
        for (market in listOf(Market.NASDAQ, Market.NYSE, Market.NYSE_ARCA, Market.NYSE_AMERICAN)) {
            assertEquals(0.01, MarketMicrostructure.tickSize(market, 150.0))
            assertEquals(0.0001, MarketMicrostructure.tickSize(market, 0.75))
        }
    }
}
