package com.amond.kmpbook.domain.model.reference

import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.time.DefaultMarketHolidays
import com.amond.kmpbook.domain.time.GameCalendar
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Frozen game calendars used to consume roll days. The U.S. entry is deliberately named as an
 * approximation because individual CME/ICE contracts can have product-specific partial sessions.
 */
enum class FuturesRollCalendar {
    US_FUTURES_FULL_DAY_APPROXIMATION,
    KRX_DERIVATIVES_FULL_DAY_APPROXIMATION,
    ;

    fun isTradingDate(date: LocalDate): Boolean {
        if (date.year !in GameCalendar.START_LOCAL_DATE_TIME.year..GameCalendar.CAMPAIGN_END_DATE.year) {
            return false
        }
        val market = when (this) {
            US_FUTURES_FULL_DAY_APPROXIMATION -> Market.NYSE
            KRX_DERIVATIVES_FULL_DAY_APPROXIMATION -> Market.KOSPI
        }
        return !GameCalendar.isWeekend(date) &&
            date !in DefaultMarketHolidays.closedDates(market, date.year)
    }

    /** Trading dates strictly after [from] and through [through], used for a roll countdown. */
    fun tradingDaysAfterThrough(from: LocalDate, through: LocalDate): Int {
        if (through <= from) return 0
        var date = from.plus(1, DateTimeUnit.DAY)
        var result = 0
        while (date <= through) {
            val supportedYear =
                date.year in GameCalendar.START_LOCAL_DATE_TIME.year..GameCalendar.CAMPAIGN_END_DATE.year
            val isOpen = if (supportedYear) {
                isTradingDate(date)
            } else {
                date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY
            }
            if (isOpen) result += 1
            date = date.plus(1, DateTimeUnit.DAY)
        }
        return result
    }
}
