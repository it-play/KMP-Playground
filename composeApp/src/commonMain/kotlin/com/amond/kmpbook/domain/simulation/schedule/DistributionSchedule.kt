package com.amond.kmpbook.domain.simulation.schedule

import com.amond.kmpbook.domain.model.instrument.DistributionFrequency
import com.amond.kmpbook.domain.model.instrument.DistributionCalendar
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.time.DefaultMarketHolidays
import com.amond.kmpbook.domain.time.GameCalendar
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.minus

/** Canonical frozen-scenario cash-distribution calendar shared by runtime and persistence checks. */
object DistributionSchedule {
    const val DISTRIBUTION_DAY: Int = 15

    fun isDistributionDate(
        date: LocalDate,
        frequency: DistributionFrequency,
        calendar: DistributionCalendar = DistributionCalendar.FIXED_DAY_15,
    ): Boolean = when (calendar) {
        DistributionCalendar.FIXED_DAY_15 -> when (frequency) {
            DistributionFrequency.NONE -> false
            DistributionFrequency.WEEKLY -> date.dayOfWeek == DayOfWeek.FRIDAY
            DistributionFrequency.MONTHLY -> date.day == DISTRIBUTION_DAY
            DistributionFrequency.QUARTERLY ->
                date.day == DISTRIBUTION_DAY && date.month in QUARTER_END_MONTHS
            DistributionFrequency.SEMIANNUAL ->
                date.day == DISTRIBUTION_DAY && date.month in SEMIANNUAL_MONTHS
            DistributionFrequency.ANNUAL ->
                date.day == DISTRIBUTION_DAY && date.month == Month.DECEMBER
        }
        DistributionCalendar.KRX_MONTH_END ->
            isEligibleMonth(date.month, frequency) && date == lastKrxBusinessDateOfMonth(date)
        DistributionCalendar.VANGUARD_VOO_EX_DATE ->
            VanguardVooDistributionCalendar.isDistributionDate(date, frequency)
        DistributionCalendar.VANGUARD_VTV_EX_DATE ->
            VanguardVtvDistributionCalendar.isDistributionDate(date, frequency)
        DistributionCalendar.KRX_PRECEDING_BUSINESS_DAY_15 ->
            frequency == DistributionFrequency.MONTHLY &&
                date == precedingKrxBusinessDateOnOrBefore(
                    LocalDate(date.year, date.month.ordinal + 1, DISTRIBUTION_DAY),
                )
    }

    private fun isEligibleMonth(month: Month, frequency: DistributionFrequency): Boolean = when (frequency) {
        DistributionFrequency.NONE -> false
        DistributionFrequency.WEEKLY -> false
        DistributionFrequency.MONTHLY -> true
        DistributionFrequency.QUARTERLY -> month in QUARTER_END_MONTHS
        DistributionFrequency.SEMIANNUAL -> month in SEMIANNUAL_MONTHS
        DistributionFrequency.ANNUAL -> month == Month.DECEMBER
    }

    private fun lastKrxBusinessDateOfMonth(date: LocalDate): LocalDate {
        val firstOfNextMonth = if (date.month == Month.DECEMBER) {
            LocalDate(date.year + 1, 1, 1)
        } else {
            LocalDate(date.year, date.month.ordinal + 2, 1)
        }
        var candidate = firstOfNextMonth.minus(1, DateTimeUnit.DAY)
        val holidays = DefaultMarketHolidays.closedDates(Market.KOSPI, candidate.year)
        while (GameCalendar.isWeekend(candidate) || candidate in holidays) {
            candidate = candidate.minus(1, DateTimeUnit.DAY)
        }
        return candidate
    }

    private fun precedingKrxBusinessDateOnOrBefore(date: LocalDate): LocalDate {
        var candidate = date
        val holidays = DefaultMarketHolidays.closedDates(Market.KOSPI, date.year)
        while (GameCalendar.isWeekend(candidate) || candidate in holidays) {
            candidate = candidate.minus(1, DateTimeUnit.DAY)
        }
        return candidate
    }

    private val QUARTER_END_MONTHS: Set<Month> = setOf(
        Month.MARCH,
        Month.JUNE,
        Month.SEPTEMBER,
        Month.DECEMBER,
    )
    private val SEMIANNUAL_MONTHS: Set<Month> = setOf(Month.JUNE, Month.DECEMBER)
}
