package com.amond.kmpbook.domain.simulation.price

import com.amond.kmpbook.domain.model.instrument.EtfExposureRegion
import com.amond.kmpbook.domain.model.instrument.EtfFxProfile
import com.amond.kmpbook.domain.model.instrument.InstrumentBehaviorProfile
import com.amond.kmpbook.domain.model.instrument.InstrumentStrategy
import com.amond.kmpbook.domain.model.instrument.InstrumentType
import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.market.Currency
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.market.ReferenceCurrency
import com.amond.kmpbook.domain.model.market.Sector
import com.amond.kmpbook.domain.model.pricing.PriceBar
import com.amond.kmpbook.domain.model.pricing.PriceBarInterval
import com.amond.kmpbook.domain.model.pricing.Quote
import com.amond.kmpbook.domain.model.venue.MarketSession
import com.amond.kmpbook.domain.simulation.market.MacroEnvironment
import com.amond.kmpbook.domain.simulation.market.MarketMicrostructure
import com.amond.kmpbook.domain.simulation.protection.TradingStabilizer
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Generates one-hour OHLCV bars. Randomness is keyed by seed, stock id, and
 * start time, so iteration order and unrelated random calls cannot change a
 * stock's history.
 */
class PriceEngine(private val seed: Long) {
    fun generateHour(input: PriceGenerationInput): PriceGenerationResult {
        val endTime = input.startTime + 1.hours
        val stock = input.stock
        val referenceFraction = input.referenceTradingFraction ?: input.regularTradingFraction
        val fairValueFraction = input.fairValueTradingFraction ?: input.regularTradingFraction
        val circuitLevel = if (stock.market.isUnitedStates) input.macro.usCircuitBreakerLevel else 0
        // A level-three trigger can occur partway through this wall-clock hour. Preserve
        // the pre-trigger trading interval and its opening carry; only a fully blocked
        // hour is flat. The resulting partial bar is still marked as halted at its end.
        if (circuitLevel == 3 && input.regularTradingFraction == 0.0) {
            return haltedUsResult(input, endTime)
        }
        if (!input.session.isTradable || input.regularTradingFraction == 0.0) {
            return closedResult(input, endTime)
        }

        val random = DeterministicRandom(
            DeterministicRandom.mixSeed(seed, stableHash64(stock.id), input.startTime.epochSeconds),
        )

        val hoursPerTradingYear = TRADING_DAYS_PER_YEAR * TRADING_HOURS_PER_DAY
        val circuitVolatilityFactor = when (circuitLevel) {
            1, 2 -> US_REOPENED_VOLATILITY_FACTOR
            else -> 1.0
        }
        val behavior = stock.behavior
        val volatilityScale = idiosyncraticRegimeScale(input.macro.volatilityRegime) *
            input.eventImpulse.volatilityMultiplier *
            circuitVolatilityFactor *
            sqrt(input.regularTradingFraction)
        val referenceReturnOverride = input.productFairValueLogReturn ?: input.basketGrossLogReturn
        val referenceResidualVolatility = if (referenceReturnOverride == null) {
            referenceResidualVolatility(
                stock = stock,
                macro = input.macro,
                eventImpulse = input.eventImpulse,
                tradingFraction = input.regularTradingFraction,
            )
        } else {
            0.0
        }
        val priceDislocationVolatility = (
            behavior.priceDislocationVolatility / sqrt(hoursPerTradingYear) * volatilityScale
            ).coerceIn(0.0, MAX_HOURLY_VOLATILITY)
        val hourlyVolatility = sqrt(
            referenceResidualVolatility * referenceResidualVolatility +
                priceDislocationVolatility * priceDislocationVolatility,
        )

        val fundLeverage = stock.etfProfile?.leverage ?: 1.0
        val basketGrossReturn = input.basketGrossLogReturn
        val productFairValueReturn = input.productFairValueLogReturn
        val factor = factorReturn(stock, input.macro)
        val diffusionFraction = sqrt(referenceFraction)
        val marketComponent = productFairValueReturn ?: basketGrossReturn ?: (
            fundLeverage * stock.beta * strategyParticipation(behavior, factor) *
                factor * diffusionFraction
            )
        val sectorFactor = input.macro.sectorHourlyReturns[stock.sector] ?: 0.0
        val sectorComponent = if (referenceReturnOverride == null) {
            fundLeverage * SECTOR_LOADING *
                strategyParticipation(behavior, sectorFactor) * sectorFactor * diffusionFraction
        } else {
            0.0
        }
        val ratesAndInflation = if (referenceReturnOverride == null) {
            instrumentRateReturn(stock, input.macro)
        } else {
            0.0
        }
        val growthAndSentiment = if (referenceReturnOverride == null) {
            instrumentGrowthAndCreditReturn(stock, input.macro)
        } else {
            0.0
        }
        val orderFlow = if (referenceReturnOverride == null) {
            instrumentOrderFlowReturn(stock, input.macro)
        } else {
            0.0
        }
        val fxReturn = foreignExchangeReturn(stock, input.macro, input.fxSensitivity) *
            fairValueFraction
        val fundCosts = if (productFairValueReturn == null) {
            fundAccrualLogReturn(
                stock = stock,
                fairValueFraction = fairValueFraction,
                annualIncomeYieldOverride = input.basketAnnualIncomeYield,
            )
        } else {
            0.0
        }
        // A bottom-up basket already contains constituent-specific dispersion and its explicit
        // tracking-error term. Reusing the catalog volatility here would add a second, often much
        // larger, unexplained NAV process on top of the actual holdings.
        val referenceResidual = if (referenceReturnOverride == null) {
            -0.5 * referenceResidualVolatility * referenceResidualVolatility +
                referenceResidualVolatility * random.nextGaussian()
        } else {
            0.0
        }
        val gapReversion = if (stock.isFundLike) {
            -priceDislocationReversionRate(stock) * input.priceToReferenceLogGap *
                input.regularTradingFraction
        } else {
            0.0
        }
        val priceDislocation = gapReversion -
            0.5 * priceDislocationVolatility * priceDislocationVolatility +
            priceDislocationVolatility * random.nextGaussian()

        val attribution = PriceAttribution(
            market = marketComponent,
            sector = sectorComponent,
            ratesAndInflation = fundLeverage * ratesAndInflation * referenceFraction,
            growthAndSentiment = fundLeverage * growthAndSentiment * referenceFraction,
            orderFlow = fundLeverage * orderFlow * referenceFraction,
            // Currency hedging is an independent overlay. Multiplying it by an inverse or
            // leveraged equity mandate would incorrectly reverse/double the FX leg.
            foreignExchange = fxReturn,
            referenceEvent = referenceEventLogReturn(
                stock = stock,
                eventImpulse = input.eventImpulse,
                referenceFraction = fairValueFraction,
            ),
            directProductEvent = directProductEventLogReturn(
                eventImpulse = input.eventImpulse,
                fairValueFraction = fairValueFraction,
            ),
            fundCosts = fundCosts,
            carriedReference = input.carriedReferenceLogReturn,
            carriedPriceDislocation = input.carriedPriceDislocationLogReturn,
            referenceResidual = referenceResidual,
            priceDislocation = priceDislocation,
        )

        // A return accumulated while the listing was closed is an opening auction gap,
        // not intrahour drift. Stabilize the live-session return independently so a
        // queued market order observes the same opening price as the OHLC bar.
        val boundedCarry = (
            input.carriedReferenceLogReturn + input.carriedPriceDislocationLogReturn
            ).coerceIn(
            -MAX_RAW_LOG_RETURN,
            MAX_RAW_LOG_RETURN,
        )
        val rawOpen = input.previousPrice * exp(boundedCarry)
        val open = boundedPrice(stock, rawOpen, input.dailyBasePrice)
        val activeLogReturn = (
                attribution.market + attribution.sector + attribution.ratesAndInflation +
                attribution.growthAndSentiment + attribution.orderFlow + attribution.foreignExchange +
                attribution.referenceEvent + attribution.directProductEvent +
                attribution.fundCosts + attribution.referenceResidual + attribution.priceDislocation
            ).coerceIn(-MAX_RAW_LOG_RETURN, MAX_RAW_LOG_RETURN)
        val stabilizedLogReturn = if (stock.market.isUnitedStates) {
            activeLogReturn.coerceIn(-MAX_US_HOURLY_LOG_MOVE, MAX_US_HOURLY_LOG_MOVE)
        } else {
            activeLogReturn
        }
        val volatilityPause = stock.market.isUnitedStates && stabilizedLogReturn != activeLogReturn

        val rawClose = open.price * exp(stabilizedLogReturn)
        val boundedClose = boundedPrice(stock, rawClose, input.dailyBasePrice)
        val close = boundedClose.price

        val rangeScale = (hourlyVolatility + abs(stabilizedLogReturn) * 0.35)
            .coerceAtMost(MAX_INTRAHOUR_RANGE)
        val rawHigh = max(open.price, close) * exp(abs(random.nextGaussian()) * rangeScale * 0.55)
        val rawLow = min(open.price, close) * exp(-abs(random.nextGaussian()) * rangeScale * 0.55)
        val high = boundHigh(stock, rawHigh, input.dailyBasePrice, max(open.price, close))
        val low = boundLow(stock, rawLow, input.dailyBasePrice, min(open.price, close))

        val returnRate = close / open.price - 1.0
        val circuitVolumeFactor = if (circuitLevel in 1..2) US_REOPENED_VOLUME_FACTOR else 1.0
        val volume = generateVolume(
            random = random,
            averageDailyVolume = input.averageDailyVolume,
            absoluteReturn = abs(returnRate),
            multiplier = input.eventImpulse.volumeMultiplier * circuitVolumeFactor *
                (1.0 + 0.75 * abs(input.macro.retailOrderFlow) +
                    0.50 * abs(input.macro.institutionalOrderFlow) +
                    0.35 * input.macro.liquidityStress) * input.regularTradingFraction,
        )

        val bar = PriceBar(
            stockId = stock.id,
            startTime = input.startTime,
            endTime = endTime,
            step = PriceBarInterval.ONE_HOUR,
            open = open.price,
            high = high.price,
            low = low.price,
            close = close,
            volume = volume,
        )
        val stabilizer = when {
            open.hitUpperLimit || boundedClose.hitUpperLimit -> TradingStabilizer.KRX_UPPER_LIMIT
            open.hitLowerLimit || boundedClose.hitLowerLimit -> TradingStabilizer.KRX_LOWER_LIMIT
            circuitLevel == 3 -> TradingStabilizer.US_LEVEL_3_HALTED
            circuitLevel == 2 -> TradingStabilizer.US_LEVEL_2_REOPENED
            circuitLevel == 1 -> TradingStabilizer.US_LEVEL_1_REOPENED
            volatilityPause -> TradingStabilizer.US_VOLATILITY_PAUSE
            else -> TradingStabilizer.NONE
        }
        val quote = Quote(
            stockId = stock.id,
            timestamp = endTime,
            price = close,
            previousClose = input.dailyBasePrice,
            open = if (input.isFirstRegularBarOfDay) open.price else input.dayOpen,
            high = if (input.isFirstRegularBarOfDay) high.price else max(input.dayHigh, high.price),
            low = if (input.isFirstRegularBarOfDay) low.price else min(input.dayLow, low.price),
            volume = volume,
            session = if (circuitLevel == 3) MarketSession.CLOSED else input.session,
        )
        return PriceGenerationResult(
            bar = bar,
            quote = quote,
            closeValueKrw = if (stock.currency == Currency.USD) close * input.macro.usdKrw else close,
            attribution = attribution,
            stabilizer = stabilizer,
            wasClamped = boundedCarry != (
                input.carriedReferenceLogReturn + input.carriedPriceDislocationLogReturn
                ) ||
                boundedClose.wasClamped || open.wasClamped || high.wasClamped ||
                low.wasClamped || volatilityPause,
        )
    }

