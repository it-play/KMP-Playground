package com.amond.kmpbook.domain.simulation.market

import com.amond.kmpbook.domain.model.index.MarketIndexCatalog
import com.amond.kmpbook.domain.model.index.MarketIndexId
import com.amond.kmpbook.domain.model.index.MarketIndexSnapshot
import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.market.Market
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

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
     * 직전 종가 시점의 시가총액을 가중치로 고정해 동시 관측 가능한 시가·종가 지수점을
     * 계산한다. 종목별 고가·저가는 각기 다른 시각의 값이므로 합산하지 않고, 두 지수점의
     * 범위를 시간봉 고가·저가로 사용해 존재하지 않았던 시장 전체 극값을 만들지 않는다.
     */
    private fun marketCapWeightedInterval(
        input: MarketIndexCalculationInput,
        include: (StockDefinition) -> Boolean,
    ): IndexIntervalCalculation {
        var weightedOpenFactor = 0.0
        var weightedCloseFactor = 0.0
        var totalWeight = 0.0
        var constituentCount = 0

        input.stocks.forEach { stock ->
            if (!stock.hasCorporateEarnings || !include(stock)) return@forEach
            val bar = input.barsByStockId[stock.id] ?: return@forEach
            val previousClose = input.previousCloseByStockId[stock.id] ?: bar.open
            if (previousClose <= 0.0) return@forEach
            weightedOpenFactor += stock.marketCap * bar.open / previousClose
            weightedCloseFactor += stock.marketCap * bar.close / previousClose
            totalWeight += stock.marketCap
            constituentCount += 1
        }

        if (totalWeight == 0.0) return IndexIntervalCalculation.neutral()
        val openFactor = weightedOpenFactor / totalWeight
        val closeFactor = weightedCloseFactor / totalWeight
        return IndexIntervalCalculation(
            openFactor = openFactor,
            highFactor = max(openFactor, closeFactor),
            lowFactor = min(openFactor, closeFactor),
            closeFactor = closeFactor,
            constituentCount = constituentCount,
        )
    }

    /** DJIA 30 교집합의 동시 관측 가능한 시가·종가에 Σprice/ΣpreviousClose를 적용한다. */
    private fun priceWeightedDowInterval(input: MarketIndexCalculationInput): IndexIntervalCalculation {
        var previousCloseSum = 0.0
        var openPriceSum = 0.0
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
            closePriceSum += bar.close
            constituentCount += 1
        }

        if (previousCloseSum == 0.0) return IndexIntervalCalculation.neutral()
        val openFactor = openPriceSum / previousCloseSum
        val closeFactor = closePriceSum / previousCloseSum
        return IndexIntervalCalculation(
            openFactor = openFactor,
            highFactor = max(openFactor, closeFactor),
            lowFactor = min(openFactor, closeFactor),
            closeFactor = closeFactor,
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

    companion object {
        /**
         * 2026-07-31 스냅샷. Alphabet(GOOGL)이 2026-06-29 Verizon(VZ)를 대체한 변경을 포함한다.
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
        internal const val MINIMUM_HOURLY_RETURN: Double = -0.99
        internal const val MAXIMUM_HOURLY_RETURN: Double = 10.0
    }
}
