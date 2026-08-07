package com.amond.kmpbook.domain.simulation

import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.MarketIndexCatalog
import com.amond.kmpbook.domain.model.MarketIndexId
import com.amond.kmpbook.domain.model.MarketIndexSnapshot
import com.amond.kmpbook.domain.model.PriceBar
import com.amond.kmpbook.domain.model.StockDefinition
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.time.Instant

/**
 * [MarketIndexEngine] 한 시간 계산에 필요한 모든 입력.
 *
 * [barsByStockId]는 `StockDefinition.id`를 키로 하는 해당 시간의 봉이다.
 * [previousCloseByStockId]는 같은 구성종목의 직전 종가로, 시가 갭을 지수에 이어
 * 붙이는 기준이다. 구형 저장 등으로 특정 종목의 값이 없을 때만 해당 봉의
 * 시가를 기준으로 사용해 갭이 없는 봉으로 처리한다.
 * [usTradingFraction]은 미국 정규장이 해당 벽시간에 차지하는 비율로, 폐장 0·일반 1을
 * 사용한다. 입력이 동일하면 결과도 항상 동일하다.
 */
data class MarketIndexCalculationInput(
    val timestamp: Instant,
    val stocks: List<StockDefinition>,
    val barsByStockId: Map<String, PriceBar>,
    val previousCloseByStockId: Map<String, Double> = emptyMap(),
    val previousIndices: Map<MarketIndexId, MarketIndexSnapshot> = emptyMap(),
    val macro: MacroEnvironment = MacroEnvironment(),
    val usTradingFraction: Double,
) {
    init {
        require(usTradingFraction in 0.0..1.0) { "미국 정규장 비율은 0에서 1 사이여야 합니다." }
        require(previousIndices.all { (id, snapshot) -> id == snapshot.id }) {
            "이전 지수 맵의 키와 스냅샷 ID가 일치해야 합니다."
        }
        require(previousCloseByStockId.values.all { it > 0.0 && it.isFinite() }) {
            "구성종목의 직전 종가는 유한한 양수여야 합니다."
        }
    }
}

/**
 * 대표 미국 지수를 게임 종목 유니버스로 재구성하는 상태 없는 결정론 엔진.
 *
 * 이 결과는 S&P DJI·Nasdaq·Cboe가 발표하는 실제 상용 지수가 아니다. 전체 공식
 * 구성종목, 유동주식수, 지수 제수, SPX 옵션 호가가 없으므로 공식 방법론의 핵심만
 * 보존한 simulation proxy다. 실행 산식은 [MarketIndexCatalog]에 명시한다.
 */
class MarketIndexEngine {
    fun initialSnapshots(timestamp: Instant): Map<MarketIndexId, MarketIndexSnapshot> =
        MarketIndexId.entries.associateWith { id -> initialSnapshot(id, timestamp) }

    fun calculate(input: MarketIndexCalculationInput): Map<MarketIndexId, MarketIndexSnapshot> {
        if (input.usTradingFraction == 0.0) {
            return MarketIndexId.entries.associateWith { id ->
                (input.previousIndices[id] ?: initialSnapshot(id, input.timestamp))
                    .copy(timestamp = input.timestamp)
            }
        }

        val sp500 = marketCapWeightedInterval(input) { stock -> stock.market.isUnitedStates }
        val nasdaq = marketCapWeightedInterval(input) { stock -> stock.market == Market.NASDAQ }
        val dow = priceWeightedDowInterval(input)
        val effectiveSp500 = sp500.scaled(input.usTradingFraction)
        val effectiveSp500Return = effectiveSp500.closeFactor - 1.0
        val sessionDate = input.timestamp.toLocalDateTime(NEW_YORK_TIME_ZONE).date

        val snapshots = linkedMapOf<MarketIndexId, MarketIndexSnapshot>()
        snapshots[MarketIndexId.SP_500] = equitySnapshot(
            id = MarketIndexId.SP_500,
            input = input,
            interval = effectiveSp500,
            constituentCount = sp500.constituentCount,
            sessionDate = sessionDate,
        )
        snapshots[MarketIndexId.NASDAQ_COMPOSITE] = equitySnapshot(
            id = MarketIndexId.NASDAQ_COMPOSITE,
            input = input,
            interval = nasdaq.scaled(input.usTradingFraction),
            constituentCount = nasdaq.constituentCount,
            sessionDate = sessionDate,
        )
        snapshots[MarketIndexId.DOW_JONES_INDUSTRIAL_AVERAGE] = equitySnapshot(
            id = MarketIndexId.DOW_JONES_INDUSTRIAL_AVERAGE,
            input = input,
            interval = dow.scaled(input.usTradingFraction),
            constituentCount = dow.constituentCount,
            sessionDate = sessionDate,
        )

        val previousVix = previousOrInitial(MarketIndexId.VIX, input)
        val vixValue = estimateVix(
            previousValue = previousVix.value,
            sp500Return = effectiveSp500Return,
            macro = input.macro,
            tradingFraction = input.usTradingFraction,
        )
        snapshots[MarketIndexId.VIX] = snapshotWithDailyOhlc(
            previous = previousVix,
            timestamp = input.timestamp,
            interval = IndexIntervalPoints.fromEndpoints(previousVix.value, vixValue),
            constituentCount = sp500.constituentCount,
            sessionDate = sessionDate,
        )

        check(snapshots.keys == MarketIndexId.entries.toSet()) { "대표 지수 4종이 모두 계산되어야 합니다." }
        return snapshots
    }