    private fun factorReturn(stock: StockDefinition, macro: MacroEnvironment): Double {
        val profile = stock.etfProfile ?: return macro.marketHourlyReturns[stock.market] ?: 0.0
        macro.regionalEtfHourlyReturns?.get(profile.exposureRegion)?.let { return it }
        val candidates = when (profile.exposureRegion) {
            EtfExposureRegion.KOREA -> macro.marketHourlyReturns.filterKeys(Market::isKorean).values
            EtfExposureRegion.UNITED_STATES -> macro.marketHourlyReturns
                .filterKeys(Market::isUnitedStates)
                .values
            EtfExposureRegion.GLOBAL -> macro.marketHourlyReturns.values
            EtfExposureRegion.DEVELOPED_EX_US,
            EtfExposureRegion.EMERGING_MARKETS,
            -> macro.marketHourlyReturns.values
        }
        return candidates.takeIf { it.isNotEmpty() }?.average()
            ?: macro.marketHourlyReturns[stock.market]
            ?: 0.0
    }

    /**
     * 상장시장이 닫힌 동안 ETF의 기초시장과 FX fair value만 누적한다. 추적오차 난수와
     * 호가 미시구조는 실제 상장시장이 열릴 때만 생성한다.
     */
    fun referenceLogReturn(
        stock: StockDefinition,
        macro: MacroEnvironment,
        referenceTradingFraction: Double,
        fxTradingFraction: Double = 1.0,
        eventImpulse: PriceImpulse = PriceImpulse(),
        /** 현재 구성 바스켓이 이미 합성한 비용 전 로그수익률. */
        basketGrossLogReturn: Double? = null,
    ): Double {
        require(referenceTradingFraction in 0.0..1.0)
        require(fxTradingFraction in 0.0..1.0)
        require(basketGrossLogReturn == null || basketGrossLogReturn.isFinite())
        val profile = stock.etfProfile ?: return 0.0
        val leverage = profile.leverage
        val behavior = stock.behavior
        val factor = factorReturn(stock, macro)
        val diffusionFraction = sqrt(referenceTradingFraction)
        val market = basketGrossLogReturn ?: (
            leverage * stock.beta * strategyParticipation(behavior, factor) *
                factor * diffusionFraction
            )
        val sectorFactor = macro.sectorHourlyReturns[stock.sector] ?: 0.0
        val sector = if (basketGrossLogReturn == null) {
            leverage * SECTOR_LOADING *
                strategyParticipation(behavior, sectorFactor) * sectorFactor * diffusionFraction
        } else {
            0.0
        }
        val ratesAndInflation = if (basketGrossLogReturn == null) {
            leverage * instrumentRateReturn(stock, macro) * referenceTradingFraction
        } else {
            0.0
        }
        val growthAndSentiment = if (basketGrossLogReturn == null) {
            leverage * instrumentGrowthAndCreditReturn(stock, macro) * referenceTradingFraction
        } else {
            0.0
        }
        val orderFlow = if (basketGrossLogReturn == null) {
            leverage * instrumentOrderFlowReturn(stock, macro) * referenceTradingFraction
        } else {
            0.0
        }
        val fx = structuredFxReturn(stock, macro, profile.fxProfile) * fxTradingFraction
        val event = referenceEventLogReturn(stock, eventImpulse, referenceTradingFraction)
        // Expense and hedge-cost accrual belongs to the listing's regular-session NAV
        // path. Including it here would charge a foreign-market ETF once while its
        // reference trades and again while the listing trades.
        return market + sector + ratesAndInflation + growthAndSentiment + orderFlow + fx + event
    }

