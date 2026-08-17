package com.amond.kmpbook.presentation.portfolio

import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.pricing.PriceBar
import com.amond.kmpbook.domain.model.pricing.PriceBarInterval
import com.amond.kmpbook.domain.time.GameCalendar
import kotlin.time.Duration.Companion.hours

/** Deterministic ONE_DAY aggregation shared by runtime corporate-action rebasing and save checks. */
object CanonicalDailyPriceBarProjection {
    fun aggregateRetainedHourly(
        stockId: String,
        market: Market,
        hourlyBars: List<PriceBar>,
        dropPotentiallyTruncatedFirstDate: Boolean,
    ): List<PriceBar> {
        val groups = hourlyBars.asSequence()
            .filterNot { bar ->
                bar.volume == 0L && bar.endTime == GameCalendar.startInstant &&
                    bar.startTime == GameCalendar.startInstant - 1.hours
            }
            .filter { bar -> GameCalendar.regularTradingFraction(market, bar.startTime) > 0.0 }
            .groupBy { bar -> GameCalendar.marketLocalDateTime(market, bar.startTime).date }
            .toSortedMap()
            .entries
            .let { entries ->
                if (dropPotentiallyTruncatedFirstDate && entries.isNotEmpty()) entries.drop(1)
                else entries
            }
        return groups.map { (_, dateBars) ->
            val bars = dateBars.sortedBy(PriceBar::startTime)
            PriceBar(
                stockId = stockId,
                startTime = bars.first().startTime,
                endTime = bars.maxOf(PriceBar::endTime),
                step = PriceBarInterval.ONE_DAY,
                open = bars.first().open,
                high = bars.maxOf(PriceBar::high),
                low = bars.minOf(PriceBar::low),
                close = bars.last().close,
                volume = bars.sumOf(PriceBar::volume),
            )
        }
    }
}