    private fun equitySnapshot(
        id: MarketIndexId,
        input: MarketIndexCalculationInput,
        interval: IndexIntervalCalculation,
        constituentCount: Int,
        sessionDate: kotlinx.datetime.LocalDate,
    ): MarketIndexSnapshot {
        val previous = previousOrInitial(id, input)
        val intervalPoints = IndexIntervalPoints(
            open = (previous.value * interval.openFactor).coerceAtLeast(MINIMUM_INDEX_VALUE),
            high = (previous.value * interval.highFactor).coerceAtLeast(MINIMUM_INDEX_VALUE),
            low = (previous.value * interval.lowFactor).coerceAtLeast(MINIMUM_INDEX_VALUE),
            close = (previous.value * interval.closeFactor).coerceAtLeast(MINIMUM_INDEX_VALUE),
        )
        return snapshotWithDailyOhlc(
            previous = previous,
            timestamp = input.timestamp,
            interval = intervalPoints,
            constituentCount = constituentCount,
            sessionDate = sessionDate,
        )
    }

    private fun snapshotWithDailyOhlc(
        previous: MarketIndexSnapshot,
        timestamp: Instant,
        interval: IndexIntervalPoints,
        constituentCount: Int,
        sessionDate: kotlinx.datetime.LocalDate,
    ): MarketIndexSnapshot {
        val isNewSession = previous.sessionDate != sessionDate
        val previousClose = if (isNewSession) previous.value else previous.previousClose
        val open = if (isNewSession) interval.open else previous.open
        val high = if (isNewSession) interval.high else max(previous.high, interval.high)
        val low = if (isNewSession) interval.low else min(previous.low, interval.low)
        return MarketIndexSnapshot(
            id = previous.id,
            timestamp = timestamp,
            value = interval.close,
            previousClose = previousClose,
            open = open,
            high = high,
            low = low,
            constituentCount = constituentCount,
            sessionDate = sessionDate,
        )
    }

    private fun previousOrInitial(
        id: MarketIndexId,
        input: MarketIndexCalculationInput,
    ): MarketIndexSnapshot = input.previousIndices[id] ?: initialSnapshot(id, input.timestamp)

    private fun initialSnapshot(id: MarketIndexId, timestamp: Instant): MarketIndexSnapshot {
        val initialValue = MarketIndexCatalog[id].initialValue
        return MarketIndexSnapshot(
            id = id,
            timestamp = timestamp,
            value = initialValue,
            previousClose = initialValue,
            open = initialValue,
            high = initialValue,
            low = initialValue,
        )
    }

