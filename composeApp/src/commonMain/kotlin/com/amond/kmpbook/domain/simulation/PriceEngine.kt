package com.amond.kmpbook.domain.simulation

import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.EtfExposureRegion
import com.amond.kmpbook.domain.model.EtfFxProfile
import com.amond.kmpbook.domain.model.InstrumentStrategy
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.MarketSession
import com.amond.kmpbook.domain.model.PriceBar
import com.amond.kmpbook.domain.model.Quote
import com.amond.kmpbook.domain.model.ReferenceCurrency
import com.amond.kmpbook.domain.model.Sector
import com.amond.kmpbook.domain.model.StockDefinition
import com.amond.kmpbook.domain.model.TurnStep
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** Macroeconomic state and already-resolved hourly factor returns. */
data class MacroEnvironment(
    val policyRate: Double = 0.03,
    val policyRateChange: Double = 0.0,
    val inflationRate: Double = 0.02,
    val inflationSurprise: Double = 0.0,
    val growthRate: Double = 0.02,
    val growthSurprise: Double = 0.0,
    val usdKrw: Double = 1_350.0,
    val previousUsdKrw: Double = usdKrw,
    /** 다중통화 ETF NAV용 원화 환율. 생략한 순수 엔진 입력은 USD 필드로 fallback한다. */
    val fxRatesToKrw: Map<ReferenceCurrency, Double>? = null,
    val previousFxRatesToKrw: Map<ReferenceCurrency, Double>? = null,
    val riskSentiment: Double = 0.0,
    val volatilityRegime: Double = 1.0,
    val marketHourlyReturns: Map<Market, Double> = emptyMap(),
    val sectorHourlyReturns: Map<Sector, Double> = emptyMap(),
    val regionalEtfHourlyReturns: Map<EtfExposureRegion, Double>? = null,
    val marketChangeFromPreviousClose: Map<Market, Double> = emptyMap(),
    /** S&P 500 프록시 기준으로 런타임이 이번 시간에 발동한 미국 공통 MWCB 레벨. */
    val usCircuitBreakerLevel: Int = 0,
) {
    init {
        require(policyRate.isFinite() && inflationRate.isFinite() && growthRate.isFinite())
        require(policyRateChange.isFinite() && inflationSurprise.isFinite() && growthSurprise.isFinite())
        require(usdKrw > 0.0 && usdKrw.isFinite()) { "USD/KRW must be positive and finite" }
        require(previousUsdKrw > 0.0 && previousUsdKrw.isFinite()) {
            "Previous USD/KRW must be positive and finite"
        }
        require(fxRatesToKrw.orEmpty().values.all { it > 0.0 && it.isFinite() })
        require(previousFxRatesToKrw.orEmpty().values.all { it > 0.0 && it.isFinite() })
        require(riskSentiment in -1.0..1.0) { "Risk sentiment must be in [-1, 1]" }
        require(volatilityRegime in 0.1..10.0) { "Volatility regime must be in [0.1, 10]" }
        require(marketHourlyReturns.values.all { it.isFinite() })
        require(sectorHourlyReturns.values.all { it.isFinite() })
        require(regionalEtfHourlyReturns.orEmpty().values.all { it.isFinite() })
        require(marketChangeFromPreviousClose.values.all { it.isFinite() })
        require(usCircuitBreakerLevel in 0..3)
    }

    fun rateToKrw(currency: ReferenceCurrency, previous: Boolean = false): Double {
        if (currency == ReferenceCurrency.KRW) return 1.0
        val rates = if (previous) previousFxRatesToKrw else fxRatesToKrw
        return rates?.get(currency) ?: if (currency == ReferenceCurrency.USD) {
            if (previous) previousUsdKrw else usdKrw
        } else {
            // A standalone engine input may omit a non-USD basket. Hold the missing cross-rate
            // flat instead of inventing currency P&L.
            1.0
        }
    }
}

/** Price, volatility, and liquidity effect applied during this one-hour step. */
data class PriceImpulse(
    val returnRate: Double = 0.0,
    /** 종목 자체의 운용·발행사 사건. 인버스·레버리지 배율을 다시 곱하지 않는다. */
    val directProductReturnRate: Double = 0.0,
    val volatilityMultiplier: Double = 1.0,
    val volumeMultiplier: Double = 1.0,
) {
    init {
        require(returnRate > -1.0 && returnRate.isFinite()) {
            "Impulse return must be finite and greater than -100%"
        }
        require(directProductReturnRate > -1.0 && directProductReturnRate.isFinite()) {
            "Direct product impulse must be finite and greater than -100%"
        }
        require(volatilityMultiplier in 0.0..20.0) {
            "Volatility multiplier must be in [0, 20]"
        }
        require(volumeMultiplier in 0.0..100.0) { "Volume multiplier must be in [0, 100]" }
    }

    val referenceReturnRate: Double
        get() = ((1.0 + returnRate) / (1.0 + directProductReturnRate) - 1.0).coerceAtLeast(-0.95)
}

