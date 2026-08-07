package com.amond.kmpbook.domain.simulation

import com.amond.kmpbook.domain.model.EtfAssetClass
import com.amond.kmpbook.domain.model.EtfExposureRegion
import com.amond.kmpbook.domain.model.EtfProfile
import com.amond.kmpbook.domain.model.EtfTaxCategory
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.MarketIndexCatalog
import com.amond.kmpbook.domain.model.MarketIndexId
import com.amond.kmpbook.domain.model.MarketIndexSnapshot
import com.amond.kmpbook.domain.model.PriceBar
import com.amond.kmpbook.domain.model.Sector
import com.amond.kmpbook.domain.model.StockDefinition
import com.amond.kmpbook.domain.model.TurnStep
import kotlinx.datetime.LocalDate
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarketIndexEngineTest {
    private val time = Instant.parse("2026-08-07T15:00:00Z")

    @Test
    fun sp500AndNasdaqUseExactMarketCapWeightedReturnsAndExcludeEtfs() {
        val nyse = stock("NYSE_BIG", Market.NYSE, marketCap = 300.0)
        val nasdaq = stock("NASDAQ_SMALL", Market.NASDAQ, marketCap = 100.0)
        val etf = stock("HUGE_ETF", Market.NASDAQ, marketCap = 1_000_000.0).asEtf()
        val previous = previousMap(
            MarketIndexId.SP_500 to 1_000.0,
            MarketIndexId.NASDAQ_COMPOSITE to 1_000.0,
        )

        val result = MarketIndexEngine().calculate(
            input(
                stocks = listOf(nyse, nasdaq, etf),
                bars = listOf(
                    bar(nyse, open = 100.0, close = 110.0),
                    bar(nasdaq, open = 100.0, close = 80.0),
                    bar(etf, open = 100.0, close = 200.0),
                ),
                previous = previous,
            ),
        )

        // (300×+10% + 100×-20%) / 400 = +2.5%
        assertEquals(1_025.0, result.getValue(MarketIndexId.SP_500).value, 1e-10)
        // Nasdaq 개별주는 -20%; 시가총액이 큰 ETF는 구성종목이 아니다.
        assertEquals(800.0, result.getValue(MarketIndexId.NASDAQ_COMPOSITE).value, 1e-10)
        assertEquals(2, result.getValue(MarketIndexId.SP_500).constituentCount)
        assertEquals(1, result.getValue(MarketIndexId.NASDAQ_COMPOSITE).constituentCount)
    }

    @Test
    fun capWeightedIndicesCarryConstituentGapAndIntrahourRangeIntoNewDailyOhlc() {
        val nyse = stock("NYSE_BIG", Market.NYSE, marketCap = 300.0)
        val nasdaq = stock("NASDAQ_SMALL", Market.NASDAQ, marketCap = 100.0)
        val previousSession = LocalDate(2026, 8, 6)
        val previous = mapOf(
            MarketIndexId.SP_500 to snapshot(
                id = MarketIndexId.SP_500,
                value = 1_000.0,
                previousClose = 990.0,
                open = 995.0,
                high = 1_010.0,
                low = 980.0,
                sessionDate = previousSession,
            ),
            MarketIndexId.NASDAQ_COMPOSITE to snapshot(
                id = MarketIndexId.NASDAQ_COMPOSITE,
                value = 1_000.0,
                sessionDate = previousSession,
            ),
        )

        val result = MarketIndexEngine().calculate(
            input(
                stocks = listOf(nyse, nasdaq),
                bars = listOf(
                    bar(nyse, open = 110.0, high = 120.0, low = 90.0, close = 115.0),
                    bar(nasdaq, open = 80.0, high = 100.0, low = 70.0, close = 75.0),
                ),
                previous = previous,
                previousCloses = mapOf(nyse.id to 100.0, nasdaq.id to 100.0),
            ),
        )

        val sp500 = result.getValue(MarketIndexId.SP_500)
        // 3:1 시가총액 가중: O=102.5%, H=115%, L=85%, C=105%.
        assertEquals(1_000.0, sp500.previousClose, 1e-10)
        assertEquals(1_025.0, sp500.open, 1e-10)
        assertEquals(1_150.0, sp500.high, 1e-10)
        assertEquals(850.0, sp500.low, 1e-10)
        assertEquals(1_050.0, sp500.value, 1e-10)

        val composite = result.getValue(MarketIndexId.NASDAQ_COMPOSITE)
        // Nasdaq 구성종목은 두 번째 종목 하나이므로 각 endpoint 비율과 일치한다.
        assertEquals(1_000.0, composite.previousClose, 1e-10)
        assertEquals(800.0, composite.open, 1e-10)
        assertEquals(1_000.0, composite.high, 1e-10)
        assertEquals(700.0, composite.low, 1e-10)
        assertEquals(750.0, composite.value, 1e-10)
    }

    @Test
    fun sameSessionKeepsPreviousCloseAndOpenWhileExtendingDailyHighAndLow() {
        val stock = stock("RANGE", Market.NYSE, marketCap = 100.0)
        val previous = snapshot(
            id = MarketIndexId.SP_500,
            value = 1_000.0,
            previousClose = 900.0,
            open = 950.0,
            high = 1_100.0,
            low = 900.0,
            sessionDate = LocalDate(2026, 8, 7),
        )

        val result = MarketIndexEngine().calculate(
            input(
                stocks = listOf(stock),
                bars = listOf(bar(stock, open = 105.0, high = 120.0, low = 85.0, close = 102.0)),
                previous = mapOf(MarketIndexId.SP_500 to previous),
                previousCloses = mapOf(stock.id to 100.0),
            ),
        ).getValue(MarketIndexId.SP_500)

        assertEquals(900.0, result.previousClose, 1e-10)
        assertEquals(950.0, result.open, 1e-10)
        assertEquals(1_200.0, result.high, 1e-10)
        assertEquals(850.0, result.low, 1e-10)
        assertEquals(1_020.0, result.value, 1e-10)
    }

    @Test
    fun dowUsesExactPriceWeightedReturnOverOfficialUniverseIntersection() {
        val apple = stock("AAPL", Market.NASDAQ, marketCap = 3_000.0)
        val microsoft = stock("MSFT", Market.NASDAQ, marketCap = 9_000.0)
        val nonMember = stock("META", Market.NASDAQ, marketCap = 99_000.0)
        // 공식 편입 티커라도 ETF라면 지수 구성종목에 들어가지 않는다.
        val etfWithDowTicker = stock("NVDA", Market.NASDAQ, marketCap = 50_000.0).asEtf()
        val result = MarketIndexEngine().calculate(
            input(
                stocks = listOf(apple, microsoft, nonMember, etfWithDowTicker),
                bars = listOf(
                    bar(apple, open = 100.0, close = 110.0),
                    bar(microsoft, open = 200.0, close = 180.0),
                    bar(nonMember, open = 100.0, close = 200.0),
                    bar(etfWithDowTicker, open = 50.0, close = 100.0),
                ),
                previous = previousMap(MarketIndexId.DOW_JONES_INDUSTRIAL_AVERAGE to 30_000.0),
            ),
        )

        // 30,000 × (110+180)/(100+200) = 29,000
        val dow = result.getValue(MarketIndexId.DOW_JONES_INDUSTRIAL_AVERAGE)
        assertEquals(29_000.0, dow.value, 1e-10)
        assertEquals(2, dow.constituentCount)
    }

    @Test
    fun dowUsesPriceWeightedGapAndOhlcAgainstConstituentPreviousCloses() {
        val apple = stock("AAPL", Market.NASDAQ, marketCap = 3_000.0)
        val microsoft = stock("MSFT", Market.NASDAQ, marketCap = 9_000.0)
        val previous = snapshot(
            id = MarketIndexId.DOW_JONES_INDUSTRIAL_AVERAGE,
            value = 30_000.0,
            sessionDate = LocalDate(2026, 8, 6),
        )

        val dow = MarketIndexEngine().calculate(
            input(
                stocks = listOf(apple, microsoft),
                bars = listOf(
                    bar(apple, open = 110.0, high = 130.0, low = 90.0, close = 120.0),
                    bar(microsoft, open = 180.0, high = 210.0, low = 160.0, close = 200.0),
                ),
                previous = mapOf(MarketIndexId.DOW_JONES_INDUSTRIAL_AVERAGE to previous),
                previousCloses = mapOf(apple.id to 100.0, microsoft.id to 200.0),
            ),
        ).getValue(MarketIndexId.DOW_JONES_INDUSTRIAL_AVERAGE)

        // 직전 종가 합 300에 대해 각 endpoint의 가격 합을 계산한다.
        assertEquals(30_000.0, dow.previousClose, 1e-10)
        assertEquals(29_000.0, dow.open, 1e-10)
        assertEquals(34_000.0, dow.high, 1e-10)
        assertEquals(25_000.0, dow.low, 1e-10)
        assertEquals(32_000.0, dow.value, 1e-10)
    }

    @Test
    fun vixProxyIsPositiveMeanRevertingAndRisesOnSharpSp500Fall() {
        val stock = stock("DROP", Market.NYSE, marketCap = 100.0)
        val previous = previousMap(
            MarketIndexId.SP_500 to 1_000.0,
            MarketIndexId.VIX to 18.0,
        )
        val falling = MarketIndexEngine().calculate(
            input(
                stocks = listOf(stock),
                // 장중에는 변하지 않았지만 직전 종가 100 대비 95로 갭 하락한 봉.
                bars = listOf(bar(stock, open = 95.0, close = 95.0)),
                previous = previous,
                previousCloses = mapOf(stock.id to 100.0),
            ),
        ).getValue(MarketIndexId.VIX)

        assertTrue(falling.value > 18.0)
        assertTrue(falling.value > 0.0)

        val elevatedPrevious = previousMap(MarketIndexId.VIX to 100.0)
        val reverting = MarketIndexEngine().calculate(
            input(
                stocks = listOf(stock),
                bars = listOf(bar(stock, open = 100.0, close = 100.0)),
                previous = elevatedPrevious,
            ),
        ).getValue(MarketIndexId.VIX)
        assertTrue(reverting.value in 0.0..<100.0)
    }

    @Test
    fun calculationIsDeterministicCompleteAndInitializesAllFourIds() {
        val stock = stock("AAPL", Market.NASDAQ, marketCap = 100.0)
        val input = input(
            stocks = listOf(stock),
            bars = listOf(bar(stock, open = 100.0, close = 101.0)),
            previous = emptyMap(),
            macro = MacroEnvironment(volatilityRegime = 1.2, riskSentiment = -0.3),
        )
        val engine = MarketIndexEngine()

        assertEquals(engine.calculate(input), engine.calculate(input))
        assertEquals(MarketIndexId.entries.toSet(), engine.calculate(input).keys)
        val initial = engine.initialSnapshots(time)
        assertEquals(MarketIndexId.entries.toSet(), initial.keys)
        MarketIndexId.entries.forEach { id ->
            assertEquals(MarketIndexCatalog[id].initialValue, initial.getValue(id).value)
            assertTrue(initial.getValue(id).isSimulationProxy)
            assertTrue(MarketIndexCatalog[id].officialMethodologyUrl.startsWith("https://"))
        }
    }

    @Test
    fun closedHourIsFlatAndPreservesPreviousCloseAndDailyOhlc() {
        val previous = MarketIndexSnapshot(
            id = MarketIndexId.SP_500,
            timestamp = time - 1.hours,
            value = 1_010.0,
            previousClose = 990.0,
            open = 1_000.0,
            high = 1_020.0,
            low = 980.0,
            constituentCount = 8,
            sessionDate = LocalDate(2026, 8, 7),
        )
        val result = MarketIndexEngine().calculate(
            MarketIndexCalculationInput(
                timestamp = time,
                stocks = emptyList(),
                barsByStockId = emptyMap(),
                previousIndices = mapOf(MarketIndexId.SP_500 to previous),
                usTradingFraction = 0.0,
            ),
        ).getValue(MarketIndexId.SP_500)

        assertEquals(previous.copy(timestamp = time), result)
    }

    @Test
    fun dowConstituentSnapshotContainsThirtySymbolsAndCurrentAlphabetChange() {
        assertEquals(30, MarketIndexEngine.DJIA_30_SYMBOLS.size)
        assertTrue("GOOGL" in MarketIndexEngine.DJIA_30_SYMBOLS)
        assertFalse("VZ" in MarketIndexEngine.DJIA_30_SYMBOLS)
    }

    private fun input(
        stocks: List<StockDefinition>,
        bars: List<PriceBar>,
        previous: Map<MarketIndexId, MarketIndexSnapshot>,
        previousCloses: Map<String, Double> = emptyMap(),
        macro: MacroEnvironment = MacroEnvironment(),
    ): MarketIndexCalculationInput = MarketIndexCalculationInput(
        timestamp = time,
        stocks = stocks,
        barsByStockId = bars.associateBy(PriceBar::stockId),
        previousCloseByStockId = previousCloses,
        previousIndices = previous,
        macro = macro,
        usTradingFraction = 1.0,
    )

    private fun previousMap(
        vararg values: Pair<MarketIndexId, Double>,
    ): Map<MarketIndexId, MarketIndexSnapshot> = values.associate { (id, value) ->
        id to snapshot(
            id = id,
            value = value,
            sessionDate = LocalDate(2026, 8, 7),
        )
    }

    private fun snapshot(
        id: MarketIndexId,
        value: Double,
        previousClose: Double = value,
        open: Double = value,
        high: Double = maxOf(open, value),
        low: Double = minOf(open, value),
        sessionDate: LocalDate,
    ): MarketIndexSnapshot = MarketIndexSnapshot(
        id = id,
        timestamp = time - 1.hours,
        value = value,
        previousClose = previousClose,
        open = open,
        high = high,
        low = low,
        sessionDate = sessionDate,
    )

    private fun stock(symbol: String, market: Market, marketCap: Double): StockDefinition =
        StockDefinition(
            symbol = symbol,
            name = symbol,
            englishName = symbol,
            market = market,
            sector = Sector.INFORMATION_TECHNOLOGY,
            initialPrice = 100.0,
            volatility = 0.2,
            dividendYield = 0.0,
            marketCap = marketCap,
            sharesOutstanding = 1_000_000L,
            description = "지수 엔진 테스트 종목",
        )

    private fun StockDefinition.asEtf(): StockDefinition = copy(
        etfProfile = EtfProfile(
            benchmark = "Test Index",
            assetClass = EtfAssetClass.BROAD_EQUITY,
            taxCategory = EtfTaxCategory.FOREIGN_LISTED,
            annualExpenseRatio = 0.001,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
        ),
    )

    private fun bar(
        stock: StockDefinition,
        open: Double,
        close: Double,
        high: Double = maxOf(open, close),
        low: Double = minOf(open, close),
    ): PriceBar = PriceBar(
        stockId = stock.id,
        startTime = time - 1.hours,
        endTime = time,
        step = TurnStep.ONE_HOUR,
        open = open,
        high = high,
        low = low,
        close = close,
        volume = 1_000L,
    )
}