    /**
     * 일일 reset 상품의 배율·환헤지·보수와 분리한 기초지수 1배 수익률이다.
     *
     * 상세 벤치마크 엔진이 없는 상품만 이 프록시를 사용한다. 상품 자체의 leverage를 여기서
     * 적용하지 않으므로 [com.amond.kmpbook.domain.simulation.fundproduct.DailyResetEngine]이
     * 전일 종가 기준 누적 단순수익률에 목표 배율을 정확히 한 번 적용할 수 있다.
     */
    fun coarseUnderlyingReferenceLogReturn(
        stock: StockDefinition,
        macro: MacroEnvironment,
        referenceTradingFraction: Double,
        eventImpulse: PriceImpulse = PriceImpulse(),
    ): Double {
        require(referenceTradingFraction in 0.0..1.0)
        require(stock.isFundLike)
        val behavior = stock.behavior
        val factor = factorReturn(stock, macro)
        val diffusionFraction = sqrt(referenceTradingFraction)
        val market = stock.beta * strategyParticipation(behavior, factor) *
            factor * diffusionFraction
        val sectorFactor = macro.sectorHourlyReturns[stock.sector] ?: 0.0
        val sector = SECTOR_LOADING * strategyParticipation(behavior, sectorFactor) *
            sectorFactor * diffusionFraction
        val ratesAndInflation = instrumentRateReturn(stock, macro) * referenceTradingFraction
        val growthAndSentiment = instrumentGrowthAndCreditReturn(stock, macro) *
            referenceTradingFraction
        val orderFlow = instrumentOrderFlowReturn(stock, macro) * referenceTradingFraction
        val event = referenceEventLogReturn(stock, eventImpulse, referenceTradingFraction)
        return market + sector + ratesAndInflation + growthAndSentiment + orderFlow + event
    }

