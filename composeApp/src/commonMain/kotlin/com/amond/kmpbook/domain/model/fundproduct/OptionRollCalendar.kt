package com.amond.kmpbook.domain.model.fundproduct

import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.time.DefaultMarketHolidays
import com.amond.kmpbook.domain.time.GameCalendar
import kotlinx.datetime.LocalDate

/** Calendar whose distinct regular-session closes consume option tenor days. */
enum class OptionRollCalendar {
    KRX_EQUITY,
    US_EQUITY,
    ;

    /** True only for a regular trading date in the frozen 2026-2040 game calendar. */
    fun isTradingDate(date: LocalDate): Boolean {
        if (date.year !in GameCalendar.START_LOCAL_DATE_TIME.year..GameCalendar.CAMPAIGN_END_DATE.year) {
            return false
        }
        val market = when (this) {
            KRX_EQUITY -> Market.KOSPI
            US_EQUITY -> Market.NYSE
        }
        return !GameCalendar.isWeekend(date) &&
            date !in DefaultMarketHolidays.closedDates(market, date.year)
    }
}
