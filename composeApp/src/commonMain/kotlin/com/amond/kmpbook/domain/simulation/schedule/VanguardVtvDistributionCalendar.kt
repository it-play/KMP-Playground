package com.amond.kmpbook.domain.simulation.schedule

import com.amond.kmpbook.domain.model.instrument.DistributionFrequency
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.time.DefaultMarketHolidays
import com.amond.kmpbook.domain.time.GameCalendar
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Vanguard Morningstar Value ETF(VTV)의 동결 시나리오 ex-date 달력이다. */
internal object VanguardVtvDistributionCalendar {
    fun isDistributionDate(
        date: LocalDate,
        frequency: DistributionFrequency,
    ): Boolean {
        if (frequency != DistributionFrequency.QUARTERLY || date.year !in SUPPORTED_YEARS) return false
        if (date.year == OFFICIAL_SCHEDULE_YEAR) return date in OFFICIAL_2026_EX_DATES
        return date == projectedExDate(date.year, date.month)
    }

    private fun projectedExDate(year: Int, month: Month): LocalDate? {
        val ordinalAnchor = when (month) {
            Month.MARCH -> nthWeekdayOfMonth(year, month, DayOfWeek.FRIDAY, occurrence = 4)
            Month.JUNE -> nthWeekdayOfMonth(year, month, DayOfWeek.FRIDAY, occurrence = 4)
            Month.SEPTEMBER -> nthWeekdayOfMonth(year, month, DayOfWeek.MONDAY, occurrence = 4)
            Month.DECEMBER -> nthWeekdayOfMonth(year, month, DayOfWeek.TUESDAY, occurrence = 4)
            else -> return null
        }
        return previousOrSameNyseArcaBusinessDate(ordinalAnchor)
    }

    private fun nthWeekdayOfMonth(
        year: Int,
        month: Month,
        weekday: DayOfWeek,
        occurrence: Int,
    ): LocalDate {
        var date = LocalDate(year, month, 1)
        while (date.dayOfWeek != weekday) date = date.plus(1, DateTimeUnit.DAY)
        return date.plus((occurrence - 1) * 7, DateTimeUnit.DAY)
    }

    private fun previousOrSameNyseArcaBusinessDate(anchor: LocalDate): LocalDate {
        val holidays = DefaultMarketHolidays.closedDates(Market.NYSE_ARCA, anchor.year)
        var candidate = anchor
        while (GameCalendar.isWeekend(candidate) || candidate in holidays) {
            candidate = candidate.minus(1, DateTimeUnit.DAY)
        }
        return candidate
    }

    private const val OFFICIAL_SCHEDULE_YEAR: Int = 2026
    private val SUPPORTED_YEARS: IntRange = 2026..2040

    private val OFFICIAL_2026_EX_DATES: Set<LocalDate> = setOf(
        LocalDate(2026, 3, 27),
        LocalDate(2026, 6, 26),
        LocalDate(2026, 9, 28),
        LocalDate(2026, 12, 22),
    )

    /*
     * Vanguard는 2027~2040 일정을 아직 발표하지 않았다. 미래 날짜는 2026 공식 월내
     * 요일 순번을 보존하고 NYSE Arca 휴장일이면 직전 거래일로 이동하는 버전된 가정이다.
     */
}