    /**
     * 상장시장의 정규 거래 시계에 따라 NAV에 쌓이는 분배 재원·보수·구조 비용이다.
     * 거래정지 중에는 시장가격을 만들지 않으므로 Runtime이 이 값을 opening carry에
     * 보존하고 재개 시 가격과 NAV가 같은 공정가치 원장을 한 번 소비한다.
     */
    fun fundAccrualLogReturn(
        stock: StockDefinition,
        fairValueFraction: Double,
        annualIncomeYieldOverride: Double? = null,
    ): Double {
        require(fairValueFraction in 0.0..1.0)
        require(annualIncomeYieldOverride == null || annualIncomeYieldOverride in 0.0..1.0)
        val profile = stock.etfProfile ?: return 0.0
        val hoursPerTradingYear = TRADING_DAYS_PER_YEAR * TRADING_HOURS_PER_DAY
        val behavior = stock.behavior
        // 분배 재원은 NAV에 먼저 적립된 뒤 배당락일에 빠진다. 커버드콜·ETN의
        // coverage<1 부분은 이 적립에서 제외되므로 반복 분배가 원금을 잠식할 수 있다.
        val annualIncomeYield = annualIncomeYieldOverride ?: stock.dividendYield
        val earnedDistributionCarry = annualIncomeYield * behavior.distributionCoverageRatio /
            hoursPerTradingYear * fairValueFraction
        val annualFundCosts = profile.annualExpenseRatio + profile.fxProfile.annualHedgeCostRate
        return earnedDistributionCarry - (
            annualFundCosts + behavior.annualStructuralDrag
            ) / hoursPerTradingYear * fairValueFraction +
            dailyResetVolatilityDrag(stock, hoursPerTradingYear) * fairValueFraction
    }

