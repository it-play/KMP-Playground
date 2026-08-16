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

/** Vanguard S&P 500 ETF(VOO)의 동결 시나리오 ex-date 달력이다. */
internal object VanguardVooDistributionCalendar {
    fun isDistributionDate(
        date: LocalDate,
        frequency: DistributionFrequency,
    ): Boolean {
        if (frequency != DistributionFrequency.QUARTERLY) return false
        if (date.year !in SUPPORTED_YEARS) return false
        if (date.year == OFFICIAL_SCHEDULE_YEAR) return date in OFFICIAL_2026_EX_DATES

        return date == projectedExDate(date.year, date.month)
    }

    private fun projectedExDate(year: Int, month: Month): LocalDate? {
        val ordinalAnchor = when (month) {
            Month.MARCH -> nthWeekdayOfMonth(year, month, DayOfWeek.FRIDAY, occurrence = 4)
            Month.JUNE -> nthWeekdayOfMonth(year, month, DayOfWeek.FRIDAY, occurrence = 4)
            Month.SEPTEMBER -> nthWeekdayOfMonth(year, month, DayOfWeek.MONDAY, occurrence = 4)
            Month.DECEMBER -> nthWeekdayOfMonth(year, month, DayOfWeek.MONDAY, occurrence = 3)
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

    /** Vanguard가 공표한 2026 VOO ex-dividend dates. */
    private val OFFICIAL_2026_EX_DATES: Set<LocalDate> = setOf(
        LocalDate(2026, 3, 27),
        LocalDate(2026, 6, 26),
        LocalDate(2026, 9, 28),
        LocalDate(2026, 12, 21),
    )

    /*
     * Vanguard는 2027~2040의 공식 일정을 아직 공표하지 않았다. 미래 날짜는 2026 공식
     * 날짜의 월내 요일 순번(3·6월 넷째 금요일, 9월 넷째 월요일, 12월 셋째 월요일)을
     * 보존하고, NYSE Arca 휴장일과 겹치면 직전 영업일로 옮기는 결정론적 모델 가정이다.
     */
}
