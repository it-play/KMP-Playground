package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** 거래소·상장 조치 뉴스를 현재 원장 상태와 연결하는 구조화된 종류다. */
enum class MarketActionKind {
    KRX_CIRCUIT_BREAKER,
    KRX_SIDECAR,
    KRX_VOLATILITY_INTERRUPTION,
    US_MARKET_WIDE_CIRCUIT_BREAKER,
    US_LIMIT_UP_LIMIT_DOWN,
    INSTRUMENT_TRADING_HALT,
    INVESTMENT_ALERT,
    LISTING_LIFECYCLE,
    LISTING_REMEDIATION,
}

/** 제목이나 이벤트 ID를 해석하지 않고 조치의 발표·효력·해제 단계를 판별하기 위한 전이 정보다. */
enum class MarketActionTransition {
    HALT_SCHEDULED,
    HALT_STARTED,
    PROGRAM_FLOW_SUSPENDED,
    CALL_AUCTION_STARTED,
    CLOSING_AUCTION_STARTED,
    MARKET_CLOSED_FOR_DAY,
    DESIGNATION_NOTICE,
    DESIGNATED,
    RELEASE_ANNOUNCED,
    RELEASED,
    REOPENED,
    LIFECYCLE_CHANGED,
    REMEDIATION_RECORDED,
}

/** 보호장치 상태와 뉴스 원장이 같은 발생 건을 가리키도록 공유하는 불변 식별자들이다. */
internal fun krxCircuitBreakerOccurrenceId(
    market: Market,
    level: KrxCircuitBreakerLevel,
    triggeredAt: Instant,
): String = "krx-cb:${market.name}:${level.name}:$triggeredAt"

internal fun krxSidecarOccurrenceId(market: Market, triggeredAt: Instant): String =
    "krx-sidecar:${market.name}:$triggeredAt"

internal fun krxViOccurrenceId(stockId: String, triggerSequence: Int, triggeredAt: Instant): String =
    "krx-vi:$stockId:$triggerSequence:$triggeredAt"

internal fun usMwcbOccurrenceId(level: UsMwcbLevel, triggeredAt: Instant): String =
    "us-mwcb:${level.name}:$triggeredAt"

internal fun usLuldOccurrenceId(stockId: String, pauseStartedAt: Instant): String =
    "us-luld:$stockId:$pauseStartedAt"

internal fun investmentAlertOccurrenceId(stockId: String, designatedAt: Instant): String =
    "investment-alert:$stockId:$designatedAt"

/**
 * 한 뉴스가 가리키는 거래소 조치의 불변 식별자다.
 *
 * 현재 상태는 [TradingProtectionSnapshot]과 상장 원장이 담당하고, 이 참조는 같은 종류의
 * 조치가 재발해도 과거 뉴스가 새 조치에 붙지 않도록 발생 시각·순번·효력일을 보존한다.
 */
