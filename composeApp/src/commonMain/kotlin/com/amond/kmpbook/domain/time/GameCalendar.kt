package com.amond.kmpbook.domain.time

import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.MarketSession
import com.amond.kmpbook.domain.model.TurnStep
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

data class MarketSessionWindow(
    val market: Market,
    val localDate: LocalDate,
    val opensAt: Instant,
    val closesAt: Instant,
) {
    init {
        require(closesAt > opensAt) { "시장 종료 시각은 개장 시각보다 뒤여야 합니다." }
    }
}

/**
 * 게임 시간과 거래소 정규장을 계산하는 상태 없는 달력.
 *
 * 게임 기준 시각은 KST이며, 미국 시장 판단은 America/New_York 변환 결과를 사용하므로
 * 서머타임(EDT/EST) 전환이 자동 반영된다. 거래소별 임시 휴장일은 [closedDates] 인자로
 * 주입할 수 있어 엔진과 테스트가 외부 상태 없이 동일한 결과를 얻는다.
 */
object GameCalendar {
    val KOREA_TIME_ZONE: TimeZone = TimeZone.of("Asia/Seoul")
    val NEW_YORK_TIME_ZONE: TimeZone = TimeZone.of("America/New_York")

    val START_LOCAL_DATE_TIME: LocalDateTime = LocalDateTime(2026, 8, 7, 9, 0)
    val END_LOCAL_DATE_TIME: LocalDateTime = LocalDateTime(2040, 12, 31, 23, 0)

    val startInstant: Instant = START_LOCAL_DATE_TIME.toInstant(KOREA_TIME_ZONE)
    val endInstant: Instant = END_LOCAL_DATE_TIME.toInstant(KOREA_TIME_ZONE)

    /** 짧은 이름이 필요한 엔진 호출부용 별칭. */
    val start: Instant get() = startInstant
    val end: Instant get() = endInstant

    private val krxOpen: LocalTime = LocalTime(9, 0)
    private val krxClose: LocalTime = LocalTime(15, 30)
    private val usPreMarketOpen: LocalTime = LocalTime(4, 0)
    private val usRegularOpen: LocalTime = LocalTime(9, 30)
    private val usRegularClose: LocalTime = LocalTime(16, 0)
    private val usAfterHoursClose: LocalTime = LocalTime(20, 0)

    fun timeZoneFor(market: Market): TimeZone = when {
        market.isKorean -> KOREA_TIME_ZONE
        else -> NEW_YORK_TIME_ZONE
    }

    /** 유효 게임 구간으로 시각을 제한한다. */
    fun clamp(time: Instant): Instant = time.coerceIn(startInstant, endInstant)

    /** 선택한 단위만큼 실제 시간을 진행하되 2040-12-31 23:00 KST를 넘지 않는다. */
    fun advance(from: Instant, step: TurnStep): Instant = advanceHours(from, step.hours)

    fun advanceHours(from: Instant, hours: Int): Instant {
        require(hours >= 0) { "진행 시간은 음수일 수 없습니다." }
        val normalized = clamp(from)
        if (normalized >= endInstant) return endInstant
        return (normalized + hours.hours).coerceAtMost(endInstant)
    }

    fun isWithinGameRange(time: Instant): Boolean = time in startInstant..endInstant

    fun isFinished(time: Instant): Boolean = time >= endInstant

    fun turnAt(time: Instant): Long = (clamp(time) - startInstant).inWholeHours

    fun remainingHours(time: Instant): Long = (endInstant - clamp(time)).inWholeHours

    fun progress(time: Instant): Double {
        val totalHours = (endInstant - startInstant).inWholeHours.toDouble()
        return if (totalHours == 0.0) 1.0 else turnAt(time) / totalHours
    }

    fun toGameLocalDateTime(time: Instant): LocalDateTime = time.toLocalDateTime(KOREA_TIME_ZONE)

    fun fromGameLocalDateTime(localDateTime: LocalDateTime): Instant = localDateTime.toInstant(KOREA_TIME_ZONE)

    fun marketLocalDateTime(market: Market, time: Instant): LocalDateTime =
        time.toLocalDateTime(timeZoneFor(market))

