package com.amond.kmpbook.domain.time

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** NYSE 계열 미국 주식시장의 전일 휴장일과 정규장·확장 세션 조기폐장을 계산한다. */
internal object NyseHolidayCalendar {
    fun closedDates(year: Int): Set<LocalDate> = buildSet {
        observedNewYearsDay(LocalDate(year, 1, 1))?.let(::add)
        if (year == 2025) {
            // NYSE Regulatory Memo 2025-1: https://www.nyse.com/publicdocs/nyse/markets/
            // american-options/rule-interpretations/2025/National_Day_of_Mourning_20250102.pdf
            add(LocalDate(2025, 1, 9))
        }
        add(nthWeekday(year, 1, DayOfWeek.MONDAY, 3))
        add(nthWeekday(year, 2, DayOfWeek.MONDAY, 3))
        add(easterSunday(year).minus(2, DateTimeUnit.DAY))
        add(lastWeekday(year, 5, DayOfWeek.MONDAY))
        add(observedFixedHoliday(LocalDate(year, 6, 19)))
        add(observedFixedHoliday(LocalDate(year, 7, 4)))
        add(nthWeekday(year, 9, DayOfWeek.MONDAY, 1))
        add(nthWeekday(year, 11, DayOfWeek.THURSDAY, 4))
        add(observedFixedHoliday(LocalDate(year, 12, 25)))
    }

    /**
     * NYSE의 공표된 반복 규칙에 따라 13:00 ET에 정규장이 끝나는 날짜를 산출한다.
     * 후보일이 주말이거나 관측 공휴일이면 다른 날짜로 이동시키지 않고 제외한다.
     */
    fun earlyCloseDates(year: Int): Set<LocalDate> {
        require(year in SUPPORTED_YEARS) { "NYSE 조기폐장 달력은 2025~2041년을 지원합니다." }
        return EARLY_CLOSE_DATES_BY_YEAR.getValue(year)
    }

    fun regularSessionClose(date: LocalDate): LocalTime =
        if (date.year in SUPPORTED_YEARS && date in earlyCloseDates(date.year)) {
            EARLY_CLOSE
        } else {
            FULL_DAY_CLOSE
        }

    /**
     * 조기폐장일의 미국 주식 late/extended session은 17:00 ET에 끝난다.
     * 확장 세션을 제공하지 않는 venue의 null 계약은 그대로 보존한다.
     */
    fun extendedSessionClose(date: LocalDate, ordinaryClose: LocalTime?): LocalTime? =
        ordinaryClose?.let { close ->
            if (regularSessionClose(date) == EARLY_CLOSE) minOf(close, EARLY_EXTENDED_CLOSE)
            else close
        }

    private fun computeEarlyCloseDates(year: Int): Set<LocalDate> {
        val fullDayClosures = closedDates(year)
        val candidates = setOf(
            LocalDate(year, 7, 3),
            nthWeekday(year, 11, DayOfWeek.THURSDAY, 4).plus(1, DateTimeUnit.DAY),
            LocalDate(year, 12, 24),
        )
        return candidates.filterTo(linkedSetOf()) { date ->
            date.dayOfWeek != DayOfWeek.SATURDAY &&
                date.dayOfWeek != DayOfWeek.SUNDAY &&
                date !in fullDayClosures
        }
    }

    /** NYSE는 1월 1일이 토요일이면 전년도 12월 31일을 대체 휴장하지 않는다. */
    private fun observedNewYearsDay(date: LocalDate): LocalDate? = when (date.dayOfWeek) {
        DayOfWeek.SATURDAY -> null
        DayOfWeek.SUNDAY -> date.plus(1, DateTimeUnit.DAY)
        else -> date
    }

    private fun observedFixedHoliday(date: LocalDate): LocalDate = when (date.dayOfWeek) {
        DayOfWeek.SATURDAY -> date.minus(1, DateTimeUnit.DAY)
        DayOfWeek.SUNDAY -> date.plus(1, DateTimeUnit.DAY)
        else -> date
    }

    private fun nthWeekday(
        year: Int,
        month: Int,
        weekday: DayOfWeek,
        occurrence: Int,
    ): LocalDate {
        var date = LocalDate(year, month, 1)
        while (date.dayOfWeek != weekday) date = date.plus(1, DateTimeUnit.DAY)
        return date.plus((occurrence - 1) * 7, DateTimeUnit.DAY)
    }

    private fun lastWeekday(year: Int, month: Int, weekday: DayOfWeek): LocalDate {
        var date = if (month == 12) LocalDate(year + 1, 1, 1) else LocalDate(year, month + 1, 1)
        date = date.minus(1, DateTimeUnit.DAY)
        while (date.dayOfWeek != weekday) date = date.minus(1, DateTimeUnit.DAY)
        return date
    }

    /** Anonymous Gregorian computus, returned as Gregorian Easter Sunday. */
    private fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate(year, month, day)
    }

    private val SUPPORTED_YEARS: IntRange = 2025..2041
    private val EARLY_CLOSE: LocalTime = LocalTime(13, 0)
    private val EARLY_EXTENDED_CLOSE: LocalTime = LocalTime(17, 0)
    private val FULL_DAY_CLOSE: LocalTime = LocalTime(16, 0)
    private val EARLY_CLOSE_DATES_BY_YEAR: Map<Int, Set<LocalDate>> =
        SUPPORTED_YEARS.associateWith(::computeEarlyCloseDates)
}
