package com.amond.kmpbook.domain.model

import com.amond.kmpbook.domain.time.DefaultMarketHolidays
import com.amond.kmpbook.domain.time.GameCalendar
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant

/** 상품 종료 공시가 선언하는 법적·계약상 종료 사유다. */
enum class InstrumentTerminationKind(
    /** 같은 효력일 공시가 겹칠 때 적용하는 계약 우선순위다. 낮을수록 우선한다. */
    val noticePriority: Int,
) {
    CONTRACTUAL_MATURITY(0),
    ISSUER_ACCELERATION(1),
    OPTIONAL_CALL(2),
    FUND_LIQUIDATION(3),
}

/** 종료일 현금 지급 단가를 산정하는 캠페인 평가 규칙이다. */
enum class InstrumentTerminationValuationMethod {
    /** ETN의 종료일 최종 지표가치를 종가로 근사한다. */
    FINAL_INDICATIVE_VALUE_PROXY,

    /** ETF·폐쇄형 펀드의 최종 순자산가치를 종가로 근사한다. */
    FINAL_NET_ASSET_VALUE_PROXY,

    /** 최근 5개 거래일 종가 평균에 공시에 고정된 발행사 회수율을 적용한다. */
    TRAILING_FIVE_SESSION_AVERAGE_WITH_RECOVERY,
}

/**
 * 하나의 상품 종료 공시에 고정되는 불변 계약 조건이다.
 *
 * 계약상 만기는 [contractualDate]를, 나머지 종료 사유는 [effectiveNotBefore]를 사용한다.
 * 발행사 가속상환의 [accelerationRecoveryRate]는 이벤트 생성 시 한 번 결정해 저장하므로
 * 저장·재개 이후에도 효력일, 우선순위, 지급 단가가 같은 공시에서 파생된다.
 */
data class InstrumentTerminationTerms(
    val kind: InstrumentTerminationKind,
    val contractualDate: LocalDate? = null,
    val effectiveNotBefore: Instant? = null,
    val valuationMethod: InstrumentTerminationValuationMethod,
    val accelerationRecoveryRate: Double? = null,
) {
    init {
        val violation = semanticInvariantViolation()
        require(violation == null) { requireNotNull(violation) }
    }

    /** Gson처럼 생성자를 우회하는 저장 복원 경계에서도 재사용할 의미 검증이다. */
    fun semanticInvariantViolation(): String? {
        val hasContractualDate = contractualDate != null
        val hasNotBeforeInstant = effectiveNotBefore != null
        if (hasContractualDate == hasNotBeforeInstant) {
            return "상품 종료 조건에는 계약일 또는 최소 효력 시각 중 정확히 하나가 필요합니다."
        }
        if (accelerationRecoveryRate != null &&
            (!accelerationRecoveryRate.isFinite() || accelerationRecoveryRate !in 0.40..0.80)
        ) {
            return "발행사 가속상환 회수율은 40% 이상 80% 이하여야 합니다."
        }
        return when (kind) {
            InstrumentTerminationKind.CONTRACTUAL_MATURITY -> when {
                contractualDate == null -> "계약상 만기에는 정확한 계약일이 필요합니다."
                valuationMethod != InstrumentTerminationValuationMethod.FINAL_INDICATIVE_VALUE_PROXY ->
                    "계약상 만기는 종료일 최종 지표가치로 평가해야 합니다."
                accelerationRecoveryRate != null -> "계약상 만기에는 가속상환 회수율을 둘 수 없습니다."
                else -> null
            }
            InstrumentTerminationKind.ISSUER_ACCELERATION -> when {
                effectiveNotBefore == null -> "발행사 가속상환에는 최소 효력 시각이 필요합니다."
                valuationMethod !=
                    InstrumentTerminationValuationMethod.TRAILING_FIVE_SESSION_AVERAGE_WITH_RECOVERY ->
                    "발행사 가속상환에는 회수율을 반영한 5거래일 평가 방식이 필요합니다."
                accelerationRecoveryRate == null -> "발행사 가속상환에는 공시에 고정된 회수율이 필요합니다."
                else -> null
            }
            InstrumentTerminationKind.OPTIONAL_CALL -> when {
                effectiveNotBefore == null -> "선택적 조기상환에는 최소 효력 시각이 필요합니다."
                valuationMethod != InstrumentTerminationValuationMethod.FINAL_INDICATIVE_VALUE_PROXY ->
                    "선택적 조기상환은 종료일 최종 지표가치로 평가해야 합니다."
                accelerationRecoveryRate != null -> "선택적 조기상환에는 가속상환 회수율을 둘 수 없습니다."
                else -> null
            }
            InstrumentTerminationKind.FUND_LIQUIDATION -> when {
                effectiveNotBefore == null -> "펀드 청산에는 최소 효력 시각이 필요합니다."
                valuationMethod != InstrumentTerminationValuationMethod.FINAL_NET_ASSET_VALUE_PROXY ->
                    "펀드 청산은 종료일 최종 순자산가치로 평가해야 합니다."
                accelerationRecoveryRate != null -> "펀드 청산에는 가속상환 회수율을 둘 수 없습니다."
                else -> null
            }
        }
    }

    val listingRiskTag: ListingRiskTag
        get() = when (kind) {
            InstrumentTerminationKind.FUND_LIQUIDATION -> ListingRiskTag.ETF_LIQUIDATION_APPROVED
            else -> ListingRiskTag.ETN_MATURITY_OR_EARLY_REDEMPTION
        }

    val finalDisposition: ListingFinalDispositionType
        get() = ListingFinalDispositionType.CASH_LIQUIDATION

    /** ID나 제목이 아니라 상품 계약 속성으로 공시 적용 가능성을 판정한다. */
    fun isEligibleFor(stock: StockDefinition): Boolean = when (kind) {
        InstrumentTerminationKind.CONTRACTUAL_MATURITY ->
            stock.instrumentType == InstrumentType.ETN &&
                stock.identityProfile?.maturityDate == contractualDate.toString()
        InstrumentTerminationKind.ISSUER_ACCELERATION -> stock.instrumentType == InstrumentType.ETN
        InstrumentTerminationKind.OPTIONAL_CALL ->
            stock.instrumentType == InstrumentType.ETN && stock.identityProfile?.callable == true
        InstrumentTerminationKind.FUND_LIQUIDATION ->
            stock.instrumentType in setOf(InstrumentType.ETF, InstrumentType.CLOSED_END_FUND)
    }
}

/**
 * 발표된 상품 종료 공시를 상장 감시와 뉴스 화면이 함께 소비하는 판정 결과다.
 * [rawEffectiveOn]은 현재일 clamp나 계약상 만기 hard cap을 적용하기 전 공시 자체의 효력 거래일이다.
 */
data class PublishedInstrumentTerminationNotice(
    val event: GameEvent,
    val terms: InstrumentTerminationTerms,
    val rawEffectiveOn: LocalDate,
)

/**
 * 한 거래일의 정규장 마감 감시에서 확정한 상품 종료 의사결정이다.
 *
 * winner 공시, 공시 자체의 원효력일, 현재 감시일을 반영한 실제 예정일을 한 값으로 묶어
 * 상장 사유·평가 방식·현금 지급 조건이 서로 다른 공시에서 섞이지 않게 한다.
 */
data class InstrumentTerminationDecision(
    val notice: PublishedInstrumentTerminationNotice,
    val scheduledTerminationOn: LocalDate,
) {
    /** 별도 복제 없이 winner 공시가 가진 원효력일을 그대로 노출한다. */
    val rawEffectiveOn: LocalDate
        get() = notice.rawEffectiveOn
}

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
