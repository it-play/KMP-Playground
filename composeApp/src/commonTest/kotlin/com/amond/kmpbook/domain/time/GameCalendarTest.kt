package com.amond.kmpbook.domain.time

import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.MarketSession
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class GameCalendarTest {
    private val usMarkets = listOf(
        Market.NASDAQ,
        Market.NYSE,
        Market.NYSE_ARCA,
        Market.NYSE_AMERICAN,
    )

    @Test
    fun allUsVenuesKeepTheCommonCoreSession() {
        val date = LocalDate(2026, 7, 7)

        for (market in usMarkets) {
            assertEquals(MarketSession.REGULAR, GameCalendar.marketSession(market, atNewYork(date, 9, 30)))
            assertEquals(MarketSession.REGULAR, GameCalendar.marketSession(market, atNewYork(date, 15, 59)))
            assertEquals(0.5, GameCalendar.regularTradingFraction(market, atNewYork(date, 9, 0)))
            assertEquals(1.0, GameCalendar.regularTradingFraction(market, atNewYork(date, 10, 0)))
        }
    }

    @Test
    fun venueSpecificExtendedSessionsAreShownWithoutMakingThemTradable() {
        val date = LocalDate(2026, 7, 7)
        val fourThirty = atNewYork(date, 4, 30)
        val sevenThirty = atNewYork(date, 7, 30)
        val afterClose = atNewYork(date, 17, 0)

        assertEquals(MarketSession.PRE_MARKET, GameCalendar.marketSession(Market.NASDAQ, fourThirty))
        assertEquals(MarketSession.PRE_MARKET, GameCalendar.marketSession(Market.NYSE_ARCA, fourThirty))
        assertEquals(MarketSession.CLOSED, GameCalendar.marketSession(Market.NYSE_AMERICAN, fourThirty))
        assertEquals(MarketSession.CLOSED, GameCalendar.marketSession(Market.NYSE, fourThirty))

        assertEquals(MarketSession.PRE_MARKET, GameCalendar.marketSession(Market.NASDAQ, sevenThirty))
        assertEquals(MarketSession.PRE_MARKET, GameCalendar.marketSession(Market.NYSE_ARCA, sevenThirty))
        assertEquals(MarketSession.PRE_MARKET, GameCalendar.marketSession(Market.NYSE_AMERICAN, sevenThirty))
        assertEquals(MarketSession.CLOSED, GameCalendar.marketSession(Market.NYSE, sevenThirty))

        assertEquals(MarketSession.AFTER_HOURS, GameCalendar.marketSession(Market.NASDAQ, afterClose))
        assertEquals(MarketSession.AFTER_HOURS, GameCalendar.marketSession(Market.NYSE_ARCA, afterClose))
        assertEquals(MarketSession.AFTER_HOURS, GameCalendar.marketSession(Market.NYSE_AMERICAN, afterClose))
        assertEquals(MarketSession.CLOSED, GameCalendar.marketSession(Market.NYSE, afterClose))

        assertEquals(MarketSession.CLOSED, GameCalendar.marketSession(Market.NASDAQ, atNewYork(date, 20, 0)))
    }

    @Test
    fun newYorkDstStillMovesTheCommonCoreByOneUtcHour() {
        val summer = GameCalendar.regularSessionWindow(Market.NASDAQ, LocalDate(2026, 7, 7))!!
        val winter = GameCalendar.regularSessionWindow(Market.NASDAQ, LocalDate(2027, 1, 5))!!

        assertEquals(Instant.parse("2026-07-07T13:30:00Z"), summer.opensAt)
        assertEquals(Instant.parse("2026-07-07T20:00:00Z"), summer.closesAt)
        assertEquals(Instant.parse("2027-01-05T14:30:00Z"), winter.opensAt)
        assertEquals(Instant.parse("2027-01-05T21:00:00Z"), winter.closesAt)
    }

    @Test
    fun campaignEndsOnlyAfterEveryMarketsDecemberThirtyFirstSession() {
        assertEquals(Instant.parse("2040-12-31T21:00:00Z"), GameCalendar.endInstant)
        assertEquals(
            LocalDateTime(2040, 12, 31, 16, 0),
            GameCalendar.endInstant.toLocalDateTime(GameCalendar.NEW_YORK_TIME_ZONE),
        )
        assertEquals(
            LocalDateTime(2041, 1, 1, 6, 0),
            GameCalendar.END_LOCAL_DATE_TIME,
        )
        assertEquals(LocalDate(2040, 12, 31), GameCalendar.campaignDate(GameCalendar.endInstant))
    }

    private fun atNewYork(date: LocalDate, hour: Int, minute: Int): Instant =
        LocalDateTime(date, LocalTime(hour, minute)).toInstant(GameCalendar.NEW_YORK_TIME_ZONE)
}