    fun isWeekend(date: LocalDate): Boolean =
        date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY

    fun isWeekend(time: Instant, timeZone: TimeZone = KOREA_TIME_ZONE): Boolean =
        isWeekend(time.toLocalDateTime(timeZone).date)

    fun isTradingDay(
        market: Market,
        time: Instant,
        closedDates: Set<LocalDate> = emptySet(),
    ): Boolean {
        val localDate = marketLocalDateTime(market, time).date
        return !isWeekend(localDate) && localDate !in closedDates
    }

    /**
     * KRX는 09:00 이상 15:30 미만, 미국은 현지 09:30 이상 16:00 미만을 정규장으로 본다.
     * 휴장일 집합은 각 시장 현지 날짜로 전달한다.
     */
    fun isRegularMarketOpen(
        market: Market,
        time: Instant,
        closedDates: Set<LocalDate> = emptySet(),
    ): Boolean = marketSession(market, time, closedDates) == MarketSession.REGULAR

    /**
     * Fraction of the following wall-clock hour covered by the regular session.
     * This preserves KRX's 15:30 close and the U.S. 09:30 open while the game
     * clock itself advances only on whole hours.
     */
    fun regularTradingFraction(
        market: Market,
        hourStart: Instant,
        closedDates: Set<LocalDate> = emptySet(),
    ): Double {
        val local = marketLocalDateTime(market, hourStart)
        if (isWeekend(local.date) || local.date in closedDates) return 0.0
        return if (market.isKorean) {
            when {
                local.time >= LocalTime(9, 0) && local.time < LocalTime(15, 0) -> 1.0
                local.time >= LocalTime(15, 0) && local.time < LocalTime(15, 30) -> 0.5
                else -> 0.0
            }
        } else {
            when {
                local.time >= LocalTime(9, 0) && local.time < LocalTime(10, 0) -> 0.5
                local.time >= LocalTime(10, 0) && local.time < LocalTime(16, 0) -> 1.0
                else -> 0.0
            }
        }
    }

    fun marketSession(
        market: Market,
        time: Instant,
        closedDates: Set<LocalDate> = emptySet(),
    ): MarketSession {
        val local = marketLocalDateTime(market, time)
        if (isWeekend(local.date) || local.date in closedDates) return MarketSession.CLOSED

        return when {
            market.isKorean && local.time.isInHalfOpenRange(krxOpen, krxClose) -> MarketSession.REGULAR
            market.isKorean -> MarketSession.CLOSED
            local.time.isInHalfOpenRange(usPreMarketOpen, usRegularOpen) -> MarketSession.PRE_MARKET
            local.time.isInHalfOpenRange(usRegularOpen, usRegularClose) -> MarketSession.REGULAR
            local.time.isInHalfOpenRange(usRegularClose, usAfterHoursClose) -> MarketSession.AFTER_HOURS
            else -> MarketSession.CLOSED
        }
    }

    fun openMarkets(
        time: Instant,
        closedDatesByMarket: Map<Market, Set<LocalDate>> = emptyMap(),
    ): Set<Market> = Market.entries
        .filterTo(mutableSetOf()) { market ->
            isRegularMarketOpen(market, time, closedDatesByMarket[market].orEmpty())
        }

    /** 현지 날짜의 정규장 UTC 구간. 주말 또는 주입된 휴장일이면 null이다. */
    fun regularSessionWindow(
        market: Market,
        localDate: LocalDate,
        closedDates: Set<LocalDate> = emptySet(),
    ): MarketSessionWindow? {
        if (isWeekend(localDate) || localDate in closedDates) return null
        val zone = timeZoneFor(market)
        val openTime = if (market.isKorean) krxOpen else usRegularOpen
        val closeTime = if (market.isKorean) krxClose else usRegularClose
        return MarketSessionWindow(
            market = market,
            localDate = localDate,
            opensAt = LocalDateTime(localDate, openTime).toInstant(zone),
            closesAt = LocalDateTime(localDate, closeTime).toInstant(zone),
        )
    }

    private fun LocalTime.isInHalfOpenRange(start: LocalTime, end: LocalTime): Boolean =
        this >= start && this < end
}