    /**
     * 거래정지 중에도 사라지면 안 되는 종목 고유 공정가치 잔차다. 정상 가격 생성과 같은
     * 종목·시각 seed와 첫 Gaussian draw를 사용해 재개 carry가 반복 순서에 의존하지 않는다.
     */
    fun referenceResidualLogReturn(
        stock: StockDefinition,
        startTime: Instant,
        macro: MacroEnvironment,
        eventImpulse: PriceImpulse,
        tradingFraction: Double,
    ): Double {
        require(tradingFraction in 0.0..1.0)
        val volatility = referenceResidualVolatility(
            stock = stock,
            macro = macro,
            eventImpulse = eventImpulse,
            tradingFraction = tradingFraction,
        )
        val random = DeterministicRandom(
            DeterministicRandom.mixSeed(seed, stableHash64(stock.id), startTime.epochSeconds),
        )
        return -0.5 * volatility * volatility + volatility * random.nextGaussian()
    }

    private fun referenceResidualVolatility(
        stock: StockDefinition,
        macro: MacroEnvironment,
        eventImpulse: PriceImpulse,
        tradingFraction: Double,
    ): Double {
        val circuitLevel = if (stock.market.isUnitedStates) macro.usCircuitBreakerLevel else 0
        val circuitVolatilityFactor = when (circuitLevel) {
            1, 2 -> US_REOPENED_VOLATILITY_FACTOR
            else -> 1.0
        }
        val hoursPerTradingYear = TRADING_DAYS_PER_YEAR * TRADING_HOURS_PER_DAY
        val volatilityScale = idiosyncraticRegimeScale(macro.volatilityRegime) *
            eventImpulse.volatilityMultiplier *
            circuitVolatilityFactor * sqrt(tradingFraction)
        return (stock.volatility / sqrt(hoursPerTradingYear) * volatilityScale)
            .coerceIn(0.0, MAX_HOURLY_VOLATILITY)
    }

    /**
     * Converts an event impulse to the exact log-return term used by live pricing. Runtime uses
     * the same function to carry the closed portion of an event into the next opening auction.
     */
    fun eventLogReturn(
        stock: StockDefinition,
        eventImpulse: PriceImpulse,
        referenceFraction: Double = 1.0,
        fairValueFraction: Double = 1.0,
    ): Double {
        require(referenceFraction in 0.0..1.0)
        require(fairValueFraction in 0.0..1.0)
        return referenceEventLogReturn(stock, eventImpulse, referenceFraction) +
            directProductEventLogReturn(eventImpulse, fairValueFraction)
    }

    fun referenceEventLogReturn(
        stock: StockDefinition,
        eventImpulse: PriceImpulse,
        referenceFraction: Double = 1.0,
    ): Double {
        require(referenceFraction in 0.0..1.0)
        val leverage = stock.etfProfile?.leverage ?: 1.0
        return leverage * strategyParticipation(stock.behavior, eventImpulse.referenceReturnRate) *
            ln(1.0 + eventImpulse.referenceReturnRate) * referenceFraction
    }

    fun directProductEventLogReturn(
        eventImpulse: PriceImpulse,
        fairValueFraction: Double = 1.0,
    ): Double {
        require(fairValueFraction in 0.0..1.0)
        return ln(1.0 + eventImpulse.directProductReturnRate) * fairValueFraction
    }

    private fun foreignExchangeReturn(
        stock: StockDefinition,
        macro: MacroEnvironment,
        stockFxSensitivity: Double,
    ): Double {
        val profile = stock.etfProfile
        if (profile != null) return structuredFxReturn(stock, macro, profile.fxProfile)

        val listingCurrency = when (stock.currency) {
            Currency.KRW -> ReferenceCurrency.KRW
            Currency.USD -> ReferenceCurrency.USD
        }
        val listingReturn = ln(
            macro.rateToKrw(listingCurrency) / macro.rateToKrw(listingCurrency, previous = true),
        )
        val referenceCurrency = stock.behavior.referenceCurrency
        if (referenceCurrency != null) {
            val referenceReturn = ln(
                macro.rateToKrw(referenceCurrency) /
                    macro.rateToKrw(referenceCurrency, previous = true),
            )
            return stock.behavior.referenceCurrencySensitivity * (referenceReturn - listingReturn)
        }
        return ln(macro.usdKrw / macro.previousUsdKrw) * stockFxSensitivity
    }

