package com.amond.kmpbook.ui.screens.market

import com.amond.kmpbook.domain.model.pricing.PriceBar

internal enum class MarketChartRange(
    val displayName: String,
    val durationSeconds: Long?,
) {
    ONE_DAY("1D", 24L * 60L * 60L),
    ONE_MONTH("1M", 31L * 24L * 60L * 60L),
    THREE_MONTHS("3M", 93L * 24L * 60L * 60L),
    ONE_YEAR("1Y", 366L * 24L * 60L * 60L),
    ALL("ALL", null),
    ;

    fun isAvailableFor(
        candleInterval: MarketCandleInterval,
        bars: List<PriceBar>,
    ): Boolean {
        val duration = durationSeconds ?: return true
        if (bars.size < MIN_VISIBLE_BAR_COUNT) return false
        if (duration < candleInterval.approximateDurationSeconds * MIN_VISIBLE_BAR_COUNT) return false

        val requestedStart = bars.last().startTime.epochSeconds - duration
        if (bars.first().startTime.epochSeconds > requestedStart) return false

        return bars.count { it.startTime.epochSeconds >= requestedStart } >= MIN_VISIBLE_BAR_COUNT
    }

    private companion object {
        const val MIN_VISIBLE_BAR_COUNT = 3L
    }
}

private val MarketCandleInterval.approximateDurationSeconds: Long
    get() = when (this) {
        MarketCandleInterval.ONE_HOUR -> 60L * 60L
        MarketCandleInterval.ONE_DAY -> 24L * 60L * 60L
        MarketCandleInterval.ONE_WEEK -> 7L * 24L * 60L * 60L
        MarketCandleInterval.ONE_MONTH -> 31L * 24L * 60L * 60L
        MarketCandleInterval.THREE_MONTHS -> 93L * 24L * 60L * 60L
    }
