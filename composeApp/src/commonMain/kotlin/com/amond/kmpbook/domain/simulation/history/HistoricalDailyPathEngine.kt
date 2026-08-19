package com.amond.kmpbook.domain.simulation.history

import com.amond.kmpbook.domain.model.history.HistoricalDailyBar
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.time.GameCalendar
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.time.Instant

/** 실제 일봉 OHLCV를 거래소 정규장 시간봉으로 손실 없이 재구성한다. */
class HistoricalDailyPathEngine(
    private val scenario: HistoricalScenarioEngine,
) {
    fun intervalAnchor(
        instrumentId: String,
        market: Market,
        from: Instant,
        to: Instant,
        playerDeviationAtOpen: Double = 0.0,
        playerDeviationAtClose: Double = 0.0,
    ): HistoricalIntervalPriceAnchor? {
        require(from < to) { "역사 가격 구간 종료는 시작보다 늦어야 합니다." }
        if (from >= scenario.pack.definition.historicalThroughAt) return null

        val localDate = GameCalendar.marketLocalDateTime(market, from).date
        val dailyBar = scenario.dailyBar(instrumentId, localDate) ?: return null
        val session = GameCalendar.regularSessionWindow(market, localDate) ?: return null
        val overlapStart = maxOf(from, session.opensAt)
        val overlapEnd = minOf(to, session.closesAt)
        if (overlapEnd <= overlapStart) return null

        val sessionMillis = (session.closesAt - session.opensAt).inWholeMilliseconds.toDouble()
        val startProgress = ((overlapStart - session.opensAt).inWholeMilliseconds / sessionMillis)
            .coerceIn(0.0, 1.0)
        val endProgress = ((overlapEnd - session.opensAt).inWholeMilliseconds / sessionMillis)
            .coerceIn(0.0, 1.0)
        val baseOpen = pathPrice(dailyBar, startProgress)
        val baseClose = pathPrice(dailyBar, endProgress)
        val includedExtremes = pathExtremes(dailyBar, startProgress, endProgress)

        val open = baseOpen * exp(playerDeviationAtOpen)
        val close = baseClose * exp(playerDeviationAtClose)
        val upperDeviation = max(playerDeviationAtOpen, playerDeviationAtClose)
        val lowerDeviation = min(playerDeviationAtOpen, playerDeviationAtClose)
        val high = max(
            max(open, close),
            includedExtremes.second * exp(upperDeviation),
        )
        val low = min(
            min(open, close),
            includedExtremes.first * exp(lowerDeviation),
        )
        val volume = intervalVolume(dailyBar.volume, startProgress, endProgress)

        return HistoricalIntervalPriceAnchor(
            tradingDate = localDate,
            open = open,
            high = high,
            low = low,
            close = close,
            volume = volume,
            playerDeviationAtOpen = playerDeviationAtOpen,
            playerDeviationAtClose = playerDeviationAtClose,
        )
    }

    private fun pathPrice(bar: HistoricalDailyBar, progress: Double): Double {
        val (firstExtreme, secondExtreme) = orderedExtremes(bar)
        return when {
            progress <= FIRST_EXTREME_PROGRESS -> interpolate(
                bar.open,
                firstExtreme,
                progress / FIRST_EXTREME_PROGRESS,
            )
            progress <= SECOND_EXTREME_PROGRESS -> interpolate(
                firstExtreme,
                secondExtreme,
                (progress - FIRST_EXTREME_PROGRESS) /
                    (SECOND_EXTREME_PROGRESS - FIRST_EXTREME_PROGRESS),
            )
            else -> interpolate(
                secondExtreme,
                bar.close,
                (progress - SECOND_EXTREME_PROGRESS) /
                    (1.0 - SECOND_EXTREME_PROGRESS),
            )
        }
    }

    private fun pathExtremes(
        bar: HistoricalDailyBar,
        startProgress: Double,
        endProgress: Double,
    ): Pair<Double, Double> {
        var low = min(pathPrice(bar, startProgress), pathPrice(bar, endProgress))
        var high = max(pathPrice(bar, startProgress), pathPrice(bar, endProgress))
        val (firstExtreme, secondExtreme) = orderedExtremes(bar)
        if (FIRST_EXTREME_PROGRESS > startProgress && FIRST_EXTREME_PROGRESS <= endProgress) {
            low = min(low, firstExtreme)
            high = max(high, firstExtreme)
        }
        if (SECOND_EXTREME_PROGRESS > startProgress && SECOND_EXTREME_PROGRESS <= endProgress) {
            low = min(low, secondExtreme)
            high = max(high, secondExtreme)
        }
        return low to high
    }

    private fun orderedExtremes(bar: HistoricalDailyBar): Pair<Double, Double> =
        if (bar.close >= bar.open) bar.low to bar.high else bar.high to bar.low

    private fun intervalVolume(dailyVolume: Long, startProgress: Double, endProgress: Double): Long {
        val cumulativeStart = (dailyVolume * volumeCumulative(startProgress)).roundToLong()
        val cumulativeEnd = (dailyVolume * volumeCumulative(endProgress)).roundToLong()
        return (cumulativeEnd - cumulativeStart).coerceAtLeast(0L)
    }

    /** 시작·마감 거래 집중을 재현하는 정규화된 U자형 누적 거래량 곡선이다. */
    private fun volumeCumulative(progress: Double): Double {
        val t = progress.coerceIn(0.0, 1.0)
        val unscaled = t + VOLUME_EDGE_WEIGHT * (
            4.0 / 3.0 * t * t * t - 2.0 * t * t + t
            )
        return unscaled / (1.0 + VOLUME_EDGE_WEIGHT / 3.0)
    }

    private fun interpolate(from: Double, to: Double, fraction: Double): Double =
        from + (to - from) * fraction.coerceIn(0.0, 1.0)

    private companion object {
        const val FIRST_EXTREME_PROGRESS: Double = 0.28
        const val SECOND_EXTREME_PROGRESS: Double = 0.72
        const val VOLUME_EDGE_WEIGHT: Double = 2.0
    }
}