    private fun structuredFxReturn(
        stock: StockDefinition,
        macro: MacroEnvironment,
        fxProfile: EtfFxProfile,
    ): Double {
        val listingCurrency = when (stock.currency) {
            Currency.KRW -> ReferenceCurrency.KRW
            Currency.USD -> ReferenceCurrency.USD
        }
        val listingReturn = ln(
            macro.rateToKrw(listingCurrency) / macro.rateToKrw(listingCurrency, previous = true),
        )
        return fxProfile.legs.sumOf { leg ->
            val legReturn = ln(
                macro.rateToKrw(leg.currency) / macro.rateToKrw(leg.currency, previous = true),
            )
            leg.netNotional * (legReturn - listingReturn)
        }
    }

    private fun strategyParticipation(
        behavior: com.amond.kmpbook.domain.model.instrument.InstrumentBehaviorProfile,
        factor: Double,
    ): Double = if (factor >= 0.0) behavior.upsideParticipation else behavior.downsideParticipation

    /**
     * 체계적 팩터는 MarketDynamicsEngine에서 이미 국면 배율을 전부 받는다. 종목 고유
     * 분산도 스트레스에 늘지만 탄력도는 1보다 작게 두어 공통 변동성을 모든 종목의
     * 독립 잡음에 다시 한 번 선형 복제하지 않는다.
     */
    private fun idiosyncraticRegimeScale(volatilityRegime: Double): Double =
        exp(ln(volatilityRegime.coerceAtLeast(0.1)) * IDIOSYNCRATIC_VOLATILITY_ELASTICITY)

    private fun instrumentRateReturn(stock: StockDefinition, macro: MacroEnvironment): Double {
        val behavior = stock.behavior
        if (behavior.durationYears != 0.0 || behavior.cashRateAccrual > 0.0) {
            val nominalDurationMove = -behavior.durationYears * macro.policyRateChange
            val inflationLinkedMove = if (behavior.strategy == InstrumentStrategy.INFLATION_LINKED_BOND) {
                behavior.durationYears * macro.inflationSurprise * HOURLY_MACRO_SCALE * 0.32
            } else {
                0.0
            }
            val cashCarry = macro.policyRate * behavior.cashRateAccrual /
                (TRADING_DAYS_PER_YEAR * TRADING_HOURS_PER_DAY)
            return nominalDurationMove + inflationLinkedMove + cashCarry
        }
        return -rateSensitivity(stock.sector) * macro.policyRateChange -
            inflationSensitivity(stock.sector) * macro.inflationSurprise * HOURLY_MACRO_SCALE
    }

    private fun instrumentGrowthAndCreditReturn(stock: StockDefinition, macro: MacroEnvironment): Double {
        val behavior = stock.behavior
        val ordinary = growthSensitivity(stock.sector) * macro.growthSurprise * HOURLY_MACRO_SCALE +
            macro.riskSentiment * RISK_SENTIMENT_HOURLY_SCALE
        val credit = behavior.creditSpreadSensitivity * (
            macro.growthSurprise * CREDIT_GROWTH_SCALE +
                macro.riskSentiment * CREDIT_SENTIMENT_SCALE
            )
        val commodityLoading = if (behavior.commodityFactorSensitivity != 0.0) {
            behavior.commodityFactorSensitivity
        } else if (behavior.strategy == InstrumentStrategy.COMMODITY_FUTURES) {
            1.0
        } else {
            0.0
        }
        val cryptoLoading = if (behavior.cryptoFactorSensitivity != 0.0) {
            behavior.cryptoFactorSensitivity
        } else if (behavior.strategy == InstrumentStrategy.CRYPTO_FUTURES) {
            1.0
        } else {
            0.0
        }
        val alternative = commodityLoading * macro.inflationSurprise * COMMODITY_INFLATION_SCALE +
            cryptoLoading * macro.riskSentiment * CRYPTO_SENTIMENT_SCALE
        return if (behavior.creditSpreadSensitivity > 0.0) credit + ordinary * 0.20
        else ordinary + alternative
    }

    /**
     * 공통 순수급은 MarketDynamicsEngine의 시장 팩터가 이미 소유한다. 여기서는 개인은
     * 고베타, 기관은 상대적으로 저베타 상품에 더 실리는 횡단면 기울기만 더한다.
     */
    private fun instrumentOrderFlowReturn(stock: StockDefinition, macro: MacroEnvironment): Double {
        val betaDistance = (stock.beta - 1.0).coerceIn(-0.8, 1.8)
        val retailTilt = macro.retailOrderFlow * betaDistance * RETAIL_FLOW_TILT_SCALE
        val institutionalTilt = macro.institutionalOrderFlow * -betaDistance *
            INSTITUTIONAL_FLOW_TILT_SCALE
        return retailTilt + institutionalTilt
    }

