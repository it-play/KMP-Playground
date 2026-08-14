package com.amond.kmpbook.domain.time

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/** KSD의 KOFR 산출·공표 업무일을 KRX 거래일과 분리한 frozen-scenario 달력이다. */
object KofrBusinessCalendar {
    fun isBusinessDate(date: LocalDate): Boolean {
        if (GameCalendar.isWeekend(date)) return false
        return when (date.year) {
            in GameCalendar.START_LOCAL_DATE_TIME.year..GameCalendar.CAMPAIGN_END_DATE.year ->
                date !in DefaultMarketHolidays.koreanFinancialClosedDates(date.year)
            GameCalendar.CAMPAIGN_END_DATE.year + 1 -> date != LocalDate(date.year, 1, 1)
            else -> error("KOFR 영업일 달력 범위를 벗어났습니다: $date")
        }
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
}
