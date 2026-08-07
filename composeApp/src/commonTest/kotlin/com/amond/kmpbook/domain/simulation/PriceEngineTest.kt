package com.amond.kmpbook.domain.simulation

import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.MarketSession
import com.amond.kmpbook.domain.model.CurrencyExposureLeg
import com.amond.kmpbook.domain.model.EtfAssetClass
import com.amond.kmpbook.domain.model.EtfExposureRegion
import com.amond.kmpbook.domain.model.EtfFxProfile
import com.amond.kmpbook.domain.model.EtfProfile
import com.amond.kmpbook.domain.model.EtfTaxCategory
import com.amond.kmpbook.domain.model.ReferenceCurrency
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
    fun coordinatedUsCircuitBreakerLevelsApplyToEveryUsVenue() {
        val stock = testStock(market = Market.NASDAQ, initialPrice = 100.0, volatility = 0.0)
        fun result(level: Int) = PriceEngine(3L).generateHour(
            PriceGenerationInput(
                stock = stock,
                startTime = time,
                previousPrice = 100.0,
                dailyBasePrice = 100.0,
                session = MarketSession.REGULAR,
                macro = MacroEnvironment(usCircuitBreakerLevel = level),
            ),
        )

        assertEquals(TradingStabilizer.US_LEVEL_1_REOPENED, result(1).stabilizer)
        assertEquals(TradingStabilizer.US_LEVEL_2_REOPENED, result(2).stabilizer)
        val levelThree = result(3)
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

    @Test
    fun partialSessionAppliesTheUnscaledMarketFactorOnce() {
        val stock = testStock(volatility = 0.0, beta = 1.0)
        val result = PriceEngine(42L).generateHour(
            PriceGenerationInput(
                stock = stock,
                startTime = time,
                previousPrice = stock.initialPrice,
                dailyBasePrice = stock.initialPrice,
                session = MarketSession.REGULAR,
                macro = MacroEnvironment(marketHourlyReturns = mapOf(stock.market to 0.02)),
                regularTradingFraction = 0.5,
                referenceTradingFraction = 0.5,
            ),
        )

        assertEquals(0.01, result.attribution.market, 1e-12)
    }

    @Test
    fun domesticUnhedgedHedgedAndUsListedEtfsApplyFxExactlyOnce() {
        val unhedged = etf(
            market = Market.KOSPI,
            fxProfile = fxProfile(ReferenceCurrency.USD, hedgeRatio = 0.0),
        )
        val hedged = etf(
            market = Market.KOSPI,
            fxProfile = fxProfile(ReferenceCurrency.USD, hedgeRatio = 1.0),
        )
        val usListed = etf(
            market = Market.NYSE_ARCA,
            fxProfile = fxProfile(ReferenceCurrency.USD, hedgeRatio = 0.0),
        )
        val macro = fxMacro(
            previous = mapOf(ReferenceCurrency.USD to 1_000.0),
            current = mapOf(ReferenceCurrency.USD to 1_100.0),
        )

        val unhedgedResult = generate(unhedged, macro)
        val hedgedResult = generate(hedged, macro)
        val usResult = generate(usListed, macro)

        assertEquals(kotlin.math.ln(1.1), unhedgedResult.attribution.foreignExchange, 1e-12)
        assertEquals(0.0, hedgedResult.attribution.foreignExchange, 1e-12)
        assertEquals(0.0, usResult.attribution.foreignExchange, 1e-12)
        assertEquals(usResult.bar.close * 1_100.0, usResult.closeValueKrw, 1e-8)
    }

    @Test
    fun usListedJapanEtfUsesJpyUsdNavThenUsdKrwPortfolioValuation() {
        val unhedged = etf(
            market = Market.NYSE_ARCA,
            fxProfile = fxProfile(ReferenceCurrency.JPY, hedgeRatio = 0.0),
        )
        val hedged = etf(
            market = Market.NYSE_ARCA,
            fxProfile = fxProfile(ReferenceCurrency.JPY, hedgeRatio = 1.0),
        )
        val macro = fxMacro(
            previous = mapOf(ReferenceCurrency.USD to 1_000.0, ReferenceCurrency.JPY to 9.0),
            current = mapOf(ReferenceCurrency.USD to 1_100.0, ReferenceCurrency.JPY to 10.0),
        )

        val unhedgedResult = generate(unhedged, macro)
        val hedgedResult = generate(hedged, macro)
        val expectedJpyUsd = kotlin.math.ln((10.0 / 9.0) / (1_100.0 / 1_000.0))

        assertEquals(expectedJpyUsd, unhedgedResult.attribution.foreignExchange, 1e-12)
        assertEquals(0.0, hedgedResult.attribution.foreignExchange, 1e-12)
        assertEquals(
            kotlin.math.ln(10.0 / 9.0),
            unhedgedResult.attribution.foreignExchange + kotlin.math.ln(1_100.0 / 1_000.0),
            1e-12,
        )
    }

    @Test
    fun leverageDoesNotMultiplyTheExplicitCurrencyNotional() {
        val macro = fxMacro(
            previous = mapOf(ReferenceCurrency.USD to 1_000.0),
            current = mapOf(ReferenceCurrency.USD to 1_100.0),
        )
        val oneX = generate(etf(leverage = 1.0), macro)
        val inverse = generate(etf(leverage = -1.0), macro)
        val twoX = generate(etf(leverage = 2.0), macro)

        assertEquals(oneX.attribution.foreignExchange, inverse.attribution.foreignExchange, 1e-12)
        assertEquals(oneX.attribution.foreignExchange, twoX.attribution.foreignExchange, 1e-12)
    }

    @Test
    fun closedListingCanCarryReferenceReturnIntoTheNextOpen() {
        val stock = etf(volatility = 0.0)
        val activeMacro = MacroEnvironment(
            regionalEtfHourlyReturns = mapOf(EtfExposureRegion.UNITED_STATES to 0.01),
        )
        val carry = PriceEngine(1L).referenceLogReturn(
            stock = stock,
            macro = activeMacro,
            referenceTradingFraction = 1.0,
            fxTradingFraction = 0.0,
        )
        val opened = PriceEngine(1L).generateHour(
            PriceGenerationInput(
                stock = stock,
                startTime = time,
                previousPrice = stock.initialPrice,
                dailyBasePrice = stock.initialPrice,
                session = MarketSession.REGULAR,
                referenceTradingFraction = 0.0,
                carriedReferenceLogReturn = carry,
                isFirstRegularBarOfDay = true,
            ),
        )

        assertEquals(0.01, carry, 1e-12)
        assertEquals(carry, opened.attribution.carriedReference, 1e-12)
        assertTrue(opened.bar.open > stock.initialPrice)
        assertEquals(opened.bar.open, opened.bar.close)
        assertEquals(opened.bar.open, opened.quote.open)
    }

    @Test
    fun closedReferenceCarryDoesNotAccrueFundCostsTwice() {
        val stock = etf(
            annualExpenseRatio = 0.01,
            fxProfile = fxProfile(
                currency = ReferenceCurrency.USD,
                hedgeRatio = 1.0,
                annualHedgeCostRate = 0.005,
            ),
        )
        val engine = PriceEngine(3L)
        val closedCarry = engine.referenceLogReturn(
            stock = stock,
            macro = MacroEnvironment(),
            referenceTradingFraction = 1.0,
            fxTradingFraction = 1.0,
        )
        val open = engine.generateHour(
            PriceGenerationInput(
                stock = stock,
                startTime = time,
                previousPrice = stock.initialPrice,
                dailyBasePrice = stock.initialPrice,
                session = MarketSession.REGULAR,
            ),
        )

        assertEquals(0.0, closedCarry, 1e-12)
        val expectedCarryNetOfCosts = (
            stock.dividendYield * stock.behavior.distributionCoverageRatio - 0.01 - 0.005
            ) / (252.0 * 6.5)
        assertEquals(expectedCarryNetOfCosts, open.attribution.fundCosts, 1e-12)
    }

    private fun etf(
        market: Market = Market.KOSPI,
        leverage: Double = 1.0,
        volatility: Double = 0.0,
        fxProfile: EtfFxProfile = fxProfile(ReferenceCurrency.USD, 0.0),
        annualExpenseRatio: Double = 0.0,
    ) = testStock(
        symbol = "ETF${market.name.take(2)}${leverage}",
        market = market,
        initialPrice = if (market.isKorean) 10_000.0 else 100.0,
        volatility = volatility,
        beta = 1.0,
    ).copy(
        etfProfile = EtfProfile(
            benchmark = "테스트 지수",
            assetClass = EtfAssetClass.BROAD_EQUITY,
            taxCategory = if (market.isKorean) {
                EtfTaxCategory.KOREAN_OTHER
            } else {
                EtfTaxCategory.FOREIGN_LISTED
            },
            annualExpenseRatio = annualExpenseRatio,
            leverage = leverage,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            fxProfile = fxProfile,
        ),
    )

    private fun fxProfile(
        currency: ReferenceCurrency,
        hedgeRatio: Double,
        annualHedgeCostRate: Double = 0.0,
    ) = EtfFxProfile(
        legs = listOf(CurrencyExposureLeg(currency, 1.0, hedgeRatio)),
        annualHedgeCostRate = annualHedgeCostRate,
    )

    private fun fxMacro(
        previous: Map<ReferenceCurrency, Double>,
        current: Map<ReferenceCurrency, Double>,
    ): MacroEnvironment = MacroEnvironment(
        usdKrw = current.getValue(ReferenceCurrency.USD),
        previousUsdKrw = previous.getValue(ReferenceCurrency.USD),
        fxRatesToKrw = current + (ReferenceCurrency.KRW to 1.0),
        previousFxRatesToKrw = previous + (ReferenceCurrency.KRW to 1.0),
    )

    private fun generate(stock: com.amond.kmpbook.domain.model.StockDefinition, macro: MacroEnvironment) =
        PriceEngine(55L).generateHour(
            PriceGenerationInput(
                stock = stock,
                startTime = time,
                previousPrice = stock.initialPrice,
                dailyBasePrice = stock.initialPrice,
                session = MarketSession.REGULAR,
                macro = macro,
            ),
        )
}