data class MarketActionReference(
    val kind: MarketActionKind,
    val occurrenceId: String,
    val transition: MarketActionTransition,
    val announcedAt: Instant,
    val effectiveAt: Instant = announcedAt,
    val endsAt: Instant? = null,
    val stockId: String? = null,
    val markets: Set<Market> = emptySet(),
    val stage: Int? = null,
    val triggerSequence: Int? = null,
    val alertLevel: InvestmentAlertLevel? = null,
    val effectiveOn: LocalDate? = null,
    val listingLedgerSequence: Long? = null,
    val listingStatus: ListingLifecycleStatus? = null,
) {
    init {
        val violation = semanticInvariantViolation()
        require(violation == null) { requireNotNull(violation) }
    }

    /**
     * Gson처럼 생성자를 우회해 복원하는 경계에서도 재사용할 수 있는 종류별 불변조건 검사다.
     * `null`이면 유효하고, 값이 있으면 첫 번째 의미 오류를 설명한다.
     */
    fun semanticInvariantViolation(): String? {
        if (occurrenceId.isBlank()) return "시장조치 발생 ID는 비어 있을 수 없습니다."
        if (effectiveAt < announcedAt) return "시장조치 효력 시각은 발표보다 빠를 수 없습니다."
        if (endsAt != null && endsAt < effectiveAt) return "시장조치 종료 시각은 효력 시각보다 빠를 수 없습니다."
        if (stockId?.isBlank() == true) return "시장조치 종목 ID는 비어 있을 수 없습니다."
        if (stage != null && stage <= 0) return "시장조치 단계는 1 이상이어야 합니다."
        if (triggerSequence != null && triggerSequence <= 0) return "시장조치 발생 순번은 1 이상이어야 합니다."
        if (listingLedgerSequence != null && listingLedgerSequence <= 0L) {
            return "상장 원장 순번은 1 이상이어야 합니다."
        }
        if (kind in STOCK_ACTION_KINDS && stockId == null) return "종목 시장조치에는 종목 ID가 필요합니다."
        if (kind == MarketActionKind.LISTING_LIFECYCLE &&
            (listingLedgerSequence == null || listingStatus == null)
        ) {
            return "상장 생명주기 뉴스에는 원장 순번과 상태가 필요합니다."
        }
        if (kind == MarketActionKind.INVESTMENT_ALERT && (alertLevel == null || effectiveOn == null)) {
            return "투자경보 뉴스에는 단계와 효력일이 필요합니다."
        }
        if (kind !in CIRCUIT_BREAKER_KINDS && stage != null) {
            return "서킷브레이커 외 조치에는 단계를 둘 수 없습니다."
        }
        if (kind != MarketActionKind.KRX_VOLATILITY_INTERRUPTION && triggerSequence != null) {
            return "VI 외 조치에는 반복 발생 순번을 둘 수 없습니다."
        }
        if (kind != MarketActionKind.INVESTMENT_ALERT && (alertLevel != null || effectiveOn != null)) {
            return "투자경보 외 조치에는 경보 단계·효력일을 둘 수 없습니다."
        }
        if (kind != MarketActionKind.LISTING_LIFECYCLE && listingLedgerSequence != null) {
            return "상장 생명주기 외 조치에는 원장 순번을 둘 수 없습니다."
        }
        if (kind !in LISTING_ACTION_KINDS && listingStatus != null) {
            return "상장 조치 외 뉴스에는 상장 상태를 둘 수 없습니다."
        }
        if (transition in EXACT_END_TRANSITIONS && endsAt != effectiveAt) {
            return "해제·재개 전이의 종료 시각은 효력 시각과 정확히 같아야 합니다."
        }
        return when (kind) {
            MarketActionKind.KRX_CIRCUIT_BREAKER -> {
                when {
                    stockId != null || markets.size != 1 || !markets.single().isKorean ->
                        "KRX 서킷브레이커는 하나의 국내 시장만 대상으로 해야 합니다."
                    !(
                        transition == MarketActionTransition.HALT_STARTED && stage in 1..2 ||
                            transition == MarketActionTransition.MARKET_CLOSED_FOR_DAY && stage == 3 ||
                            transition == MarketActionTransition.REOPENED && stage == null
                    ) -> "KRX 서킷브레이커 단계와 전이가 일치하지 않습니다."
                    endsAt == null -> "KRX 서킷브레이커에는 정확한 조치 종료 시각이 필요합니다."
                    else -> null
                }
            }
            MarketActionKind.KRX_SIDECAR -> {
                when {
                    stockId != null || markets.size != 1 || !markets.single().isKorean ->
                        "KRX 사이드카는 하나의 국내 시장만 대상으로 해야 합니다."
                    transition !in SIDECAR_TRANSITIONS -> "KRX 사이드카 전이가 올바르지 않습니다."
                    else -> null
                }
            }
            MarketActionKind.KRX_VOLATILITY_INTERRUPTION -> {
                when {
                    markets.size != 1 || !markets.single().isKorean || triggerSequence == null ->
                        "KRX VI에는 하나의 국내 시장과 반복 발생 순번이 필요합니다."
                    transition != MarketActionTransition.CALL_AUCTION_STARTED -> "KRX VI 전이가 올바르지 않습니다."
                    endsAt == null -> "KRX VI에는 단일가 종료 시각이 필요합니다."
                    else -> null
                }
            }
            MarketActionKind.US_MARKET_WIDE_CIRCUIT_BREAKER -> {
                when {
                    stockId != null || markets.isEmpty() || !markets.all(Market::isUnitedStates) ->
                        "미국 시장 전체 서킷브레이커는 미국 시장만 대상으로 해야 합니다."
                    !(
                        transition == MarketActionTransition.HALT_STARTED && stage in 1..2 ||
                            transition == MarketActionTransition.MARKET_CLOSED_FOR_DAY && stage == 3 ||
                            transition == MarketActionTransition.REOPENED && stage == null
                    ) -> "미국 시장 전체 서킷브레이커 단계와 전이가 일치하지 않습니다."
                    endsAt == null -> "미국 시장 전체 서킷브레이커에는 정확한 조치 종료 시각이 필요합니다."
                    else -> null
                }
            }
            MarketActionKind.US_LIMIT_UP_LIMIT_DOWN -> {
                when {
                    markets.size != 1 || !markets.single().isUnitedStates ->
                        "미국 LULD는 하나의 미국 주 상장시장만 대상으로 해야 합니다."
                    transition !in LULD_TRANSITIONS -> "미국 LULD 전이가 올바르지 않습니다."
                    endsAt == null -> "미국 LULD에는 정확한 조치 종료 시각이 필요합니다."
                    else -> null
                }
            }
            MarketActionKind.INSTRUMENT_TRADING_HALT -> {
                when {
                    markets.size != 1 -> "종목 거래정지는 하나의 주 상장시장을 대상으로 해야 합니다."
                    transition !in INSTRUMENT_HALT_TRANSITIONS -> "종목 거래정지 전이가 올바르지 않습니다."
                    transition in setOf(
                        MarketActionTransition.HALT_SCHEDULED,
                        MarketActionTransition.RELEASED,
                    ) && endsAt == null -> "예정·해제 거래정지에는 정확한 종료 시각이 필요합니다."
                    else -> null
                }
            }
            MarketActionKind.INVESTMENT_ALERT -> {
                when {
                    markets.size != 1 || !markets.single().isKorean ->
                        "투자경보는 하나의 국내 주 상장시장을 대상으로 해야 합니다."
                    transition !in INVESTMENT_ALERT_TRANSITIONS -> "투자경보 전이가 올바르지 않습니다."
                    transition == MarketActionTransition.DESIGNATION_NOTICE && endsAt == null ->
                        "투자경보 지정예고에는 관찰 종료 시각이 필요합니다."
                    else -> null
                }
            }
            MarketActionKind.LISTING_LIFECYCLE ->
                when {
                    markets.size != 1 -> "상장 생명주기 조치는 하나의 주 상장시장을 대상으로 해야 합니다."
                    transition != MarketActionTransition.LIFECYCLE_CHANGED -> "상장 생명주기 전이가 올바르지 않습니다."
                    else -> null
                }
            MarketActionKind.LISTING_REMEDIATION ->
                when {
                    markets.size != 1 -> "상장 개선심사 조치는 하나의 주 상장시장을 대상으로 해야 합니다."
                    transition != MarketActionTransition.REMEDIATION_RECORDED -> "상장 개선심사 전이가 올바르지 않습니다."
                    else -> null
                }
        }
    }

    private companion object {
        val STOCK_ACTION_KINDS = setOf(
            MarketActionKind.KRX_VOLATILITY_INTERRUPTION,
            MarketActionKind.US_LIMIT_UP_LIMIT_DOWN,
            MarketActionKind.INSTRUMENT_TRADING_HALT,
            MarketActionKind.INVESTMENT_ALERT,
            MarketActionKind.LISTING_LIFECYCLE,
            MarketActionKind.LISTING_REMEDIATION,
        )
        val LISTING_ACTION_KINDS = setOf(
            MarketActionKind.LISTING_LIFECYCLE,
            MarketActionKind.LISTING_REMEDIATION,
        )
        val CIRCUIT_BREAKER_KINDS = setOf(
            MarketActionKind.KRX_CIRCUIT_BREAKER,
            MarketActionKind.US_MARKET_WIDE_CIRCUIT_BREAKER,
        )
        val SIDECAR_TRANSITIONS = setOf(
            MarketActionTransition.PROGRAM_FLOW_SUSPENDED,
            MarketActionTransition.RELEASED,
        )
        val LULD_TRANSITIONS = setOf(
            MarketActionTransition.HALT_STARTED,
            MarketActionTransition.CLOSING_AUCTION_STARTED,
            MarketActionTransition.REOPENED,
        )
        val INSTRUMENT_HALT_TRANSITIONS = setOf(
            MarketActionTransition.HALT_SCHEDULED,
            MarketActionTransition.HALT_STARTED,
            MarketActionTransition.RELEASED,
        )
        val INVESTMENT_ALERT_TRANSITIONS = setOf(
            MarketActionTransition.DESIGNATION_NOTICE,
            MarketActionTransition.DESIGNATED,
            MarketActionTransition.RELEASE_ANNOUNCED,
        )
        val EXACT_END_TRANSITIONS = setOf(
            MarketActionTransition.RELEASE_ANNOUNCED,
            MarketActionTransition.RELEASED,
            MarketActionTransition.REOPENED,
        )
    }
}
