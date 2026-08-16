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

/** NYSE session and quarterly date calculations for the Morningstar large-cap value provider. */
internal object MorningstarLargeCapValueCalendar {
    /** Ranking is frozen after the first Friday close, rolled back when that date is not a session. */
    fun quarterlyRankingDate(year: Int, month: Int): LocalDate {
        var date = firstWeekday(year, month, DayOfWeek.FRIDAY)
        while (!isUsTradingDate(date)) date = date.minus(1, DateTimeUnit.DAY)
        return date
    }

    /** Published fifth transition close: the first trading Tuesday after the third Friday. */
    fun quarterlyFinalTransitionCloseDate(year: Int, month: Int): LocalDate {
        var date = thirdFriday(year, month).plus(4, DateTimeUnit.DAY)
        while (!isUsTradingDate(date)) date = date.plus(1, DateTimeUnit.DAY)
        return date
    }

    /** Four published partial transition closes precede the fifth, final transition close. */
    fun quarterlyPartialTransitionCloseDates(year: Int, month: Int): List<LocalDate> {
        val finalDate = quarterlyFinalTransitionCloseDate(year, month)
        val descending = buildList {
            var date = finalDate
            repeat(PARTIAL_TRANSITION_COUNT) {
                do {
                    date = date.minus(1, DateTimeUnit.DAY)
                } while (!isUsTradingDate(date))
                add(date)
            }
        }
        return descending.asReversed()
    }

    /**
     * The host mutates its daily ledger after regular open. A close-effective index state is
     * therefore recorded at the next NYSE session open, avoiding one session of premature return.
     */
    fun quarterlyFinalApplicationDate(year: Int, month: Int): LocalDate =
        addUsTradingDays(quarterlyFinalTransitionCloseDate(year, month), 1)

    fun quarterlyPartialApplicationDates(year: Int, month: Int): List<LocalDate> =
        quarterlyPartialTransitionCloseDates(year, month).map { closeDate ->
            addUsTradingDays(closeDate, 1)
        }

    /** Quarter-end RIC diversification test close. */
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

    fun firstWeekday(year: Int, month: Int, dayOfWeek: DayOfWeek): LocalDate {
        require(month in 1..12)
        var date = LocalDate(year, month, 1)
        while (date.dayOfWeek != dayOfWeek) date = date.plus(1, DateTimeUnit.DAY)
        return date
    }

    fun thirdFriday(year: Int, month: Int): LocalDate =
        firstWeekday(year, month, DayOfWeek.FRIDAY).plus(14, DateTimeUnit.DAY)

    fun isUsTradingDate(date: LocalDate): Boolean {
        if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) return false
        return date.year !in HOLIDAY_CALENDAR_START_YEAR..GameCalendar.CAMPAIGN_END_DATE.year ||
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

    private const val PARTIAL_TRANSITION_COUNT: Int = 4
    private const val HOLIDAY_CALENDAR_START_YEAR: Int = 2026
    private val US_REGULAR_OPEN: LocalTime = LocalTime(9, 30)
    private val US_REGULAR_CLOSE: LocalTime = LocalTime(16, 0)
}
