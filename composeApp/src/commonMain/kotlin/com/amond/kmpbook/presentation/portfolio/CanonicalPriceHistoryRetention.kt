package com.amond.kmpbook.presentation.portfolio

import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.pricing.PriceBar
import com.amond.kmpbook.domain.time.GameCalendar

/** Shared bounded retention for the recent session grid and the 16-point surveillance window. */
object CanonicalPriceHistoryRetention {
    const val MAX_HOURLY_BARS: Int = 384
    const val MAX_ONE_DAY_BARS: Int = 100
    const val CHRONOLOGICAL_HOURLY_TAIL: Int = 256
    const val CHRONOLOGICAL_ONE_DAY_TAIL: Int = 84
    const val POSITIVE_DECISION_DAYS: Int = 16

    fun hourly(market: Market, bars: List<PriceBar>): List<PriceBar> {
        if (bars.size <= CHRONOLOGICAL_HOURLY_TAIL) return bars
        val tailStart = bars.size - CHRONOLOGICAL_HOURLY_TAIL
        val boundaryDate = GameCalendar.marketLocalDateTime(market, bars[tailStart].startTime).date
        val positiveDecisionDates = positiveDecisionDates(market, bars)
        return bars.filterIndexed { index, bar ->
            index >= tailStart ||
                GameCalendar.marketLocalDateTime(market, bar.startTime).date == boundaryDate ||
                GameCalendar.marketLocalDateTime(market, bar.startTime).date in positiveDecisionDates
        }.also { retained -> check(retained.size <= MAX_HOURLY_BARS) }
    }

    fun oneDay(market: Market, bars: List<PriceBar>): List<PriceBar> {
        val positiveDecisionDates = positiveDecisionDates(market, bars)
        val tailStart = (bars.size - CHRONOLOGICAL_ONE_DAY_TAIL).coerceAtLeast(0)
        return bars.filterIndexed { index, bar ->
            index >= tailStart ||
                GameCalendar.marketLocalDateTime(market, bar.startTime).date in positiveDecisionDates
        }.also { retained -> check(retained.size <= MAX_ONE_DAY_BARS) }
    }

    private fun positiveDecisionDates(market: Market, bars: List<PriceBar>) = bars.asSequence()
        .groupBy { bar -> GameCalendar.marketLocalDateTime(market, bar.startTime).date }
        .asSequence()
        .filter { (date, dateBars) ->
            dateBars.any { bar -> bar.volume > 0L } &&
                GameCalendar.regularSessionWindow(market, date)?.closesAt?.let { closesAt ->
                    dateBars.maxOf(PriceBar::endTime) >= closesAt
                } == true
        }
        .map { (date, _) -> date }
        .sorted()
        .toList()
        .takeLast(POSITIVE_DECISION_DAYS)
        .toSet()
}