data class PriceGenerationInput(
    val stock: StockDefinition,
    val startTime: Instant,
    val previousPrice: Double,
    val dailyBasePrice: Double,
    val session: MarketSession,
    val macro: MacroEnvironment = MacroEnvironment(),
    val eventImpulse: PriceImpulse = PriceImpulse(),
    val averageDailyVolume: Long = defaultAverageDailyVolume(stock),
    val dayOpen: Double = previousPrice,
    val dayHigh: Double = max(previousPrice, dayOpen),
    val dayLow: Double = min(previousPrice, dayOpen),
    /** Optional stock-specific sensitivity to a percentage move in USD/KRW. */
    val fxSensitivity: Double = defaultFxSensitivity(stock),
    /**
     * Portion of this wall-clock hour covered by the regular session. Use 0.5
     * for KRX's 15:00-15:30 final half-hour and 1.0 for ordinary full hours.
     */
    val regularTradingFraction: Double = 1.0,
    /**
     * Fair-value clock visible to this bar. Normally the same as the regular fraction;
     * after an intrahour L1/L2 reopening it keeps running for the ordinary session even
     * though volume and idiosyncratic volatility use the reduced trading fraction.
     */
    val fairValueTradingFraction: Double? = null,
    /** 기초자산 시장이 이 시간에 실제로 거래된 비율. 해외 ETF의 개장 갭에 사용한다. */
    val referenceTradingFraction: Double? = null,
    /** 상장시장 폐장 중 누적된 기초자산·환율 fair-value 로그수익률. 개장 시 한 번 적용한다. */
    val carriedReferenceLogReturn: Double = 0.0,
    /** 이번 봉이 현지 거래일의 첫 정규장 봉인지를 런타임이 지정한다. */
    val isFirstRegularBarOfDay: Boolean = false,
) {
    init {
        require(previousPrice > 0.0 && previousPrice.isFinite()) {
            "Previous price must be positive and finite"
        }
        require(dailyBasePrice > 0.0 && dailyBasePrice.isFinite()) {
            "Daily base price must be positive and finite"
        }
        require(averageDailyVolume >= 0L) { "Average daily volume cannot be negative" }
        require(dayOpen > 0.0 && dayHigh > 0.0 && dayLow > 0.0)
        require(dayHigh >= max(previousPrice, dayOpen))
        require(dayLow <= min(previousPrice, dayOpen))
        require(fxSensitivity.isFinite())
        require(regularTradingFraction in 0.0..1.0) {
            "Regular trading fraction must be in [0, 1]"
        }
        require(fairValueTradingFraction == null || fairValueTradingFraction in 0.0..1.0) {
            "Fair-value trading fraction must be in [0, 1]"
        }
        require(referenceTradingFraction == null || referenceTradingFraction in 0.0..1.0) {
            "Reference trading fraction must be in [0, 1]"
        }
        require(carriedReferenceLogReturn.isFinite())
    }

    companion object {
        fun defaultAverageDailyVolume(stock: StockDefinition): Long =
            (stock.sharesOutstanding.toDouble() * DEFAULT_DAILY_TURNOVER)
                .coerceIn(1_000.0, Long.MAX_VALUE.toDouble() / 4.0)
                .toLong()

        fun defaultFxSensitivity(stock: StockDefinition): Double = when {
            stock.etfProfile != null -> 0.0
            stock.market.isUnitedStates -> -0.10
            stock.sector in EXPORT_HEAVY_SECTORS -> 0.25
            stock.sector in IMPORT_HEAVY_SECTORS -> -0.15
            else -> 0.05
        }

        private val EXPORT_HEAVY_SECTORS = setOf(
            Sector.SEMICONDUCTOR,
            Sector.AUTOMOTIVE,
            Sector.AEROSPACE_DEFENSE,
            Sector.INFORMATION_TECHNOLOGY,
            Sector.ENTERTAINMENT,
            Sector.INDUSTRIALS,
        )
        private val IMPORT_HEAVY_SECTORS = setOf(
            Sector.ENERGY,
            Sector.UTILITIES,
            Sector.TRANSPORTATION_LOGISTICS,
            Sector.RETAIL_ECOMMERCE,
        )
        private const val DEFAULT_DAILY_TURNOVER: Double = 0.004
    }
}

