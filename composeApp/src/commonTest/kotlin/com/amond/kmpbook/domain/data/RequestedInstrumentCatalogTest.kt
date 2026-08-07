package com.amond.kmpbook.domain.data

import com.amond.kmpbook.domain.model.InstrumentStrategy
import com.amond.kmpbook.domain.model.InstrumentType
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.ReferenceCurrency
import com.amond.kmpbook.domain.model.Sector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RequestedInstrumentCatalogTest {
    @Test
    fun everyRequestedInstrumentIsPresentOnceWithVerifiedIdentity() {
        assertEquals(88, US_REQUESTED_SYMBOLS.size)
        assertEquals(43, KOREAN_REQUESTED_SYMBOLS.size)
        assertEquals(131, ALL_REQUESTED_SYMBOLS.size)
        assertEquals(78, RequestedUsInstrumentCatalog.definitions.size)
        assertEquals(39, RequestedKoreanInstrumentCatalog.definitions.size)
        assertEquals(117, StockCatalog.requestedDefinitions.size)

        val instruments = ALL_REQUESTED_SYMBOLS.map(::requiredInstrument)
        assertEquals(131, instruments.distinctBy { it.id }.size)
        assertEquals(114, instruments.count { it.instrumentType == InstrumentType.ETF })
        assertEquals(5, instruments.count { it.instrumentType == InstrumentType.CLOSED_END_FUND })
        assertEquals(3, instruments.count { it.instrumentType == InstrumentType.ETN })
        assertEquals(2, instruments.count { it.instrumentType == InstrumentType.REIT })
        assertEquals(2, instruments.count { it.instrumentType == InstrumentType.ADR })
        assertEquals(5, instruments.count { it.instrumentType == InstrumentType.STOCK })
        val identities = instruments.map { assertNotNull(it.identityProfile) }
        assertTrue(instruments.all { instrument ->
            instrument.identityProfile?.let { identity ->
                identity.verifiedOn == "2026-08-07" && identity.officialSourceUrl.startsWith("https://")
            } == true
        })
        assertEquals(131, identities.map { it.officialSourceUrl }.distinct().size)
        assertTrue(identities.all { it.eventRiskTags.isNotEmpty() && it.distributionNotes.isNotBlank() })
        assertTrue(
            requiredInstrument("EGGQ").identityProfile?.supportingSourceUrls.orEmpty().any {
                it.startsWith("https://www.sec.gov/")
            },
        )
    }

    @Test
    fun legalStructuresMarketsAndMaturitiesStayExplicit() {
        assertEquals(
            setOf("BLW", "HIO", "PHK", "GOF", "TYG"),
            ALL_REQUESTED_SYMBOLS.filterTo(mutableSetOf()) {
                requiredInstrument(it).instrumentType == InstrumentType.CLOSED_END_FUND
            },
        )
        assertEquals(
            setOf("SLVO", "USOI", "GLDI"),
            ALL_REQUESTED_SYMBOLS.filterTo(mutableSetOf()) {
                requiredInstrument(it).instrumentType == InstrumentType.ETN
            },
        )
        assertEquals(InstrumentType.REIT, requiredInstrument("O").instrumentType)
        assertEquals(InstrumentType.REIT, requiredInstrument("ORC").instrumentType)
        assertEquals(InstrumentType.ADR, requiredInstrument("ITUB").instrumentType)
        assertEquals(InstrumentType.ADR, requiredInstrument("TSM").instrumentType)

        val correctedVenues = mapOf(
            "SGOV" to Market.NYSE,
            "SHV" to Market.NYSE,
            "FIXT" to Market.NYSE,
            "DIVY" to Market.NYSE,
            "TPHD" to Market.NYSE,
            "ACKY" to Market.NYSE,
            "ITA" to Market.CBOE_BZX,
            "PAWZ" to Market.CBOE_BZX,
            "FLOT" to Market.CBOE_BZX,
            "ICSH" to Market.CBOE_BZX,
            "JUDO" to Market.CBOE_BZX,
            "NOBL" to Market.CBOE_BZX,
            "WEEK" to Market.CBOE_BZX,
            "EGGQ" to Market.NASDAQ,
        )
        correctedVenues.forEach { (symbol, venue) ->
            assertEquals(venue, requiredInstrument(symbol).market, symbol)
        }

        assertEquals("2033-04-21", requiredInstrument("SLVO").identityProfile?.maturityDate)
        assertEquals("2037-04-24", requiredInstrument("USOI").identityProfile?.maturityDate)
        assertEquals("2033-02-02", requiredInstrument("GLDI").identityProfile?.maturityDate)
        listOf("SLVO", "USOI", "GLDI").forEach { symbol ->
            val instrument = requiredInstrument(symbol)
            assertEquals(1.0, instrument.behavior.commodityFactorSensitivity, symbol)
            val identity = assertNotNull(instrument.identityProfile)
            assertEquals("UBS AG, London Branch", identity.issuerOrManager, symbol)
            assertTrue(identity.callable, symbol)
        }

        assertEquals(1.0, requiredInstrument("ITUB").identityProfile?.adrUnderlyingShareRatio)
        assertEquals(5.0, requiredInstrument("TSM").identityProfile?.adrUnderlyingShareRatio)
    }

    @Test
    fun leverageAndCurrencyExposureMatchProductMechanics() {
        assertLeverage("TQQQ", 3.0, InstrumentStrategy.DAILY_LEVERAGED)
        assertLeverage("SOXS", -3.0, InstrumentStrategy.DAILY_INVERSE)
        assertLeverage("TYO", -3.0, InstrumentStrategy.DAILY_INVERSE)
        assertLeverage("114800", -1.0, InstrumentStrategy.DAILY_INVERSE)
        assertLeverage("0193L0", -2.0, InstrumentStrategy.DAILY_INVERSE)
        assertEquals(7.5, requiredInstrument("TYO").behavior.durationYears)
        assertEquals(-5.0, requiredInstrument("RISR").behavior.durationYears)
        assertEquals(Sector.OTHER, requiredInstrument("114800").sector)

        assertEquals(ReferenceCurrency.SGD, requiredInstrument("EWS").singleFxCurrency())
        assertEquals(ReferenceCurrency.AUD, requiredInstrument("FLAU").singleFxCurrency())
        val hedgedDividend = requiredInstrument("452360").etfProfile?.fxProfile
        assertTrue(hedgedDividend?.isFullyHedged == true)
        assertTrue(hedgedDividend.annualHedgeCostRate > 0.0)
        assertEquals(ReferenceCurrency.BRL, requiredInstrument("ITUB").behavior.referenceCurrency)
        assertEquals(0.8, requiredInstrument("ITUB").behavior.referenceCurrencySensitivity)
        assertEquals(ReferenceCurrency.TWD, requiredInstrument("TSM").behavior.referenceCurrency)
        assertEquals(0.8, requiredInstrument("TSM").behavior.referenceCurrencySensitivity)

        val bndwFx = assertNotNull(requiredInstrument("BNDW").etfProfile?.fxProfile)
        val usdLeg = bndwFx.legs.single { it.currency == ReferenceCurrency.USD }
        val nonUsdLegs = bndwFx.legs.filter { it.currency != ReferenceCurrency.USD }
        assertEquals(0.0, usdLeg.hedgeRatioToListingCurrency)
        assertTrue(nonUsdLegs.isNotEmpty())
        assertTrue(nonUsdLegs.all { it.hedgeRatioToListingCurrency == 1.0 })
        assertTrue(bndwFx.annualHedgeCostRate > 0.0)
        assertEquals(1.0, bndwFx.legs.sumOf { it.grossNotional }, 1e-9)

        val requestedFunds = ALL_REQUESTED_SYMBOLS.map(::requiredInstrument).filter { it.isFundLike }
        assertTrue(requestedFunds.all { it.etfProfile?.fxProfile != null })
    }

    @Test
    fun correctedNamesAndSearchAliasesAreAvailableInTheMergedCatalog() {
        assertEquals("iShares 7-10 Year Treasury Bond ETF", requiredInstrument("IEF").identityProfile?.legalName)
        assertEquals("Vanguard Total World Stock ETF", requiredInstrument("VT").name)
        assertEquals("Vanguard Value ETF", requiredInstrument("VTV").name)
        assertEquals("KODEX 미국배당다우존스타겟커버드콜", requiredInstrument("483290").name)
        assertTrue(StockCatalog.search("타켓커버드콜").any { it.symbol == "483290" })
        assertTrue(StockCatalog.search("싱가포르 ETF").any { it.symbol == "EWS" })
    }

    @Test
    fun symbolCanonicalizationDuplicateSafetyAndUnderlyingLinksStayExplicit() {
        val tqqq = requiredInstrument("TQQQ")
        assertEquals(tqqq.id, StockCatalog.findBySymbol("  tQqQ  ", Market.NASDAQ)?.id)
        assertFailsWith<IllegalArgumentException> {
            tqqq.copy(symbol = " TQQQ ")
        }
        assertFailsWith<IllegalArgumentException> {
            StockCatalog.withAdditional(listOf(tqqq.copy(symbol = "tqqq")))
        }

        val samsung = assertNotNull(StockCatalog.findBySymbol("005930", Market.KOSPI))
        assertEquals(
            setOf(samsung.id),
            requiredInstrument("0193L0").identityProfile?.underlyingInstrumentIds,
        )
    }

    private fun assertLeverage(symbol: String, leverage: Double, strategy: InstrumentStrategy) {
        val instrument = requiredInstrument(symbol)
        assertEquals(leverage, instrument.etfProfile?.leverage)
        assertEquals(strategy, instrument.behavior.strategy)
    }

    private fun com.amond.kmpbook.domain.model.StockDefinition.singleFxCurrency(): ReferenceCurrency =
        assertNotNull(etfProfile?.fxProfile).legs.single().currency

    private fun requiredInstrument(symbol: String) = assertNotNull(
        StockCatalog.findBySymbol(symbol),
        "요청 종목을 찾을 수 없습니다: $symbol",
    )

    private companion object {
        val US_REQUESTED_SYMBOLS = setOf(
            "EWS", "FLAU", "IALT", "IPO", "ITA", "PAWZ", "VT", "CLOI", "DGRO", "DVY",
            "FLOT", "ICSH", "IEF", "JEPI", "JEPQ", "JUDO", "PEY", "SCHD", "SGOV", "SPYD",
            "BLW", "HIO", "HIYY", "PCEF", "PHK", "QQQY", "SLVO", "USOI", "YMAX", "AGZD",
            "AOM", "BDVG", "BNDW", "CBON", "CGOV", "CSHI", "DIVY", "EMHC", "FIXT", "FNDX",
            "FXG", "GPIQ", "GUNR", "IQMM", "JAAA", "JPST", "NOBL", "ONEY", "PFF", "PFXF",
            "RYLG", "SBAR", "SHV", "SOFR", "TDIV", "TIP", "TPHD", "USDX", "VIGI", "WEEK",
            "ACKY", "BIZD", "GLDI", "GOF", "RISR", "WEPN", "GILD", "O", "ORC", "ITUB",
            "T", "OILK", "PFE", "VTV", "VOO", "EGGQ", "TQQQ", "SOXS", "VYMI", "TSNF",
            "SOLZ", "QQQI", "BITO", "TSM", "RPAR", "TYLD", "TYG", "TYO",
        )

        val KOREAN_REQUESTED_SYMBOLS = setOf(
            "423160", "0089D0", "0052D0", "0004G0", "487340", "456880", "402970", "0046Y0",
            "316300", "0189Z0", "0111J0", "0127T0", "0097L0", "468370", "153130", "483290",
            "437070", "237370", "476800", "0098N0", "461490", "459750", "497880", "452360",
            "0152E0", "357870", "488500", "429000", "0046A0", "0176P0", "0208N0", "010120",
            "114800", "0183J0", "446690", "423170", "0220B0", "491700", "472920", "391670",
            "0193L0", "494420", "138930",
        )

        val ALL_REQUESTED_SYMBOLS = US_REQUESTED_SYMBOLS + KOREAN_REQUESTED_SYMBOLS
    }
}
