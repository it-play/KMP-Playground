package com.amond.kmpbook.domain.time

import com.amond.kmpbook.domain.model.game.TurnStep
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.venue.MarketSession
import com.amond.kmpbook.domain.model.venue.MarketVenueProfiles
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

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
 * 주입할 수 있어 엔진과 테스트가 외부 상태 없이 동일한 결과를 얻는다. 미국 정규장은
 * 공통 09:30-16:00이고 확장 세션 표시는 [MarketVenueProfiles]의 venue 규칙을 따른다.
 */
object GameCalendar {
    val KOREA_TIME_ZONE: TimeZone = TimeZone.of("Asia/Seoul")
    val NEW_YORK_TIME_ZONE: TimeZone = TimeZone.of("America/New_York")

    val START_LOCAL_DATE_TIME: LocalDateTime = LocalDateTime(2026, 8, 7, 9, 0)
    val CAMPAIGN_END_DATE: LocalDate = LocalDate(2040, 12, 31)
    private val END_NEW_YORK_DATE_TIME: LocalDateTime = LocalDateTime(CAMPAIGN_END_DATE, LocalTime(16, 0))
    val startInstant: Instant = START_LOCAL_DATE_TIME.toInstant(KOREA_TIME_ZONE)
    /** 모든 지원 거래소의 2040-12-31 정규장이 끝나는 뉴욕장 마감 시각. */
    val endInstant: Instant = END_NEW_YORK_DATE_TIME.toInstant(NEW_YORK_TIME_ZONE)
    /** 실제 KST 표시는 2041-01-01 06:00이지만 캠페인 귀속일은 2040-12-31이다. */
    val END_LOCAL_DATE_TIME: LocalDateTime = endInstant.toLocalDateTime(KOREA_TIME_ZONE)

    /** 짧은 이름이 필요한 엔진 호출부용 별칭. */
    val start: Instant get() = startInstant
    val end: Instant get() = endInstant

    private val krxOpen: LocalTime = LocalTime(9, 0)
    private val krxClose: LocalTime = LocalTime(15, 30)
    private val usRegularOpen: LocalTime = LocalTime(9, 30)
    private val usRegularClose: LocalTime = LocalTime(16, 0)

    fun timeZoneFor(market: Market): TimeZone = when {
        market.isKorean -> KOREA_TIME_ZONE
        else -> NEW_YORK_TIME_ZONE
    }

    /** 유효 게임 구간으로 시각을 제한한다. */
    fun clamp(time: Instant): Instant = time.coerceIn(startInstant, endInstant)

    /** 선택한 단위만큼 실제 시간을 진행하되 마지막 지원 거래소의 2040-12-31 마감을 넘지 않는다. */
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

    /** KST 자정 뒤 이어지는 마지막 미국장도 2040-12-31 캠페인 날짜로 귀속한다. */
    fun campaignDate(time: Instant): LocalDate =
        time.toLocalDateTime(KOREA_TIME_ZONE).date.coerceAtMost(CAMPAIGN_END_DATE)

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

        if (market.isKorean) {
            return if (local.time.isInHalfOpenRange(krxOpen, krxClose)) {
                MarketSession.REGULAR
            } else {
                MarketSession.CLOSED
            }
        }

        val venue = MarketVenueProfiles.forMarket(market)
        val preMarketOpen = venue.preMarketOpensAt
        val afterHoursClose = venue.afterHoursClosesAt
        return when {
            preMarketOpen != null && local.time.isInHalfOpenRange(preMarketOpen, usRegularOpen) ->
                MarketSession.PRE_MARKET

            local.time.isInHalfOpenRange(usRegularOpen, usRegularClose) -> MarketSession.REGULAR
            afterHoursClose != null && local.time.isInHalfOpenRange(usRegularClose, afterHoursClose) ->
                MarketSession.AFTER_HOURS

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