    /**
     * 직전 종가 시점의 시가총액을 가중치로 고정한 뒤 각 endpoint를
     * `Σ(wᵢ×priceᵢ/previousCloseᵢ)/Σwᵢ`로 계산한다. 이로써 시가 갭과
     * 시간 봉의 고가·저가·종가가 모두 같은 시가총액 가중 원리를 따른다.
     */
    private fun marketCapWeightedInterval(
        input: MarketIndexCalculationInput,
        include: (StockDefinition) -> Boolean,
    ): IndexIntervalCalculation {
        var weightedOpenFactor = 0.0
        var weightedHighFactor = 0.0
        var weightedLowFactor = 0.0
        var weightedCloseFactor = 0.0
        var totalWeight = 0.0
        var constituentCount = 0

        input.stocks.forEach { stock ->
            if (!stock.hasCorporateEarnings || !include(stock)) return@forEach
            val bar = input.barsByStockId[stock.id] ?: return@forEach
            val previousClose = input.previousCloseByStockId[stock.id] ?: bar.open
            if (previousClose <= 0.0) return@forEach
            weightedOpenFactor += stock.marketCap * bar.open / previousClose
            weightedHighFactor += stock.marketCap * bar.high / previousClose
            weightedLowFactor += stock.marketCap * bar.low / previousClose
            weightedCloseFactor += stock.marketCap * bar.close / previousClose
            totalWeight += stock.marketCap
            constituentCount += 1
        }

        if (totalWeight == 0.0) return IndexIntervalCalculation.neutral()
        return IndexIntervalCalculation(
            openFactor = weightedOpenFactor / totalWeight,
            highFactor = weightedHighFactor / totalWeight,
            lowFactor = weightedLowFactor / totalWeight,
            closeFactor = weightedCloseFactor / totalWeight,
            constituentCount = constituentCount,
        )
    }

    /** DJIA 30 스냅샷과 게임 개별주의 교집합에 대해 각 endpoint의 Σprice/ΣpreviousClose를 적용한다. */
    private fun priceWeightedDowInterval(input: MarketIndexCalculationInput): IndexIntervalCalculation {
        var previousCloseSum = 0.0
        var openPriceSum = 0.0
        var highPriceSum = 0.0
        var lowPriceSum = 0.0
        var closePriceSum = 0.0
        var constituentCount = 0

        input.stocks.forEach { stock ->
            if (!stock.hasCorporateEarnings || !stock.market.isUnitedStates || stock.symbol.uppercase() !in DJIA_30_SYMBOLS) {
                return@forEach
            }
            val bar = input.barsByStockId[stock.id] ?: return@forEach
            val previousClose = input.previousCloseByStockId[stock.id] ?: bar.open
            if (previousClose <= 0.0) return@forEach
            previousCloseSum += previousClose
            openPriceSum += bar.open
            highPriceSum += bar.high
            lowPriceSum += bar.low
            closePriceSum += bar.close
            constituentCount += 1
        }

        if (previousCloseSum == 0.0) return IndexIntervalCalculation.neutral()
        return IndexIntervalCalculation(
            openFactor = openPriceSum / previousCloseSum,
            highFactor = highPriceSum / previousCloseSum,
            lowFactor = lowPriceSum / previousCloseSum,
            closeFactor = closePriceSum / previousCloseSum,
            constituentCount = constituentCount,
        )
    }

    /**
     * Cboe VIX의 “SPX 옵션이 내포한 향후 30일 연환산 기대변동성”이라는 의미를 보존한
     * 게임 추정치다. 옵션 호가를 재현하지 않고, 변동성 국면과 SPX 프록시의 당일 충격을
     * 30일 기대 목표로 반영한다. 하락 충격에 더 큰 스크류 프리미엄을 주고 매시간 긴 평균으로
     * 회귀시켜 항상 양수를 유지한다.
     */
    private fun estimateVix(
        previousValue: Double,
        sp500Return: Double,
        macro: MacroEnvironment,
        tradingFraction: Double,
    ): Double {
        val regimeTarget = LONG_RUN_VIX * macro.volatilityRegime
        val annualizedObservedMove = abs(sp500Return) * sqrt(TRADING_DAYS_PER_YEAR) * 100.0
        val downsideSkewPremium = max(-sp500Return, 0.0) * 100.0 * DOWNSIDE_SKEW_MULTIPLIER
        val riskAversionPremium = max(-macro.riskSentiment, 0.0) * MAX_RISK_SENTIMENT_PREMIUM
        val expectedThirtyDayVolatility = (
            regimeTarget +
                annualizedObservedMove * OBSERVED_MOVE_WEIGHT +
                downsideSkewPremium +
                riskAversionPremium
            ).coerceIn(MINIMUM_VIX, MAXIMUM_VIX)
        val meanReversion = VIX_MEAN_REVERSION_PER_HOUR * tradingFraction
        return (previousValue + meanReversion * (expectedThirtyDayVolatility - previousValue))
            .coerceIn(MINIMUM_VIX, MAXIMUM_VIX)
    }

