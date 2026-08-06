package com.amond.kmpbook.domain.simulation

import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.MarketSession
import com.amond.kmpbook.domain.model.Sector
import kotlin.math.abs
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PriceEngineTest {
    private val time = Instant.parse("2026-08-07T01:00:00Z")

    @Test
    fun sameSeedAndInputProduceIdenticalOhlcv() {
        val stock = testStock()
        val input = PriceGenerationInput(
            stock = stock,
            startTime = time,
            previousPrice = stock.initialPrice,
            dailyBasePrice = stock.initialPrice,
            session = MarketSession.REGULAR,
            macro = MacroEnvironment(
                marketHourlyReturns = mapOf(stock.market to 0.002),
                sectorHourlyReturns = mapOf(stock.sector to -0.001),
                riskSentiment = 0.2,
            ),
        )

        assertEquals(PriceEngine(777L).generateHour(input), PriceEngine(777L).generateHour(input))
    }

    @Test
    fun closedMarketProducesFlatZeroVolumeBar() {
        val stock = testStock()
        val input = PriceGenerationInput(
            stock = stock,
            startTime = time,
            previousPrice = 71_230.0,
            dailyBasePrice = 70_000.0,
            session = MarketSession.CLOSED,
            eventImpulse = PriceImpulse(returnRate = 0.9, volatilityMultiplier = 10.0, volumeMultiplier = 10.0),
        )

        val result = PriceEngine(1L).generateHour(input)

        assertEquals(71_230.0, result.bar.open)
        assertEquals(result.bar.open, result.bar.high)
        assertEquals(result.bar.open, result.bar.low)
        assertEquals(result.bar.open, result.bar.close)
        assertEquals(0L, result.bar.volume)
        assertEquals(TradingStabilizer.MARKET_CLOSED, result.stabilizer)
    }

    @Test
    fun krxExtremeMovesStopAtDailyLimitsAndRemainPositive() {
        val stock = testStock(volatility = 0.0)
        val limits = requireNotNull(MarketMicrostructure.dailyPriceLimits(stock.market, stock.initialPrice))
        val baseInput = PriceGenerationInput(
            stock = stock,
            startTime = time,
            previousPrice = stock.initialPrice,
            dailyBasePrice = stock.initialPrice,
            session = MarketSession.REGULAR,
        )

        val upper = PriceEngine(5L).generateHour(
            baseInput.copy(eventImpulse = PriceImpulse(returnRate = 5.0)),
        )
        val lower = PriceEngine(5L).generateHour(
            baseInput.copy(eventImpulse = PriceImpulse(returnRate = -0.99)),
        )

        assertEquals(limits.upper, upper.bar.close)
        assertEquals(TradingStabilizer.KRX_UPPER_LIMIT, upper.stabilizer)
        assertEquals(limits.lower, lower.bar.close)
        assertEquals(TradingStabilizer.KRX_LOWER_LIMIT, lower.stabilizer)
        for (bar in listOf(upper.bar, lower.bar)) {
            assertTrue(bar.low > 0.0)
            assertTrue(bar.low >= limits.lower)
            assertTrue(bar.high <= limits.upper)
            assertTrue(bar.high >= maxOf(bar.open, bar.close))
            assertTrue(bar.low <= minOf(bar.open, bar.close))
        }
    }

    @Test
    fun usCircuitBreakersApplySevenThirteenTwentyThresholds() {
        val stock = testStock(market = Market.NASDAQ, initialPrice = 100.0, volatility = 0.0)
        fun result(drawdown: Double) = PriceEngine(3L).generateHour(
            PriceGenerationInput(
                stock = stock,
                startTime = time,
                previousPrice = 100.0,
                dailyBasePrice = 100.0,
                session = MarketSession.REGULAR,
                macro = MacroEnvironment(marketChangeFromPreviousClose = mapOf(Market.NASDAQ to drawdown)),
            ),
        )

        assertEquals(TradingStabilizer.US_LEVEL_1_REOPENED, result(-0.07).stabilizer)
        assertEquals(TradingStabilizer.US_LEVEL_2_REOPENED, result(-0.13).stabilizer)
        val levelThree = result(-0.20)
        assertEquals(TradingStabilizer.US_LEVEL_3_HALTED, levelThree.stabilizer)
        assertEquals(100.0, levelThree.bar.close)
        assertEquals(0L, levelThree.bar.volume)
    }

    @Test
    fun usIndividualMoveIsStabilizedWithoutArtificialDailyLimit() {
        val stock = testStock(market = Market.NYSE, initialPrice = 100.0, volatility = 0.0)
        val result = PriceEngine(9L).generateHour(
            PriceGenerationInput(
                stock = stock,
                startTime = time,
                previousPrice = 100.0,
                dailyBasePrice = 100.0,
                session = MarketSession.REGULAR,
                eventImpulse = PriceImpulse(returnRate = 1.0),
            ),
        )

        assertEquals(TradingStabilizer.US_VOLATILITY_PAUSE, result.stabilizer)
        assertTrue(result.bar.close <= 135.0)
        assertTrue(result.bar.close > 0.0)
    }

    @Test
    fun exchangeRateAffectsExporterAndUsdValuation() {
        val exporter = testStock(
            market = Market.KOSPI,
            sector = Sector.SEMICONDUCTOR,
            volatility = 0.0,
        )
        val flatFx = PriceEngine(22L).generateHour(
            PriceGenerationInput(
                stock = exporter,
                startTime = time,
                previousPrice = exporter.initialPrice,
                dailyBasePrice = exporter.initialPrice,
                session = MarketSession.REGULAR,
                macro = MacroEnvironment(usdKrw = 1_350.0, previousUsdKrw = 1_350.0),
            ),
        )
        val weakKrw = PriceEngine(22L).generateHour(
            PriceGenerationInput(
                stock = exporter,
                startTime = time,
                previousPrice = exporter.initialPrice,
                dailyBasePrice = exporter.initialPrice,
                session = MarketSession.REGULAR,
                macro = MacroEnvironment(usdKrw = 1_400.0, previousUsdKrw = 1_350.0),
            ),
        )
        assertTrue(weakKrw.attribution.foreignExchange > 0.0)
        assertTrue(weakKrw.bar.close > flatFx.bar.close)

        val usStock = testStock(market = Market.NASDAQ, volatility = 0.0)
        val usResult = PriceEngine(22L).generateHour(
            PriceGenerationInput(
                stock = usStock,
                startTime = time,
                previousPrice = usStock.initialPrice,
                dailyBasePrice = usStock.initialPrice,
                session = MarketSession.REGULAR,
                macro = MacroEnvironment(usdKrw = 1_400.0, previousUsdKrw = 1_350.0),
            ),
        )
        assertTrue(abs(usResult.closeValueKrw - usResult.bar.close * 1_400.0) < 1e-8)
    }

    @Test
    fun krxFinalHalfHourUsesExplicitHalfSessionApproximation() {
        val stock = testStock(volatility = 0.0)
        val common = PriceGenerationInput(
            stock = stock,
            startTime = time,
            previousPrice = stock.initialPrice,
            dailyBasePrice = stock.initialPrice,
            session = MarketSession.REGULAR,
            averageDailyVolume = 10_000_000L,
            eventImpulse = PriceImpulse(returnRate = 0.01),
        )
        val full = PriceEngine(41L).generateHour(common)
        val half = PriceEngine(41L).generateHour(common.copy(regularTradingFraction = 0.5))

        assertTrue(half.bar.volume < full.bar.volume)
        assertTrue(abs(half.attribution.event - kotlin.math.ln(1.01) * 0.5) < 1e-12)
        assertTrue(abs(full.attribution.event - kotlin.math.ln(1.01)) < 1e-12)
    }
}
