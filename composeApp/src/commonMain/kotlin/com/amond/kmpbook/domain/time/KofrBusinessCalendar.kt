package com.amond.kmpbook.domain.time

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/** KSD의 KOFR 산출·공표 업무일을 KRX 거래일과 분리한 계산형 달력이다. */
object KofrBusinessCalendar {
    fun isBusinessDate(date: LocalDate): Boolean {
        if (GameCalendar.isWeekend(date)) return false
        require(date.year in SUPPORTED_YEARS) { "KOFR 영업일 달력 범위를 벗어났습니다: $date" }
        return date !in DefaultMarketHolidays.koreanFinancialClosedDatesForCalendarYear(date.year)
    }

    fun previousBusinessDate(date: LocalDate): LocalDate {
        var candidate = date.minus(1, DateTimeUnit.DAY)
        while (!isBusinessDate(candidate)) candidate = candidate.minus(1, DateTimeUnit.DAY)
        return candidate
    }

    fun latestBusinessDateOnOrBefore(date: LocalDate): LocalDate {
        var candidate = date
        while (!isBusinessDate(candidate)) candidate = candidate.minus(1, DateTimeUnit.DAY)
        return candidate
    }

    private val SUPPORTED_YEARS: IntRange =
        (GameCalendar.START_LOCAL_DATE_TIME.year - 1)..(GameCalendar.CAMPAIGN_END_DATE.year + 1)
}
