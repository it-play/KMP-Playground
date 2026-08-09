package com.amond.kmpbook.domain.model.listing.termination

import com.amond.kmpbook.domain.model.event.GameEvent
import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.listing.termination.InstrumentTerminationDecision
import com.amond.kmpbook.domain.model.listing.termination.InstrumentTerminationTerms
import com.amond.kmpbook.domain.model.listing.termination.PublishedInstrumentTerminationNotice
import com.amond.kmpbook.domain.model.listing.termination.rawEffectiveTradingDate
import com.amond.kmpbook.domain.model.listing.termination.resolveInstrumentTerminationAtSessionClose
import com.amond.kmpbook.domain.model.listing.termination.resolvePublishedInstrumentTerminationNotice
import com.amond.kmpbook.domain.model.listing.termination.scheduledTerminationOn
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.time.DefaultMarketHolidays
import com.amond.kmpbook.domain.time.GameCalendar
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * 상장 감시가 이 공시를 처음 채택한 [evaluatedOn]을 기준으로 실제 종료 예정일을 확정한다.
 * 늦게 관측된 공시는 과거로 소급하지 않고, ETN의 계약 만기는 더 늦은 선택적 콜보다 항상
 * hard cap으로 우선한다. Runtime·저장 검증·뉴스 화면이 모두 같은 계산을 사용한다.
 */
fun PublishedInstrumentTerminationNotice.scheduledTerminationOn(
    stock: StockDefinition,
    evaluatedOn: LocalDate,
): LocalDate {
    val contractualHardCap = stock.identityProfile?.maturityDate
        ?.let(LocalDate::parse)
        ?.let { maturity -> firstTerminationTradingDateOnOrAfter(stock.market, maturity) }
    return maxOf(evaluatedOn, minOf(rawEffectiveOn, contractualHardCap ?: rawEffectiveOn))
}

/**
 * 공시 조건이 처음 효력을 가질 수 있는 주 상장시장 정규장 종가의 현지 거래일을 계산한다.
 * 계약일·최소 효력 시각을 달력 날짜로 단순 변환하지 않고 캠페인의 거래소 휴장일을 동일하게 반영한다.
 */
fun InstrumentTerminationTerms.rawEffectiveTradingDate(stock: StockDefinition): LocalDate {
    contractualDate?.let { contractual ->
        return firstTerminationTradingDateOnOrAfter(stock.market, contractual)
    }

    val notBefore = requireNotNull(effectiveNotBefore)
    var candidate = GameCalendar.marketLocalDateTime(stock.market, notBefore).date
    repeat(MAX_TERMINATION_SESSION_SEARCH_DAYS) {
        val session = GameCalendar.regularSessionWindow(
            market = stock.market,
            localDate = candidate,
            closedDates = terminationClosedDates(stock.market, candidate),
        )
        if (session != null && notBefore <= session.closesAt) return candidate
        candidate = candidate.plus(1, DateTimeUnit.DAY)
    }
    error("상품 종료 최소 효력 시각 $notBefore 이후의 거래소 종가를 찾을 수 없습니다.")
}

/**
 * [publishedAt]까지 실제 발표된, [stock]을 직접 종료할 수 있는 공시 중 하나의 winner를 고른다.
 * 비교 규칙은 상장 감시와 UI에서 공유하며 표시 문자열이나 이벤트 ID 형식에 의존하지 않는다.
 * 기존 원장 지배 공시가 있으면 더 이른 원효력일 또는 같은 날의 더 낮은 계약 우선순위만
 * 이를 교체할 수 있다. 같은 날짜·우선순위의 표시상 tie-break는 확정된 계약 계보를 바꾸지 않는다.
 */
