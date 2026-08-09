package com.amond.kmpbook.ui.screens

import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.PriceBar
import com.amond.kmpbook.domain.time.GameCalendar
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus

internal enum class MarketChartRange(
    val displayName: String,
    val usesDailyBars: Boolean,
) {
    ONE_DAY("1일", false),
    ONE_WEEK("1주", false),
    ONE_MONTH("1개월", true),
    THREE_MONTHS("3개월", true),
    ;

    fun selectBars(
        intradayBars: List<PriceBar>,
        dailyBars: List<PriceBar>,
        market: Market,
    ): List<PriceBar> {
        val source = (if (usesDailyBars) dailyBars else intradayBars)
            .asSequence()
            .filter { it.volume > 0L }
            .sortedBy(PriceBar::startTime)
            .toList()
        val latestDate = source.lastOrNull()?.let { bar ->
            GameCalendar.marketLocalDateTime(market, bar.startTime).date
        } ?: return emptyList()
        val firstDate = when (this) {
            ONE_DAY -> latestDate
            ONE_WEEK -> latestDate.minus(6, DateTimeUnit.DAY)
            ONE_MONTH -> latestDate.minus(1, DateTimeUnit.MONTH)
            THREE_MONTHS -> latestDate.minus(3, DateTimeUnit.MONTH)
        }
        return source.filter { bar ->
            GameCalendar.marketLocalDateTime(market, bar.startTime).date >= firstDate
        }
    }
}
