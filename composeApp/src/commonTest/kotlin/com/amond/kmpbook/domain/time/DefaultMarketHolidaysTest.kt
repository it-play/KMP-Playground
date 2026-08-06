package com.amond.kmpbook.domain.time

import com.amond.kmpbook.domain.model.Market
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultMarketHolidaysTest {
    @Test
    fun krxIncludesLunarHolidayAndYearEndClosure() {
        val dates = DefaultMarketHolidays.closedDates(Market.KOSPI, 2026)
        assertTrue(LocalDate(2026, 2, 17) in dates)
        assertTrue(LocalDate(2026, 9, 25) in dates)
        assertTrue(LocalDate(2026, 12, 31) in dates)
    }

    @Test
    fun usObservedHolidayAndGoodFridayAreClosed() {
        val dates = DefaultMarketHolidays.closedDates(Market.NASDAQ, 2027)
        assertTrue(LocalDate(2027, 7, 5) in dates)
        assertTrue(LocalDate(2027, 3, 26) in dates)
        assertFalse(LocalDate(2027, 7, 6) in dates)
    }

    @Test
    fun bothVenuesShareCountryCalendar() {
        assertTrue(
            DefaultMarketHolidays.closedDates(Market.KOSPI, 2030) ==
                DefaultMarketHolidays.closedDates(Market.KOSDAQ, 2030),
        )
        assertTrue(
            DefaultMarketHolidays.closedDates(Market.NASDAQ, 2030) ==
                DefaultMarketHolidays.closedDates(Market.NYSE, 2030),
        )
    }

    @Test
    fun wholeHourClockPreservesHalfHourMarketBoundaries() {
        val krxFinalHour = LocalDateTime(2026, 8, 7, 15, 0)
            .toInstant(TimeZone.of("Asia/Seoul"))
        val usOpeningHour = LocalDateTime(2026, 8, 7, 9, 0)
            .toInstant(TimeZone.of("America/New_York"))
        assertTrue(GameCalendar.regularTradingFraction(Market.KOSPI, krxFinalHour) == 0.5)
        assertTrue(GameCalendar.regularTradingFraction(Market.NASDAQ, usOpeningHour) == 0.5)
    }
}