fun resolvePublishedInstrumentTerminationNotice(
    stock: StockDefinition,
    events: Iterable<GameEvent>,
    publishedAt: Instant,
    incumbentOccurrenceId: String? = null,
): PublishedInstrumentTerminationNotice? {
    val publishedNotices = events.asSequence()
        .filter { event -> event.startsAt <= publishedAt && stock.id in event.affectedStockIds }
        .mapNotNull { event ->
            val terms = event.instrumentTermination
                ?.takeIf { termination -> termination.isEligibleFor(stock) }
                ?: return@mapNotNull null
            PublishedInstrumentTerminationNotice(
                event = event,
                terms = terms,
                rawEffectiveOn = terms.rawEffectiveTradingDate(stock),
            )
        }
        .toList()
    val rankedWinner = publishedNotices.minWithOrNull(PUBLISHED_TERMINATION_NOTICE_ORDER)
    if (incumbentOccurrenceId == null) return rankedWinner

    val incumbent = requireNotNull(
        publishedNotices.singleOrNull { notice -> notice.event.id == incumbentOccurrenceId },
    ) {
        "현재 상품 종료 상태의 지배 공시 $incumbentOccurrenceId 를 발표된 뉴스 원장에서 찾을 수 없습니다."
    }
    return rankedWinner?.takeIf { candidate ->
        candidate.rawEffectiveOn < incumbent.rawEffectiveOn ||
            candidate.rawEffectiveOn == incumbent.rawEffectiveOn &&
            candidate.terms.kind.noticePriority < incumbent.terms.kind.noticePriority
    } ?: incumbent
}

/**
 * [evaluatedOn] 거래일의 정규장 마감 직전까지 실제 발표된 공시만으로 상품 종료를 결정한다.
 *
 * 마감 시각에 발표된 공시는 해당 거래일 감시에 소급 적용하지 않는다. 공시 winner를 고르는
 * 규칙과 휴장일·계약 만기 hard cap을 적용한 실제 종료 예정일 계산을 이 경계에서 한 번만
 * 수행하므로, 호출자는 [InstrumentTerminationDecision.notice]의 조건과 같은 공시에서 파생된
 * 날짜만 소비하게 된다.
 */
fun resolveInstrumentTerminationAtSessionClose(
    stock: StockDefinition,
    events: Iterable<GameEvent>,
    evaluatedOn: LocalDate,
    incumbentOccurrenceId: String? = null,
): InstrumentTerminationDecision? {
    val session = requireNotNull(
        GameCalendar.regularSessionWindow(
            market = stock.market,
            localDate = evaluatedOn,
            closedDates = terminationClosedDates(stock.market, evaluatedOn),
        ),
    ) {
        "$evaluatedOn 은 ${stock.market} 정규장 거래일이 아닙니다."
    }
    val notice = resolvePublishedInstrumentTerminationNotice(
        stock = stock,
        events = events,
        publishedAt = session.closesAt - 1.nanoseconds,
        incumbentOccurrenceId = incumbentOccurrenceId,
    ) ?: return null
    return InstrumentTerminationDecision(
        notice = notice,
        scheduledTerminationOn = notice.scheduledTerminationOn(stock, evaluatedOn),
    )
}

private val PUBLISHED_TERMINATION_NOTICE_ORDER =
    compareBy<PublishedInstrumentTerminationNotice>(PublishedInstrumentTerminationNotice::rawEffectiveOn)
        .thenBy { notice -> notice.terms.kind.noticePriority }
        .thenByDescending { notice -> notice.event.severity.level }
        .thenBy { notice -> notice.event.startsAt }
        .thenBy { notice -> notice.event.id }

private fun firstTerminationTradingDateOnOrAfter(market: Market, date: LocalDate): LocalDate {
    var candidate = date
    repeat(MAX_TERMINATION_SESSION_SEARCH_DAYS) {
        if (GameCalendar.regularSessionWindow(
                market = market,
                localDate = candidate,
                closedDates = terminationClosedDates(market, candidate),
            ) != null
        ) {
            return candidate
        }
        candidate = candidate.plus(1, DateTimeUnit.DAY)
    }
    error("상품 종료 계약일 $date 이후의 거래소 거래일을 찾을 수 없습니다.")
}

/** 마지막 미국장·법정 결제 꼬리가 2041년 초까지 이어지는 현재 캠페인 달력 규칙이다. */
private fun terminationClosedDates(market: Market, date: LocalDate): Set<LocalDate> = when {
    date.year in GameCalendar.START_LOCAL_DATE_TIME.year..GameCalendar.CAMPAIGN_END_DATE.year ->
        DefaultMarketHolidays.closedDates(market, date.year)
    date == LocalDate(GameCalendar.CAMPAIGN_END_DATE.year + 1, 1, 1) -> setOf(date)
    else -> emptySet()
}

private const val MAX_TERMINATION_SESSION_SEARCH_DAYS = 370