enum class TradingStabilizer {
    NONE,
    MARKET_CLOSED,
    KRX_UPPER_LIMIT,
    KRX_LOWER_LIMIT,
    US_LEVEL_1_REOPENED,
    US_LEVEL_2_REOPENED,
    US_LEVEL_3_HALTED,
    US_VOLATILITY_PAUSE,
}

data class PriceAttribution(
    val market: Double,
    val sector: Double,
    val ratesAndInflation: Double,
    val growthAndSentiment: Double,
    val foreignExchange: Double,
    val event: Double,
    val fundCosts: Double,
    val carriedReference: Double,
    val idiosyncratic: Double,
) {
    val totalBeforeStabilization: Double
        get() = market + sector + ratesAndInflation + growthAndSentiment +
            foreignExchange + event + fundCosts + carriedReference + idiosyncratic
}

data class PriceGenerationResult(
    val bar: PriceBar,
    val quote: Quote,
    val closeValueKrw: Double,
    val attribution: PriceAttribution,
    val stabilizer: TradingStabilizer,
    val wasClamped: Boolean,
)

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
        if (circuitLevel == 3) return haltedUsResult(input, endTime)
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
        val combinedAnnualVolatility = sqrt(
            stock.volatility * stock.volatility +
                behavior.priceDislocationVolatility * behavior.priceDislocationVolatility,
        )
        val hourlyVolatility = (
            combinedAnnualVolatility / sqrt(hoursPerTradingYear) *
                input.macro.volatilityRegime *
                input.eventImpulse.volatilityMultiplier *
                circuitVolatilityFactor *
                sqrt(input.regularTradingFraction)
            ).coerceIn(0.0, MAX_HOURLY_VOLATILITY)

        val fundLeverage = stock.etfProfile?.leverage ?: 1.0
        val factor = factorReturn(stock, input.macro)
        val marketComponent = fundLeverage * stock.beta *
            strategyParticipation(behavior, factor) * factor * referenceFraction
        val sectorFactor = input.macro.sectorHourlyReturns[stock.sector] ?: 0.0
        val sectorComponent = fundLeverage * SECTOR_LOADING *
            strategyParticipation(behavior, sectorFactor) * sectorFactor * referenceFraction
        val ratesAndInflation = instrumentRateReturn(stock, input.macro)
        val growthAndSentiment = instrumentGrowthAndCreditReturn(stock, input.macro)
        val fxReturn = foreignExchangeReturn(stock, input.macro, input.fxSensitivity) *
            fairValueFraction
        val resetVolatilityDrag = dailyResetVolatilityDrag(stock, hoursPerTradingYear)
        // 분배 재원은 NAV에 먼저 적립된 뒤 배당락일에 빠진다. 커버드콜·ETN의
        // coverage<1 부분은 이 적립에서 제외되므로 반복 분배가 원금을 잠식할 수 있다.
        val earnedDistributionCarry = stock.dividendYield * behavior.distributionCoverageRatio /
            hoursPerTradingYear * fairValueFraction
        val annualFundCosts = stock.etfProfile?.let { profile ->
            profile.annualExpenseRatio + profile.fxProfile.annualHedgeCostRate
        } ?: 0.0
        val fundCosts = earnedDistributionCarry - (
            annualFundCosts + behavior.annualStructuralDrag
            ) /
            hoursPerTradingYear * fairValueFraction + resetVolatilityDrag * fairValueFraction
        val randomComponent = -0.5 * hourlyVolatility * hourlyVolatility +
            hourlyVolatility * random.nextGaussian()

        val attribution = PriceAttribution(
            market = marketComponent,
            sector = sectorComponent,
            ratesAndInflation = fundLeverage * ratesAndInflation * referenceFraction,
            growthAndSentiment = fundLeverage * growthAndSentiment * referenceFraction,
            // Currency hedging is an independent overlay. Multiplying it by an inverse or
            // leveraged equity mandate would incorrectly reverse/double the FX leg.
            foreignExchange = fxReturn,
            event = eventLogReturn(
                stock = stock,
                eventImpulse = input.eventImpulse,
                // News reaches the listing while its underlying reference venue may be closed.
                referenceFraction = fairValueFraction,
                fairValueFraction = fairValueFraction,
            ),
            fundCosts = fundCosts,
            carriedReference = input.carriedReferenceLogReturn,
            idiosyncratic = randomComponent,
        )

        // A return accumulated while the listing was closed is an opening auction gap,
        // not intrahour drift. Stabilize the live-session return independently so a
        // queued market order observes the same opening price as the OHLC bar.
        val boundedCarry = input.carriedReferenceLogReturn.coerceIn(
            -MAX_RAW_LOG_RETURN,
            MAX_RAW_LOG_RETURN,
        )
        val rawOpen = input.previousPrice * exp(boundedCarry)
        val open = boundedPrice(stock, rawOpen, input.dailyBasePrice)
        val activeLogReturn = (
            attribution.market + attribution.sector + attribution.ratesAndInflation +
                attribution.growthAndSentiment + attribution.foreignExchange +
                attribution.event + attribution.fundCosts + attribution.idiosyncratic
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
                input.regularTradingFraction,
        )

        val bar = PriceBar(
            stockId = stock.id,
            startTime = input.startTime,
            endTime = endTime,
            step = TurnStep.ONE_HOUR,
            open = open.price,
            high = high.price,
            low = low.price,
            close = close,
            volume = volume,
        )
        val stabilizer = when {
            open.hitUpperLimit || boundedClose.hitUpperLimit -> TradingStabilizer.KRX_UPPER_LIMIT
            open.hitLowerLimit || boundedClose.hitLowerLimit -> TradingStabilizer.KRX_LOWER_LIMIT
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
            session = input.session,
        )
        return PriceGenerationResult(
            bar = bar,
            quote = quote,
            closeValueKrw = if (stock.currency == Currency.USD) close * input.macro.usdKrw else close,
            attribution = attribution,
            stabilizer = stabilizer,
            wasClamped = boundedCarry != input.carriedReferenceLogReturn ||
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
    ): Double {
        require(referenceTradingFraction in 0.0..1.0)
        require(fxTradingFraction in 0.0..1.0)
        val profile = stock.etfProfile ?: return 0.0
        val leverage = profile.leverage
        val behavior = stock.behavior
        val factor = factorReturn(stock, macro)
        val market = leverage * stock.beta * strategyParticipation(behavior, factor) *
            factor * referenceTradingFraction
        val sectorFactor = macro.sectorHourlyReturns[stock.sector] ?: 0.0
        val sector = leverage * SECTOR_LOADING *
            strategyParticipation(behavior, sectorFactor) * sectorFactor * referenceTradingFraction
        val ratesAndInflation = leverage * instrumentRateReturn(stock, macro) * referenceTradingFraction
        val growthAndSentiment = leverage * instrumentGrowthAndCreditReturn(stock, macro) *
            referenceTradingFraction
        val fx = structuredFxReturn(stock, macro, profile.fxProfile) * fxTradingFraction
        val event = eventLogReturn(stock, eventImpulse, referenceTradingFraction, fxTradingFraction)
        // Expense and hedge-cost accrual belongs to the listing's regular-session NAV
        // path. Including it here would charge a foreign-market ETF once while its
        // reference trades and again while the listing trades.
        return market + sector + ratesAndInflation + growthAndSentiment + fx + event
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
        val leverage = stock.etfProfile?.leverage ?: 1.0
        val behavior = stock.behavior
        return leverage * strategyParticipation(behavior, eventImpulse.referenceReturnRate) *
            ln(1.0 + eventImpulse.referenceReturnRate) * referenceFraction +
            ln(1.0 + eventImpulse.directProductReturnRate) * fairValueFraction
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
        behavior: com.amond.kmpbook.domain.model.InstrumentBehaviorProfile,
        factor: Double,
    ): Double = if (factor >= 0.0) behavior.upsideParticipation else behavior.downsideParticipation

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
            step = TurnStep.ONE_HOUR,
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
        return result.copy(stabilizer = TradingStabilizer.US_LEVEL_3_HALTED)
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

    private data class BoundedPrice(
        val price: Double,
        val wasClamped: Boolean,
        val hitUpperLimit: Boolean = false,
        val hitLowerLimit: Boolean = false,
    )

    companion object {
        private const val TRADING_DAYS_PER_YEAR: Double = 252.0
        private const val TRADING_HOURS_PER_DAY: Double = 6.5
        private const val SECTOR_LOADING: Double = 0.55
        private const val HOURLY_MACRO_SCALE: Double = 0.0015
        private const val RISK_SENTIMENT_HOURLY_SCALE: Double = 0.0012
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
        private val ZERO_ATTRIBUTION = PriceAttribution(
            0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
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
