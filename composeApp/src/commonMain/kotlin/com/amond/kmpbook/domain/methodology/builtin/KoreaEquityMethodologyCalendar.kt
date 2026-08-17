package com.amond.kmpbook.domain.methodology.builtin

import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.time.DefaultMarketHolidays
import com.amond.kmpbook.domain.time.GameCalendar
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** Deterministic KRX session and methodology-date helpers shared by Korean equity providers. */
internal object KoreaEquityMethodologyCalendar {
    fun secondThursday(year: Int, month: Int): LocalDate {
        require(month in 1..12)
        var date = LocalDate(year, month, 1)
        while (date.dayOfWeek != DayOfWeek.THURSDAY) {
            date = date.plus(1, DateTimeUnit.DAY)
        }
        return date.plus(7, DateTimeUnit.DAY)
    }

    /** The first KRX regular-session open after the methodology's second-Thursday close. */
    fun scheduledEffectiveDateAfterSecondThursday(year: Int, month: Int): LocalDate =
        addKrxTradingDays(secondThursday(year, month), 1)

    fun addKrxTradingDays(date: LocalDate, days: Int): LocalDate {
        require(days >= 0)
        var result = date
        var remaining = days
        while (remaining > 0) {
            result = result.plus(1, DateTimeUnit.DAY)
            if (isKrxTradingDate(result)) remaining -= 1
        }
        return result
    }

    fun subtractKrxTradingDays(date: LocalDate, days: Int): LocalDate {
        require(days >= 0)
        var result = date
        var remaining = days
        while (remaining > 0) {
            result = result.minus(1, DateTimeUnit.DAY)
            if (isKrxTradingDate(result)) remaining -= 1
        }
        return result
    }

    fun firstKrxTradingDateOnOrAfter(date: LocalDate): LocalDate {
        var result = date
        while (!isKrxTradingDate(result)) result = result.plus(1, DateTimeUnit.DAY)
        return result
    }

    fun lastKrxTradingDateOnOrBefore(date: LocalDate): LocalDate {
        var result = date
        while (!isKrxTradingDate(result)) result = result.minus(1, DateTimeUnit.DAY)
        return result
    }

    fun firstKrxTradingDateOfNextMonth(date: LocalDate): LocalDate {
        val firstOfNextMonth = if (date.month.ordinal + 1 == 12) {
            LocalDate(date.year + 1, 1, 1)
        } else {
            LocalDate(date.year, date.month.ordinal + 2, 1)
        }
        return firstKrxTradingDateOnOrAfter(firstOfNextMonth)
    }

    fun isKrxTradingDate(date: LocalDate): Boolean {
        if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) {
            return false
        }
        return !DefaultMarketHolidays.supportsYear(date.year) ||
            date !in DefaultMarketHolidays.closedDates(Market.KOSPI, date.year)
    }

    fun hasPassedKrxRegularOpen(effectiveDate: LocalDate, at: Instant): Boolean {
        val local = at.toLocalDateTime(GameCalendar.KOREA_TIME_ZONE)
        return local.date > effectiveDate ||
            local.date == effectiveDate && local.time > KRX_REGULAR_OPEN
    }

    fun hasReachedKrxRegularClose(referenceDate: LocalDate, at: Instant): Boolean {
        val local = at.toLocalDateTime(GameCalendar.KOREA_TIME_ZONE)
        return local.date > referenceDate ||
            local.date == referenceDate && local.time >= KRX_REGULAR_CLOSE
    }

    fun intersectsKrxRegularSession(from: Instant, to: Instant): Boolean {
        val fromKorea = from.toLocalDateTime(GameCalendar.KOREA_TIME_ZONE)
        val toKorea = to.toLocalDateTime(GameCalendar.KOREA_TIME_ZONE)
        return fromKorea.date == toKorea.date &&
            toKorea.time > KRX_REGULAR_OPEN && fromKorea.time < KRX_REGULAR_CLOSE
    }

    fun reachesKrxRegularClose(from: Instant, to: Instant): Boolean {
        val fromKorea = from.toLocalDateTime(GameCalendar.KOREA_TIME_ZONE)
        val toKorea = to.toLocalDateTime(GameCalendar.KOREA_TIME_ZONE)
        return fromKorea.date == toKorea.date &&
            fromKorea.time < KRX_REGULAR_CLOSE && toKorea.time >= KRX_REGULAR_CLOSE
    }

    private val KRX_REGULAR_OPEN: LocalTime = LocalTime(9, 0)
    private val KRX_REGULAR_CLOSE: LocalTime = LocalTime(15, 30)
}
