package com.amond.kmpbook.domain.simulation.schedule

import com.amond.kmpbook.domain.model.instrument.DistributionFrequency
import com.amond.kmpbook.domain.model.market.Market
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Schwab equity ETF의 2026 공식 ex-date와 그 이후 동결 시나리오 달력이다. */
internal object SchwabEquityEtfDistributionCalendar {
    fun isDistributionDate(date: LocalDate, frequency: DistributionFrequency): Boolean {
        if (frequency != DistributionFrequency.QUARTERLY || date.year !in 2026..2040) return false
        if (date.year == 2026) return date in OFFICIAL_2026_EX_DATES
        val anchor = when (date.month) {
            Month.MARCH, Month.JUNE, Month.SEPTEMBER ->
                nthWeekday(date.year, date.month, DayOfWeek.WEDNESDAY, 4)
            Month.DECEMBER -> nthWeekday(date.year, date.month, DayOfWeek.WEDNESDAY, 2)
            else -> return false
        }
        var projected = anchor
        while (!DistributionSchedule.isBusinessDate(Market.NYSE_ARCA, projected)) {
            projected = projected.minus(1, DateTimeUnit.DAY)
        }
        return date == projected
    }

    private fun nthWeekday(year: Int, month: Month, weekday: DayOfWeek, ordinal: Int): LocalDate {
        var date = LocalDate(year, month, 1)
        while (date.dayOfWeek != weekday) date = date.plus(1, DateTimeUnit.DAY)
        return date.plus((ordinal - 1) * 7, DateTimeUnit.DAY)
    }

    private val OFFICIAL_2026_EX_DATES: Set<LocalDate> = setOf(
        LocalDate(2026, 3, 25),
        LocalDate(2026, 6, 24),
        LocalDate(2026, 9, 23),
        LocalDate(2026, 12, 9),
    )
}