    private fun dailyResetVolatilityDrag(stock: StockDefinition, hoursPerTradingYear: Double): Double {
        val leverage = stock.etfProfile?.leverage ?: return 0.0
        if (stock.behavior.strategy !in setOf(
                InstrumentStrategy.DAILY_LEVERAGED,
                InstrumentStrategy.DAILY_INVERSE,
            )
        ) {
            return 0.0
        }
        val underlyingAnnualVolatility = stock.volatility / abs(leverage).coerceAtLeast(1.0)
        return -0.5 * abs(leverage * (leverage - 1.0)) *
            underlyingAnnualVolatility * underlyingAnnualVolatility / hoursPerTradingYear
    }

    private fun closedResult(input: PriceGenerationInput, endTime: Instant): PriceGenerationResult {
        val price = input.previousPrice
        val bar = PriceBar(
            stockId = input.stock.id,
            startTime = input.startTime,
            endTime = endTime,
            step = PriceBarInterval.ONE_HOUR,
            open = price,
            high = price,
            low = price,
            close = price,
            volume = 0L,
        )
        return PriceGenerationResult(
            bar = bar,
            quote = Quote(
                stockId = input.stock.id,
                timestamp = endTime,
                price = price,
                previousClose = input.dailyBasePrice,
                open = input.dayOpen,
                high = input.dayHigh,
                low = input.dayLow,
                volume = 0L,
                session = input.session,
            ),
            closeValueKrw = if (input.stock.currency == Currency.USD) {
                price * input.macro.usdKrw
            } else {
                price
            },
            attribution = ZERO_ATTRIBUTION,
            stabilizer = TradingStabilizer.MARKET_CLOSED,
            wasClamped = false,
        )
    }

    private fun haltedUsResult(input: PriceGenerationInput, endTime: Instant): PriceGenerationResult {
        val result = closedResult(input, endTime)
        return result.copy(
            quote = result.quote.copy(session = MarketSession.CLOSED),
            stabilizer = TradingStabilizer.US_LEVEL_3_HALTED,
        )
    }

    private fun generateVolume(
        random: DeterministicRandom,
        averageDailyVolume: Long,
        absoluteReturn: Double,
        multiplier: Double,
    ): Long {
        if (averageDailyVolume == 0L || multiplier == 0.0) return 0L
        val hourlyBaseline = averageDailyVolume.toDouble() / TRADING_HOURS_PER_DAY
        val noise = exp(VOLUME_NOISE * random.nextGaussian())
        val movementBoost = 1.0 + (absoluteReturn / TYPICAL_HOURLY_MOVE).coerceAtMost(12.0)
        return (hourlyBaseline * noise * movementBoost * multiplier)
            .coerceIn(0.0, Long.MAX_VALUE.toDouble() / 4.0)
            .toLong()
    }

    private fun boundedPrice(stock: StockDefinition, rawPrice: Double, basePrice: Double): BoundedPrice {
        val positive = rawPrice.coerceAtLeast(MarketMicrostructure.minimumPrice(stock.market))
        val limits = MarketMicrostructure.dailyPriceLimits(stock, basePrice)
        if (limits == null) {
            val rounded = MarketMicrostructure.roundNearest(stock, positive)
            return BoundedPrice(rounded, rounded != rawPrice)
        }

        val clamped = positive.coerceIn(limits.lower, limits.upper)
        val rounded = MarketMicrostructure.roundNearest(stock, clamped)
            .coerceIn(limits.lower, limits.upper)
        return BoundedPrice(
            price = rounded,
            wasClamped = rounded != rawPrice,
            hitUpperLimit = rawPrice >= limits.upper,
            hitLowerLimit = rawPrice <= limits.lower,
        )
    }

    private fun boundHigh(
        stock: StockDefinition,
        rawPrice: Double,
        basePrice: Double,
        minimum: Double,
    ): BoundedPrice {
        val limits = MarketMicrostructure.dailyPriceLimits(stock, basePrice)
        val limited = if (limits == null) rawPrice else rawPrice.coerceAtMost(limits.upper)
        val rounded = MarketMicrostructure.roundUp(stock, max(limited, minimum))
        val finalPrice = if (limits == null) rounded else rounded.coerceAtMost(limits.upper)
        return BoundedPrice(finalPrice, finalPrice != rawPrice)
    }

