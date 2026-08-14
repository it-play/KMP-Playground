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

/** Deterministic U.S. session and methodology-date helpers owned by the SCHD provider. */
internal object SchdDividend100Calendar {
    fun scheduledRebalanceDate(year: Int, month: Int): LocalDate {
        require(month in 1..12)
        var effective = thirdFriday(year, month).plus(3, DateTimeUnit.DAY)
        while (!isUsTradingDate(effective)) effective = effective.plus(1, DateTimeUnit.DAY)
        return effective
    }

    fun annualWeightReferenceDate(effectiveDate: LocalDate): LocalDate =
        subtractUsTradingDays(effectiveDate, ANNUAL_WEIGHT_REFERENCE_LEAD_DAYS)

    fun quarterlyWeightReferenceDate(effectiveDate: LocalDate): LocalDate {
        var firstFriday = LocalDate(effectiveDate.year, effectiveDate.month.ordinal + 1, 1)
        while (firstFriday.dayOfWeek != DayOfWeek.FRIDAY) {
            firstFriday = firstFriday.plus(1, DateTimeUnit.DAY)
        }
        var reference = firstFriday.minus(2, DateTimeUnit.DAY)
        while (!isUsTradingDate(reference)) reference = reference.minus(1, DateTimeUnit.DAY)
        return reference
    }

    fun thirdFriday(year: Int, month: Int): LocalDate {
        require(month in 1..12)
        var friday = LocalDate(year, month, 1)
        while (friday.dayOfWeek != DayOfWeek.FRIDAY) friday = friday.plus(1, DateTimeUnit.DAY)
        return friday.plus(14, DateTimeUnit.DAY)
    }

    fun isDailyCapFreezeDate(rebalanceMonths: Set<Int>, date: LocalDate): Boolean {
        if (date.month.ordinal + 1 !in rebalanceMonths) return false
        val thirdFriday = thirdFriday(date.year, date.month.ordinal + 1)
        val secondFriday = thirdFriday.minus(7, DateTimeUnit.DAY)
        val freezeStarts = secondFriday.minus(2, DateTimeUnit.DAY)
        val freezeEnds = thirdFriday.plus(3, DateTimeUnit.DAY)
        return date in freezeStarts..freezeEnds
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

    fun subtractUsTradingDays(date: LocalDate, days: Int): LocalDate {
        require(days >= 0)
        var result = date
        var remaining = days
        while (remaining > 0) {
            result = result.minus(1, DateTimeUnit.DAY)
            if (isUsTradingDate(result)) remaining -= 1
        }
        return result
    }

    fun lastUsTradingDateOfMonth(year: Int, month: Int): LocalDate {
        require(month in 1..12)
        val firstOfNextMonth = if (month == 12) {
            LocalDate(year + 1, 1, 1)
        } else {
            LocalDate(year, month + 1, 1)
        }
        var date = firstOfNextMonth.minus(1, DateTimeUnit.DAY)
        while (!isUsTradingDate(date)) date = date.minus(1, DateTimeUnit.DAY)
        return date
    }

    fun firstUsTradingDateOfNextMonth(date: LocalDate): LocalDate {
        var result = if (date.month.ordinal + 1 == 12) {
            LocalDate(date.year + 1, 1, 1)
        } else {
            LocalDate(date.year, date.month.ordinal + 2, 1)
        }
        while (!isUsTradingDate(result)) result = result.plus(1, DateTimeUnit.DAY)
        return result
    }

    fun firstUsTradingDateOnOrAfter(date: LocalDate): LocalDate {
        var result = date
        while (!isUsTradingDate(result)) result = result.plus(1, DateTimeUnit.DAY)
        return result
    }

    fun isUsTradingDate(date: LocalDate): Boolean {
        if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) return false
        return date.year !in HOLIDAY_CALENDAR_START_YEAR..GameCalendar.CAMPAIGN_END_DATE.year ||
            date !in DefaultMarketHolidays.closedDates(Market.NYSE, date.year)
    }

    /** 효력일 정규장 개장 구간이 끝나 새 구성이 적용됐어야 하는 시점인지 판정한다. */
    fun hasPassedUsRegularOpen(effectiveDate: LocalDate, at: Instant): Boolean {
        val local = at.toLocalDateTime(GameCalendar.NEW_YORK_TIME_ZONE)
        return local.date > effectiveDate ||
            local.date == effectiveDate && local.time > US_REGULAR_OPEN
    }

    /** 기준일 종가 계산으로 구성 계획을 만들 수 있게 된 시점인지 판정한다. */
    fun hasReachedUsRegularClose(referenceDate: LocalDate, at: Instant): Boolean {
        val local = at.toLocalDateTime(GameCalendar.NEW_YORK_TIME_ZONE)
        return local.date > referenceDate ||
            local.date == referenceDate && local.time >= US_REGULAR_CLOSE
    }

    fun intersectsUsRegularSession(from: Instant, to: Instant): Boolean {
        val fromNewYork = from.toLocalDateTime(GameCalendar.NEW_YORK_TIME_ZONE)
        val toNewYork = to.toLocalDateTime(GameCalendar.NEW_YORK_TIME_ZONE)
        return fromNewYork.date == toNewYork.date &&
            toNewYork.time > US_REGULAR_OPEN && fromNewYork.time < US_REGULAR_CLOSE
    }

    fun reachesUsRegularClose(from: Instant, to: Instant): Boolean {
        val fromNewYork = from.toLocalDateTime(GameCalendar.NEW_YORK_TIME_ZONE)
        val toNewYork = to.toLocalDateTime(GameCalendar.NEW_YORK_TIME_ZONE)
        return fromNewYork.date == toNewYork.date &&
            fromNewYork.time < US_REGULAR_CLOSE && toNewYork.time >= US_REGULAR_CLOSE
    }

    private const val ANNUAL_WEIGHT_REFERENCE_LEAD_DAYS: Int = 12
    private const val HOLIDAY_CALENDAR_START_YEAR: Int = 2026
    private val US_REGULAR_OPEN: LocalTime = LocalTime(9, 30)
    private val US_REGULAR_CLOSE: LocalTime = LocalTime(16, 0)
}
