package com.amond.kmpbook.domain.simulation

import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.MarketSession
import com.amond.kmpbook.domain.model.PriceBar
import com.amond.kmpbook.domain.model.Quote
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
    val riskSentiment: Double = 0.0,
    val volatilityRegime: Double = 1.0,
    val marketHourlyReturns: Map<Market, Double> = emptyMap(),
    val sectorHourlyReturns: Map<Sector, Double> = emptyMap(),
    val marketChangeFromPreviousClose: Map<Market, Double> = emptyMap(),
) {
    init {
        require(policyRate.isFinite() && inflationRate.isFinite() && growthRate.isFinite())
        require(policyRateChange.isFinite() && inflationSurprise.isFinite() && growthSurprise.isFinite())
        require(usdKrw > 0.0 && usdKrw.isFinite()) { "USD/KRW must be positive and finite" }
        require(previousUsdKrw > 0.0 && previousUsdKrw.isFinite()) {
            "Previous USD/KRW must be positive and finite"
        }
        require(riskSentiment in -1.0..1.0) { "Risk sentiment must be in [-1, 1]" }
        require(volatilityRegime in 0.1..10.0) { "Volatility regime must be in [0.1, 10]" }
        require(marketHourlyReturns.values.all { it.isFinite() })
        require(sectorHourlyReturns.values.all { it.isFinite() })
        require(marketChangeFromPreviousClose.values.all { it.isFinite() })
    }
}