    private fun boundLow(
        stock: StockDefinition,
        rawPrice: Double,
        basePrice: Double,
        maximum: Double,
    ): BoundedPrice {
        val limits = MarketMicrostructure.dailyPriceLimits(stock, basePrice)
        val limited = if (limits == null) rawPrice else rawPrice.coerceAtLeast(limits.lower)
        val rounded = MarketMicrostructure.roundDown(stock, min(limited, maximum))
        val finalPrice = if (limits == null) rounded else rounded.coerceAtLeast(limits.lower)
        return BoundedPrice(finalPrice, finalPrice != rawPrice)
    }

    private fun priceDislocationReversionRate(stock: StockDefinition): Double = when (stock.instrumentType) {
        InstrumentType.CLOSED_END_FUND -> CEF_DISLOCATION_REVERSION_PER_HOUR
        InstrumentType.ETN -> ETN_DISLOCATION_REVERSION_PER_HOUR
        else -> ETF_DISLOCATION_REVERSION_PER_HOUR
    }

    private fun rateSensitivity(sector: Sector): Double = when (sector) {
        Sector.REAL_ESTATE, Sector.UTILITIES, Sector.INFORMATION_TECHNOLOGY,
        Sector.INTERNET_PLATFORM, Sector.BATTERY, Sector.ROBOTICS,
        -> 1.4

        Sector.FINANCIALS -> -0.35
        else -> 0.65
    }

    private fun inflationSensitivity(sector: Sector): Double = when (sector) {
        Sector.ENERGY, Sector.MATERIALS_CHEMICALS -> -0.1
        Sector.CONSUMER_DISCRETIONARY, Sector.RETAIL_ECOMMERCE, Sector.REAL_ESTATE -> 1.1
        else -> 0.45
    }

    private fun growthSensitivity(sector: Sector): Double = when (sector) {
        Sector.CONSUMER_DISCRETIONARY, Sector.AUTOMOTIVE, Sector.INDUSTRIALS,
        Sector.SEMICONDUCTOR, Sector.TRANSPORTATION_LOGISTICS,
        -> 1.2

        Sector.CONSUMER_STAPLES, Sector.UTILITIES, Sector.HEALTHCARE_BIO -> 0.35
        else -> 0.75
    }

    companion object {
        private const val TRADING_DAYS_PER_YEAR: Double = 252.0
        private const val TRADING_HOURS_PER_DAY: Double = 6.5
        private const val SECTOR_LOADING: Double = 0.55
        private const val HOURLY_MACRO_SCALE: Double = 0.0015
        private const val RISK_SENTIMENT_HOURLY_SCALE: Double = 0.00018
        private const val CREDIT_GROWTH_SCALE: Double = 0.00045
        private const val CREDIT_SENTIMENT_SCALE: Double = 0.00060
        private const val COMMODITY_INFLATION_SCALE: Double = 0.00075
        private const val CRYPTO_SENTIMENT_SCALE: Double = 0.0028
        private const val MAX_HOURLY_VOLATILITY: Double = 0.18
        private const val MAX_RAW_LOG_RETURN: Double = 1.5
        private val MAX_US_HOURLY_LOG_MOVE: Double = ln(1.35)
        private const val MAX_INTRAHOUR_RANGE: Double = 0.25
        private const val US_REOPENED_VOLATILITY_FACTOR: Double = 0.55
        private const val US_REOPENED_VOLUME_FACTOR: Double = 0.75
        private const val VOLUME_NOISE: Double = 0.38
        private const val TYPICAL_HOURLY_MOVE: Double = 0.008
        private const val ETF_DISLOCATION_REVERSION_PER_HOUR: Double = 0.18
        private const val ETN_DISLOCATION_REVERSION_PER_HOUR: Double = 0.04
        private const val CEF_DISLOCATION_REVERSION_PER_HOUR: Double = 0.015
        private const val RETAIL_FLOW_TILT_SCALE: Double = 0.000045
        private const val INSTITUTIONAL_FLOW_TILT_SCALE: Double = 0.000035
        private const val IDIOSYNCRATIC_VOLATILITY_ELASTICITY: Double = 0.55
        private val ZERO_ATTRIBUTION = PriceAttribution(
            market = 0.0,
            sector = 0.0,
            ratesAndInflation = 0.0,
            growthAndSentiment = 0.0,
            orderFlow = 0.0,
            foreignExchange = 0.0,
            referenceEvent = 0.0,
            directProductEvent = 0.0,
            fundCosts = 0.0,
            carriedReference = 0.0,
            carriedPriceDislocation = 0.0,
            referenceResidual = 0.0,
            priceDislocation = 0.0,
        )

        internal fun stableHash64(value: String): Long {
            var hash = 0xCBF29CE484222325uL
            for (character in value) {
                hash = hash xor character.code.toULong()
                hash *= 0x100000001B3uL
            }
            return hash.toLong()
        }
    }
}
