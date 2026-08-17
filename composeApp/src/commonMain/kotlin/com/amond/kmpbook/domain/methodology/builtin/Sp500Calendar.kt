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

/** S&P 500 v2가 사용하는 미국 거래 세션과 분기 기준일 계산이다. */
internal object Sp500Calendar {
    fun quarterlyEffectiveDate(year: Int, month: Int): LocalDate {
        var date = thirdFriday(year, month).plus(1, DateTimeUnit.DAY)
        while (!isUsTradingDate(date)) date = date.plus(1, DateTimeUnit.DAY)
        return date
    }

    /** 셋째 금요일이 휴장일이면 그 직전 NYSE 거래일 종가를 사용한다. */
    fun quarterlyReferenceDate(year: Int, month: Int): LocalDate {
        var date = thirdFriday(year, month)
        while (!isUsTradingDate(date)) date = date.minus(1, DateTimeUnit.DAY)
        return date
    }

    fun thirdFriday(year: Int, month: Int): LocalDate {
        require(month in 1..12)
        var date = LocalDate(year, month, 1)
        while (date.dayOfWeek != DayOfWeek.FRIDAY) date = date.plus(1, DateTimeUnit.DAY)
        return date.plus(14, DateTimeUnit.DAY)
    }

    fun isUsTradingDate(date: LocalDate): Boolean {
        if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) return false
        return !DefaultMarketHolidays.supportsYear(date.year) ||
            date !in DefaultMarketHolidays.closedDates(Market.NYSE, date.year)
    }

    fun addUsTradingDays(date: LocalDate, days: Int): LocalDate {
        require(days >= 0)
        var result = date
        var remaining = days
        while (remaining > 0) {
            result = result.plus(1, DateTimeUnit.DAY)
            if (isUsTradingDate(result)) remaining -= 1
        }
        return result
    }

    fun hasPassedUsRegularOpen(effectiveDate: LocalDate, at: Instant): Boolean {
        val local = at.toLocalDateTime(GameCalendar.NEW_YORK_TIME_ZONE)
        return local.date > effectiveDate ||
            local.date == effectiveDate && local.time > US_REGULAR_OPEN
    }

    fun hasReachedUsRegularClose(referenceDate: LocalDate, at: Instant): Boolean {
        val session = usRegularSession(referenceDate) ?: return false
        return at >= session.closesAt
    }

    fun intersectsUsRegularSession(from: Instant, to: Instant): Boolean {
        val fromNewYork = from.toLocalDateTime(GameCalendar.NEW_YORK_TIME_ZONE)
        val toNewYork = to.toLocalDateTime(GameCalendar.NEW_YORK_TIME_ZONE)
        if (fromNewYork.date != toNewYork.date) return false
        val session = usRegularSession(fromNewYork.date) ?: return false
        return to > session.opensAt && from < session.closesAt
    }

    fun reachesUsRegularClose(from: Instant, to: Instant): Boolean {
        val fromNewYork = from.toLocalDateTime(GameCalendar.NEW_YORK_TIME_ZONE)
        val toNewYork = to.toLocalDateTime(GameCalendar.NEW_YORK_TIME_ZONE)
        if (fromNewYork.date != toNewYork.date) return false
        val session = usRegularSession(fromNewYork.date) ?: return false
        return from < session.closesAt && to >= session.closesAt
    }

    private fun usRegularSession(date: LocalDate) = GameCalendar.regularSessionWindow(
        market = Market.NYSE,
        localDate = date,
        closedDates = if (DefaultMarketHolidays.supportsYear(date.year)) {
            DefaultMarketHolidays.closedDates(Market.NYSE, date.year)
        } else {
            emptySet()
        },
    )

    private val US_REGULAR_OPEN: LocalTime = LocalTime(9, 30)
}