/** Price, volatility, and liquidity effect applied during this one-hour step. */
data class PriceImpulse(
    val returnRate: Double = 0.0,
    val volatilityMultiplier: Double = 1.0,
    val volumeMultiplier: Double = 1.0,
) {
    init {
        require(returnRate > -1.0 && returnRate.isFinite()) {
            "Impulse return must be finite and greater than -100%"
        }
        require(volatilityMultiplier in 0.0..20.0) {
            "Volatility multiplier must be in [0, 20]"
        }
        require(volumeMultiplier in 0.0..100.0) { "Volume multiplier must be in [0, 100]" }
    }
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
    }

    companion object {
        fun defaultAverageDailyVolume(stock: StockDefinition): Long =
            (stock.sharesOutstanding.toDouble() * DEFAULT_DAILY_TURNOVER)
                .coerceIn(1_000.0, Long.MAX_VALUE.toDouble() / 4.0)
                .toLong()

        fun defaultFxSensitivity(stock: StockDefinition): Double = when {
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
    val idiosyncratic: Double,
) {
    val totalBeforeStabilization: Double
        get() = market + sector + ratesAndInflation + growthAndSentiment +
            foreignExchange + event + idiosyncratic
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
        if (!input.session.isTradable || input.regularTradingFraction == 0.0) {
            return closedResult(input, endTime)
        }

        val stock = input.stock
        val marketDrawdown = input.macro.marketChangeFromPreviousClose[stock.market] ?: 0.0
        val circuitLevel = usCircuitLevel(stock.market, marketDrawdown)
        if (circuitLevel == 3) return haltedUsResult(input, endTime)

        val random = DeterministicRandom(
            DeterministicRandom.mixSeed(seed, stableHash64(stock.id), input.startTime.epochSeconds),
        )

        val hoursPerTradingYear = TRADING_DAYS_PER_YEAR * TRADING_HOURS_PER_DAY
        val circuitVolatilityFactor = when (circuitLevel) {
            1, 2 -> US_REOPENED_VOLATILITY_FACTOR
            else -> 1.0
        }
        val hourlyVolatility = (
            stock.volatility / sqrt(hoursPerTradingYear) *
                input.macro.volatilityRegime *
                input.eventImpulse.volatilityMultiplier *
                circuitVolatilityFactor *
                sqrt(input.regularTradingFraction)
            ).coerceIn(0.0, MAX_HOURLY_VOLATILITY)

        val marketComponent = stock.beta *
            (input.macro.marketHourlyReturns[stock.market] ?: 0.0) * input.regularTradingFraction
        val sectorComponent = SECTOR_LOADING *
            (input.macro.sectorHourlyReturns[stock.sector] ?: 0.0) * input.regularTradingFraction
        val ratesAndInflation =
            -rateSensitivity(stock.sector) * input.macro.policyRateChange -
                inflationSensitivity(stock.sector) * input.macro.inflationSurprise * HOURLY_MACRO_SCALE
        val growthAndSentiment =
            growthSensitivity(stock.sector) * input.macro.growthSurprise * HOURLY_MACRO_SCALE +
                input.macro.riskSentiment * RISK_SENTIMENT_HOURLY_SCALE
        val fxReturn = ln(input.macro.usdKrw / input.macro.previousUsdKrw) *
            input.fxSensitivity * input.regularTradingFraction
        val randomComponent = -0.5 * hourlyVolatility * hourlyVolatility +
            hourlyVolatility * random.nextGaussian()

        val attribution = PriceAttribution(
            market = marketComponent,
            sector = sectorComponent,
            ratesAndInflation = ratesAndInflation * input.regularTradingFraction,
            growthAndSentiment = growthAndSentiment * input.regularTradingFraction,
            foreignExchange = fxReturn,
            event = ln(1.0 + input.eventImpulse.returnRate) * input.regularTradingFraction,
            idiosyncratic = randomComponent,
        )

        val proposedLogReturn = attribution.totalBeforeStabilization.coerceIn(
            -MAX_RAW_LOG_RETURN,
            MAX_RAW_LOG_RETURN,
        )
        val stabilizedLogReturn = if (stock.market.isUnitedStates) {
            proposedLogReturn.coerceIn(-MAX_US_HOURLY_LOG_MOVE, MAX_US_HOURLY_LOG_MOVE)
        } else {
            proposedLogReturn
        }
        val volatilityPause = stock.market.isUnitedStates && stabilizedLogReturn != proposedLogReturn

        val rawClose = input.previousPrice * exp(stabilizedLogReturn)
        val boundedClose = boundedPrice(stock.market, rawClose, input.dailyBasePrice)
        val open = boundedPrice(stock.market, input.previousPrice, input.dailyBasePrice)
        val close = boundedClose.price

        val rangeScale = (hourlyVolatility + abs(stabilizedLogReturn) * 0.35)
            .coerceAtMost(MAX_INTRAHOUR_RANGE)
        val rawHigh = max(open.price, close) * exp(abs(random.nextGaussian()) * rangeScale * 0.55)
        val rawLow = min(open.price, close) * exp(-abs(random.nextGaussian()) * rangeScale * 0.55)
        val high = boundHigh(stock.market, rawHigh, input.dailyBasePrice, max(open.price, close))
        val low = boundLow(stock.market, rawLow, input.dailyBasePrice, min(open.price, close))

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
            boundedClose.hitUpperLimit -> TradingStabilizer.KRX_UPPER_LIMIT
            boundedClose.hitLowerLimit -> TradingStabilizer.KRX_LOWER_LIMIT
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
            open = input.dayOpen,
            high = max(input.dayHigh, high.price),
            low = min(input.dayLow, low.price),
            volume = volume,
            session = input.session,
        )
        return PriceGenerationResult(
            bar = bar,
            quote = quote,
            closeValueKrw = if (stock.currency == Currency.USD) close * input.macro.usdKrw else close,
            attribution = attribution,
            stabilizer = stabilizer,
            wasClamped = boundedClose.wasClamped || open.wasClamped ||
                high.wasClamped || low.wasClamped || volatilityPause,
        )
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

    private fun boundedPrice(market: Market, rawPrice: Double, basePrice: Double): BoundedPrice {
        val positive = rawPrice.coerceAtLeast(MarketMicrostructure.minimumPrice(market))
        val limits = MarketMicrostructure.dailyPriceLimits(market, basePrice)
        if (limits == null) {
            val rounded = MarketMicrostructure.roundNearest(market, positive)
            return BoundedPrice(rounded, rounded != rawPrice)
        }

        val clamped = positive.coerceIn(limits.lower, limits.upper)
        val rounded = MarketMicrostructure.roundNearest(market, clamped)
            .coerceIn(limits.lower, limits.upper)
        return BoundedPrice(
            price = rounded,
            wasClamped = rounded != rawPrice,
            hitUpperLimit = rawPrice >= limits.upper,
            hitLowerLimit = rawPrice <= limits.lower,
        )
    }

    private fun boundHigh(
        market: Market,
        rawPrice: Double,
        basePrice: Double,
        minimum: Double,
    ): BoundedPrice {
        val limits = MarketMicrostructure.dailyPriceLimits(market, basePrice)
        val limited = if (limits == null) rawPrice else rawPrice.coerceAtMost(limits.upper)
        val rounded = MarketMicrostructure.roundUp(market, max(limited, minimum))
        val finalPrice = if (limits == null) rounded else rounded.coerceAtMost(limits.upper)
        return BoundedPrice(finalPrice, finalPrice != rawPrice)
    }

    private fun boundLow(
        market: Market,
        rawPrice: Double,
        basePrice: Double,
        maximum: Double,
    ): BoundedPrice {
        val limits = MarketMicrostructure.dailyPriceLimits(market, basePrice)
        val limited = if (limits == null) rawPrice else rawPrice.coerceAtLeast(limits.lower)
        val rounded = MarketMicrostructure.roundDown(market, min(limited, maximum))
        val finalPrice = if (limits == null) rounded else rounded.coerceAtLeast(limits.lower)
        return BoundedPrice(finalPrice, finalPrice != rawPrice)
    }

    private fun usCircuitLevel(market: Market, marketDrawdown: Double): Int {
        if (!market.isUnitedStates) return 0
        return when {
            marketDrawdown <= -0.20 -> 3
            marketDrawdown <= -0.13 -> 2
            marketDrawdown <= -0.07 -> 1
            else -> 0
        }
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
        private const val MAX_HOURLY_VOLATILITY: Double = 0.18
        private const val MAX_RAW_LOG_RETURN: Double = 1.5
        private val MAX_US_HOURLY_LOG_MOVE: Double = ln(1.35)
        private const val MAX_INTRAHOUR_RANGE: Double = 0.25
        private const val US_REOPENED_VOLATILITY_FACTOR: Double = 0.55
        private const val US_REOPENED_VOLUME_FACTOR: Double = 0.75
        private const val VOLUME_NOISE: Double = 0.38
        private const val TYPICAL_HOURLY_MOVE: Double = 0.008
        private val ZERO_ATTRIBUTION = PriceAttribution(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)

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
