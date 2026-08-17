package com.amond.kmpbook.domain.simulation.reference

import com.amond.kmpbook.domain.model.fund.CompositeRebalanceCalendar
import com.amond.kmpbook.domain.model.fund.CompositeRebalanceSchedule
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.market.ReferenceCurrency
import com.amond.kmpbook.domain.time.DefaultMarketHolidays
import com.amond.kmpbook.domain.time.GameCalendar
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** Calendar resolver shared by composite and alternative-risk-premia reference books. */
internal object CompositeScheduleResolver {
    private val scheduledDateCache = mutableMapOf<String, List<LocalDate>>()
    private val closedDateCache = mutableMapOf<Pair<Market, Int>, Set<LocalDate>>()

    fun nextDate(
        schedule: CompositeRebalanceSchedule,
        baseCurrency: ReferenceCurrency,
        after: LocalDate,
    ): LocalDate? {
        if (schedule.calendar == CompositeRebalanceCalendar.STATIC) return null
        var year = after.year
        while (year <= LAST_YEAR) {
            scheduledDates(schedule, baseCurrency, year).firstOrNull { it > after }?.let { return it }
            year += 1
        }
        return null
    }

    fun nextDateAfterInstant(
        schedule: CompositeRebalanceSchedule,
        baseCurrency: ReferenceCurrency,
        at: Instant,
    ): LocalDate? {
        if (schedule.calendar == CompositeRebalanceCalendar.STATIC) return null
        val market = market(baseCurrency)
        val localDate = at.toLocalDateTime(GameCalendar.timeZoneFor(market)).date
        var year = localDate.year
        while (year <= LAST_YEAR) {
            scheduledDates(schedule, baseCurrency, year).firstOrNull { date ->
                closeInstant(baseCurrency, date) > at
            }?.let { return it }
            year += 1
        }
        return null
    }

    fun crossesClose(
        baseCurrency: ReferenceCurrency,
        date: LocalDate?,
        from: Instant,
        to: Instant,
    ): Boolean {
        if (date == null) return false
        val close = closeInstant(baseCurrency, date)
        return from < close && to >= close
    }

    fun closeAt(baseCurrency: ReferenceCurrency, date: LocalDate): Instant =
        closeInstant(baseCurrency, date)

    fun localDateAt(baseCurrency: ReferenceCurrency, at: Instant): LocalDate =
        at.toLocalDateTime(GameCalendar.timeZoneFor(market(baseCurrency))).date

    private fun scheduledDates(
        schedule: CompositeRebalanceSchedule,
        baseCurrency: ReferenceCurrency,
        year: Int,
    ): List<LocalDate> {
        val key = "${schedule.calendar}:${schedule.months.joinToString(",")}:$baseCurrency:$year"
        return scheduledDateCache.getOrPut(key) {
            when (schedule.calendar) {
                CompositeRebalanceCalendar.STATIC -> emptyList()
                CompositeRebalanceCalendar.DAILY,
                CompositeRebalanceCalendar.CONTINUOUS_ACTIVE,
                -> tradingDates(baseCurrency, year)
                CompositeRebalanceCalendar.MONTHLY,
                CompositeRebalanceCalendar.QUARTERLY,
                CompositeRebalanceCalendar.SEMI_ANNUAL,
                CompositeRebalanceCalendar.ANNUAL,
                -> schedule.months.map { month ->
                    lastTradingDate(baseCurrency, year, month)
                }.sorted()
            }
        }
    }

    private fun tradingDates(baseCurrency: ReferenceCurrency, year: Int): List<LocalDate> {
        val market = market(baseCurrency)
        val result = mutableListOf<LocalDate>()
        var date = LocalDate(year, 1, 1)
        val end = LocalDate(year, 12, 31)
        while (date <= end) {
            if (isTradingDate(market, date)) result += date
            date = date.plus(1, DateTimeUnit.DAY)
        }
        return result
    }

    private fun lastTradingDate(
        baseCurrency: ReferenceCurrency,
        year: Int,
        month: Int,
    ): LocalDate {
        val market = market(baseCurrency)
        val nextMonth = if (month == 12) LocalDate(year + 1, 1, 1) else LocalDate(year, month + 1, 1)
        var date = nextMonth.minus(1, DateTimeUnit.DAY)
        while (!isTradingDate(market, date)) date = date.minus(1, DateTimeUnit.DAY)
        return date
    }

    private fun isTradingDate(market: Market, date: LocalDate): Boolean =
        !GameCalendar.isWeekend(date) &&
            (!DefaultMarketHolidays.supportsYear(date.year) ||
                date !in closedDateCache.getOrPut(market to date.year) {
                    DefaultMarketHolidays.closedDates(market, date.year)
                })

    private fun closeInstant(baseCurrency: ReferenceCurrency, date: LocalDate): Instant {
        val market = market(baseCurrency)
        return requireNotNull(
            GameCalendar.regularSessionWindow(
                market = market,
                localDate = date,
                closedDates = closedDateCache.getOrPut(market to date.year) {
                    if (DefaultMarketHolidays.supportsYear(date.year)) {
                        DefaultMarketHolidays.closedDates(market, date.year)
                    } else {
                        emptySet()
                    }
                },
            ),
        ) {
            "$date 은 $market 정규장 거래일이 아닙니다."
        }.closesAt
    }

    private fun market(baseCurrency: ReferenceCurrency): Market =
        if (baseCurrency == ReferenceCurrency.KRW) Market.KOSPI else Market.NYSE

    private const val FIRST_YEAR: Int = 2026
    private const val LAST_YEAR: Int = 2040
}