    private data class IndexIntervalCalculation(
        val openFactor: Double,
        val highFactor: Double,
        val lowFactor: Double,
        val closeFactor: Double,
        val constituentCount: Int,
    ) {
        init {
            require(openFactor > 0.0 && highFactor > 0.0 && lowFactor > 0.0 && closeFactor > 0.0)
            require(listOf(openFactor, highFactor, lowFactor, closeFactor).all(Double::isFinite))
            require(highFactor >= maxOf(openFactor, closeFactor, lowFactor))
            require(lowFactor <= minOf(openFactor, closeFactor, highFactor))
        }

        fun scaled(fraction: Double): IndexIntervalCalculation = copy(
            openFactor = scaleFactor(openFactor, fraction),
            highFactor = scaleFactor(highFactor, fraction),
            lowFactor = scaleFactor(lowFactor, fraction),
            closeFactor = scaleFactor(closeFactor, fraction),
        )

        companion object {
            fun neutral(): IndexIntervalCalculation = IndexIntervalCalculation(
                openFactor = 1.0,
                highFactor = 1.0,
                lowFactor = 1.0,
                closeFactor = 1.0,
                constituentCount = 0,
            )

            private fun scaleFactor(factor: Double, fraction: Double): Double =
                (1.0 + (factor - 1.0) * fraction).coerceIn(
                    1.0 + MINIMUM_HOURLY_RETURN,
                    1.0 + MAXIMUM_HOURLY_RETURN,
                )
        }
    }

    private data class IndexIntervalPoints(
        val open: Double,
        val high: Double,
        val low: Double,
        val close: Double,
    ) {
        init {
            require(listOf(open, high, low, close).all { it > 0.0 && it.isFinite() })
            require(high >= maxOf(open, close, low))
            require(low <= minOf(open, close, high))
        }

        companion object {
            fun fromEndpoints(open: Double, close: Double): IndexIntervalPoints = IndexIntervalPoints(
                open = open,
                high = max(open, close),
                low = min(open, close),
                close = close,
            )
        }
    }

    companion object {
        /**
         * 2026-08-07 스냅샷. Alphabet(GOOGL)이 2026-06-29 Verizon(VZ)를 대체한 변경을 포함한다.
         * 실제 지수는 변경 시 제수를 조정하지만, 게임은 해당 시점 유니버스와의 교집합 수익률로
         * 이어 붙인다.
         */
        val DJIA_30_SYMBOLS: Set<String> = linkedSetOf(
            "MMM", "AXP", "AMGN", "AMZN", "AAPL", "BA", "CAT", "CVX", "CSCO", "KO",
            "DIS", "GS", "HD", "HON", "IBM", "JNJ", "JPM", "MCD", "MRK", "MSFT",
            "NKE", "NVDA", "PG", "CRM", "SHW", "TRV", "UNH", "V", "WMT", "GOOGL",
        )

        private val NEW_YORK_TIME_ZONE: TimeZone = TimeZone.of("America/New_York")
        private const val LONG_RUN_VIX: Double = 18.0
        private const val MINIMUM_VIX: Double = 5.0
        private const val MAXIMUM_VIX: Double = 150.0
        private const val VIX_MEAN_REVERSION_PER_HOUR: Double = 0.18
        private const val OBSERVED_MOVE_WEIGHT: Double = 0.12
        private const val DOWNSIDE_SKEW_MULTIPLIER: Double = 3.0
        private const val MAX_RISK_SENTIMENT_PREMIUM: Double = 10.0
        private const val TRADING_DAYS_PER_YEAR: Double = 252.0
        private const val MINIMUM_INDEX_VALUE: Double = 0.01
        private const val MINIMUM_HOURLY_RETURN: Double = -0.99
        private const val MAXIMUM_HOURLY_RETURN: Double = 10.0
    }
}
