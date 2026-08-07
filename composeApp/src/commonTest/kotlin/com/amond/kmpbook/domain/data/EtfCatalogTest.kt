package com.amond.kmpbook.domain.data

import com.amond.kmpbook.domain.model.EtfTaxCategory
import com.amond.kmpbook.domain.model.EtfExposureRegion
import com.amond.kmpbook.domain.model.InstrumentType
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.ReferenceCurrency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EtfCatalogTest {
    @Test
    fun catalogContainsExactlyOneHundredKoreanAndThreeHundredUsEtfs() {
        assertEquals(100, EtfCatalog.korean.size)
        assertEquals(300, EtfCatalog.unitedStates.size)
        assertEquals(400, EtfCatalog.definitions.size)

        assertTrue(EtfCatalog.korean.all { it.market == Market.KOSPI })
        assertTrue(
            EtfCatalog.unitedStates.all {
                it.market == Market.NASDAQ || it.market == Market.NYSE_ARCA
            },
        )
        assertEquals(
            EtfCatalog.definitions.size,
            EtfCatalog.definitions.distinctBy { it.market to it.symbol }.size,
        )
        assertTrue(EtfCatalog.IDENTITY_SOURCE_URLS.isNotEmpty())
        assertTrue(EtfCatalog.IDENTITY_SOURCE_URLS.all { it.startsWith("https://") })
    }

    @Test
    fun everyCatalogEntryIsExplicitlyAnEtfAndUsesCompatibleTaxCategory() {
        assertTrue(
            EtfCatalog.definitions.all {
                it.instrumentType == InstrumentType.ETF && it.isEtf && !it.hasCorporateEarnings
            },
        )
        assertTrue(
            EtfCatalog.korean.all {
                requireNotNull(it.etfProfile).taxCategory != EtfTaxCategory.FOREIGN_LISTED
            },
        )
        assertTrue(
            EtfCatalog.unitedStates.all {
                requireNotNull(it.etfProfile).taxCategory == EtfTaxCategory.FOREIGN_LISTED
            },
        )
    }

    @Test
    fun representativeRealIdentitiesRetainTheirListingMarketsAndEtfRules() {
        val kodex200 = assertNotNull(EtfCatalog.findBySymbol("069500", Market.KOSPI))
        assertEquals("KODEX 200", kodex200.name)
        assertEquals(EtfTaxCategory.KOREAN_DOMESTIC_EQUITY, kodex200.etfProfile?.taxCategory)
        assertEquals(EtfExposureRegion.KOREA, kodex200.etfProfile?.exposureRegion)
        assertEquals(ReferenceCurrency.KRW, kodex200.etfProfile?.fxProfile?.legs?.single()?.currency)

        val tigerUs = assertNotNull(EtfCatalog.findBySymbol("360750", Market.KOSPI))
        assertEquals("TIGER 미국S&P500", tigerUs.name)
        assertEquals(EtfTaxCategory.KOREAN_OTHER, tigerUs.etfProfile?.taxCategory)
        assertEquals(EtfExposureRegion.UNITED_STATES, tigerUs.etfProfile?.exposureRegion)
        assertEquals(ReferenceCurrency.USD, tigerUs.etfProfile?.fxProfile?.legs?.single()?.currency)
        assertEquals(0.0, tigerUs.etfProfile?.fxProfile?.legs?.single()?.hedgeRatioToListingCurrency)

        val hedgedUs = assertNotNull(EtfCatalog.findBySymbol("449180", Market.KOSPI))
        assertEquals(EtfExposureRegion.UNITED_STATES, hedgedUs.etfProfile?.exposureRegion)
        assertEquals(1.0, hedgedUs.etfProfile?.fxProfile?.legs?.single()?.hedgeRatioToListingCurrency)

        val voo = assertNotNull(EtfCatalog.findBySymbol("VOO", Market.NYSE_ARCA))
        assertEquals("Vanguard S&P 500 ETF", voo.name)
        val qqq = assertNotNull(EtfCatalog.findBySymbol("QQQ", Market.NASDAQ))
        assertEquals("Invesco QQQ Trust, Series 1", qqq.name)

        val vea = assertNotNull(EtfCatalog.findBySymbol("VEA", Market.NYSE_ARCA))
        assertEquals(EtfExposureRegion.DEVELOPED_EX_US, vea.etfProfile?.exposureRegion)
        assertEquals(0.0, vea.etfProfile?.usdKrwSensitivity)

        val emerging = assertNotNull(EtfCatalog.findBySymbol("EMXC", Market.NASDAQ))
        assertEquals(EtfExposureRegion.EMERGING_MARKETS, emerging.etfProfile?.exposureRegion)
        val world = assertNotNull(EtfCatalog.findBySymbol("VT", Market.NYSE_ARCA))
        assertEquals(EtfExposureRegion.GLOBAL, world.etfProfile?.exposureRegion)

        val hewj = assertNotNull(EtfCatalog.findBySymbol("HEWJ", Market.NYSE_ARCA))
        assertTrue(vea.etfProfile?.fxProfile?.isFullyHedged == false)
        assertTrue(hewj.etfProfile?.fxProfile?.isFullyHedged == true)

        val gold = assertNotNull(EtfCatalog.findBySymbol("411060", Market.KOSPI))
        assertEquals(ReferenceCurrency.USD, gold.etfProfile?.fxProfile?.legs?.single()?.currency)
        val mixed = assertNotNull(EtfCatalog.findBySymbol("284430", Market.KOSPI))
        assertEquals(
            mapOf(ReferenceCurrency.KRW to 0.5, ReferenceCurrency.USD to 0.5),
            mixed.etfProfile?.fxProfile?.legs?.associate { it.currency to it.grossNotional },
        )

        val qld = assertNotNull(EtfCatalog.findBySymbol("QLD", Market.NYSE_ARCA))
        assertEquals(2.0, qld.etfProfile?.leverage)
    }

    @Test
    fun usUniverseExcludesUnsupportedCommodityTrustsPoolsAndEtns() {
        val blockedSymbols = setOf(
            "GLD", "IAU", "SLV", "USO", "UNG", "DBC", "GSG", "IBIT", "GBTC", "VXX",
        )
        assertTrue(EtfCatalog.unitedStates.none { it.symbol in blockedSymbols })
        assertTrue(
            EtfCatalog.unitedStates.none {
                val name = it.name.lowercase()
                name.contains(" etn") ||
                    name.contains("exchange traded note") ||
                    name.contains("exchange-traded note") ||
                    name.contains("notes due")
            },
        )
    }

    @Test
    fun simulationMetricsArePositiveDeterministicGameValues() {
        assertTrue(EtfCatalog.DISCLAIMER.contains("게임 데이터"))
        assertTrue(
            EtfCatalog.definitions.all {
                it.initialPrice > 0.0 &&
                    it.volatility >= 0.0 &&
                    it.dividendYield >= 0.0 &&
                    it.marketCap > 0.0 &&
                    it.sharesOutstanding > 0L &&
                    (
                        it.description.contains("게임 데이터") ||
                            it.description.lowercase().contains("game data")
                    )
            },
        )
        assertTrue(EtfCatalog.definitions.all { it.etfProfile?.fxProfile != null })
        assertEquals(25_050_000_000_000.0, EtfCatalog.korean.first().marketCap)
        assertEquals(240_250_000_000.0, EtfCatalog.unitedStates.first().marketCap)
        assertTrue(EtfCatalog.korean.zipWithNext().all { (left, right) -> left.marketCap > right.marketCap })
        assertTrue(
            EtfCatalog.unitedStates.zipWithNext().all { (left, right) -> left.marketCap > right.marketCap },
        )
        assertFalse(EtfCatalog.korean.any { !it.symbol.matches(Regex("[0-9A-Z]{6}")) })
    }
}
