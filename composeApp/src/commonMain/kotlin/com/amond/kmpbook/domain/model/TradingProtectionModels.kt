package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

/** A KRX circuit-breaker stage. Each stage may be activated only once per trading day. */
enum class KrxCircuitBreakerLevel(val declineRate: Double) {
    LEVEL_1(0.08),
    LEVEL_2(0.15),
    LEVEL_3(0.20),
}

enum class KrxCircuitBreakerPhase {
    NORMAL,
    HALTED,
    REOPENING_CALL_AUCTION,
    CLOSED_FOR_DAY,
}

/**
 * Immutable KOSPI/KOSDAQ circuit-breaker state. It contains only values that can be
 * serialized by the desktop persistence adapter; no clock or callback is retained.
 */
data class KrxCircuitBreakerState(
    val market: Market,
    val tradingDate: LocalDate,
    val phase: KrxCircuitBreakerPhase = KrxCircuitBreakerPhase.NORMAL,
    val triggeredLevels: Set<KrxCircuitBreakerLevel> = emptySet(),
    val triggerIndexValues: Map<KrxCircuitBreakerLevel, Double> = emptyMap(),
    val pendingLevel: KrxCircuitBreakerLevel? = null,
    val conditionSince: Instant? = null,
    val activeLevel: KrxCircuitBreakerLevel? = null,
    val triggeredAt: Instant? = null,
    val haltEndsAt: Instant? = null,
    val reopeningEndsAt: Instant? = null,
) {
    init {
        require(market == Market.KOSPI || market == Market.KOSDAQ) { "KRX CB는 KOSPI/KOSDAQ에만 적용됩니다." }
        require((pendingLevel == null) == (conditionSince == null)) { "CB 예고 단계와 지속 시작 시각은 함께 있어야 합니다." }
        require(triggerIndexValues.keys.all { it in triggeredLevels })
        require(triggerIndexValues.values.all { it > 0.0 && it.isFinite() })
        when (phase) {
            KrxCircuitBreakerPhase.NORMAL -> Unit
            KrxCircuitBreakerPhase.HALTED -> {
                require(activeLevel == KrxCircuitBreakerLevel.LEVEL_1 || activeLevel == KrxCircuitBreakerLevel.LEVEL_2)
                require(triggeredAt != null && haltEndsAt != null && reopeningEndsAt != null)
            }
            KrxCircuitBreakerPhase.REOPENING_CALL_AUCTION -> {
                require(activeLevel == KrxCircuitBreakerLevel.LEVEL_1 || activeLevel == KrxCircuitBreakerLevel.LEVEL_2)
                require(reopeningEndsAt != null)
            }
            KrxCircuitBreakerPhase.CLOSED_FOR_DAY -> require(activeLevel == KrxCircuitBreakerLevel.LEVEL_3)
        }
    }
}

data class KrxCircuitBreakerObservation(
    val market: Market,
    val tradingDate: LocalDate,
    val observedAt: Instant,
    val indexValue: Double,
    val previousClose: Double,
    /** Whole or fractional minutes remaining until the scheduled regular-session close. */
    val minutesUntilClose: Double,
    /**
     * Optional intrabar estimate of when every condition for the candidate stage became true.
     * An hourly simulator should linearly interpolate the threshold crossing and pass that time;
     * null makes the engine conservatively begin persistence at [observedAt].
     */
    val conditionSatisfiedSince: Instant? = null,
) {
    init {
        require(market == Market.KOSPI || market == Market.KOSDAQ)
        require(indexValue > 0.0 && indexValue.isFinite())
        require(previousClose > 0.0 && previousClose.isFinite())
        require(minutesUntilClose >= 0.0 && minutesUntilClose.isFinite())
        require(conditionSatisfiedSince == null || conditionSatisfiedSince <= observedAt)
    }

    val declineRate: Double get() = (previousClose - indexValue) / previousClose
}

enum class KrxCircuitBreakerEvent {
    NONE,
    SESSION_RESET,
    PERSISTENCE_STARTED,
    PERSISTENCE_CLEARED,
    LEVEL_1_TRIGGERED,
    LEVEL_2_TRIGGERED,
    LEVEL_3_TRIGGERED,
    HALT_ENDED_REOPENING_STARTED,
    REOPENING_COMPLETED,
}

data class KrxCircuitBreakerTransition(
    val state: KrxCircuitBreakerState,
    val event: KrxCircuitBreakerEvent = KrxCircuitBreakerEvent.NONE,
)

enum class MarketMoveDirection { UP, DOWN }

enum class ProgramOrderSide { BUY, SELL }

enum class KrxSidecarPhase {
    IDLE,
    NOTICE,
    PROGRAM_FLOW_SUSPENDED,
    FINISHED_FOR_DAY,
}

enum class KrxSidecarReleaseReason {
    FIVE_MINUTES_ELAPSED,
    CLOSING_WINDOW,
    CIRCUIT_BREAKER_RESUMPTION,
    MARKET_CLOSED,
}

data class KrxSidecarState(
    val market: Market,
    val tradingDate: LocalDate,
    val phase: KrxSidecarPhase = KrxSidecarPhase.IDLE,
    /** KRX permits one activation per market/day regardless of upward or downward direction. */
    val activationUsed: Boolean = false,
    val pendingDirection: MarketMoveDirection? = null,
    val conditionSince: Instant? = null,
    val triggeredDirection: MarketMoveDirection? = null,
    val suspendedProgramSide: ProgramOrderSide? = null,
    val triggeredAt: Instant? = null,
    val suspensionEndsAt: Instant? = null,
    val releaseOnCircuitBreakerResume: Boolean = false,
    val releasedAt: Instant? = null,
    val releaseReason: KrxSidecarReleaseReason? = null,
) {
    init {
        require(market == Market.KOSPI || market == Market.KOSDAQ)
        require((pendingDirection == null) == (conditionSince == null))
        if (phase == KrxSidecarPhase.PROGRAM_FLOW_SUSPENDED) {
            require(activationUsed)
            require(triggeredDirection != null && suspendedProgramSide != null)
            require(triggeredAt != null && suspensionEndsAt != null)
        }
        if (phase == KrxSidecarPhase.FINISHED_FOR_DAY) require(activationUsed)
    }
}

data class KrxSidecarObservation(
    val market: Market,
    val tradingDate: LocalDate,
    val observedAt: Instant,
    val futuresChangeRate: Double,
    /** Required for KOSDAQ150; ignored for KOSPI200. */
    val spotIndexChangeRate: Double? = null,
    val minutesAfterOpen: Double,
    val minutesUntilClose: Double,
    val futuresTradingHalted: Boolean = false,
    val circuitBreakerPhase: KrxCircuitBreakerPhase = KrxCircuitBreakerPhase.NORMAL,
    /** Intrabar time when all direction-specific futures/spot conditions became true. */
    val conditionSatisfiedSince: Instant? = null,
) {
    init {
        require(market == Market.KOSPI || market == Market.KOSDAQ)
        require(futuresChangeRate.isFinite())
        require(spotIndexChangeRate == null || spotIndexChangeRate.isFinite())
        require(minutesAfterOpen >= 0.0 && minutesAfterOpen.isFinite())
        require(minutesUntilClose >= 0.0 && minutesUntilClose.isFinite())
        require(market != Market.KOSDAQ || spotIndexChangeRate != null) { "KOSDAQ 사이드카에는 현물지수 변동률이 필요합니다." }
        require(conditionSatisfiedSince == null || conditionSatisfiedSince <= observedAt)
    }
}

enum class KrxSidecarEvent {
    NONE,
    SESSION_RESET,
    NOTICE_STARTED,
    NOTICE_CANCELLED,
    ACTIVATED,
    CIRCUIT_BREAKER_TAKES_PRECEDENCE,
    RELEASED,
}

data class KrxSidecarTransition(
    val state: KrxSidecarState,
    val event: KrxSidecarEvent = KrxSidecarEvent.NONE,
)

enum class KrxViKind { DYNAMIC, STATIC }

enum class KrxViProductClass {
    KOSPI200_CONSTITUENT,
    OTHER_EQUITY,
    CORE_INDEX_INVERSE_OR_BOND_ETP,
    OTHER_ETP,
    ;

    val usesLowerDynamicRate: Boolean
        get() = this == KOSPI200_CONSTITUENT || this == CORE_INDEX_INVERSE_OR_BOND_ETP
}

enum class KrxViSession {
    OPENING_CALL_AUCTION,
    CONTINUOUS_AUCTION,
    CLOSING_CALL_AUCTION,
    AFTER_HOURS_PERIODIC_CALL_AUCTION,
}

enum class KrxViDirection { UPPER, LOWER }

enum class KrxViPhase { IDLE, CALL_AUCTION }

data class KrxViState(
    val stockId: String,
    val market: Market,
    val phase: KrxViPhase = KrxViPhase.IDLE,
    val kind: KrxViKind? = null,
    val session: KrxViSession? = null,
    val referencePrice: Double? = null,
    val triggerRate: Double? = null,
    val direction: KrxViDirection? = null,
    val triggeredAt: Instant? = null,
    val auctionEndsAt: Instant? = null,
    /** VI is repeatable and therefore this counter has no daily cap. */
    val triggerCount: Int = 0,
) {
    init {
        require(stockId.isNotBlank())
        require(market == Market.KOSPI || market == Market.KOSDAQ)
        require(triggerCount >= 0)
        if (phase == KrxViPhase.CALL_AUCTION) {
            require(kind != null && session != null && direction != null)
            require(referencePrice != null && referencePrice > 0.0)
            require(triggerRate != null && triggerRate > 0.0)
            require(triggeredAt != null && auctionEndsAt != null)
        }
    }
}

data class KrxViObservation(
    val stockId: String,
    val market: Market,
    val observedAt: Instant,
    val kind: KrxViKind,
    val productClass: KrxViProductClass,
    val session: KrxViSession,
    val referencePrice: Double,
    val potentialExecutionPrice: Double,
    /** Existing periodic/closing call-auction end; VI extends that auction by two minutes. */
    val existingCallAuctionEndsAt: Instant? = null,
    val isEquityDerivativesExpirationClosingAuction: Boolean = false,
    val isViExcluded: Boolean = false,
    val circuitBreakerPhase: KrxCircuitBreakerPhase = KrxCircuitBreakerPhase.NORMAL,
) {
    init {
        require(stockId.isNotBlank())
        require(market == Market.KOSPI || market == Market.KOSDAQ)
        require(referencePrice > 0.0 && referencePrice.isFinite())
        require(potentialExecutionPrice > 0.0 && potentialExecutionPrice.isFinite())
        require(existingCallAuctionEndsAt == null || existingCallAuctionEndsAt >= observedAt)
    }
}

enum class TriggeringQuotationDisposition {
    UNAFFECTED,
    NOT_EXECUTED_AND_ENTERED_INTO_CALL_AUCTION,
}

enum class KrxViEvent {
    NONE,
    TRIGGERED,
    AUCTION_COMPLETED,
    CANCELLED_BY_MARKET_CIRCUIT_BREAKER,
}

data class KrxViTransition(
    val state: KrxViState,
    val event: KrxViEvent = KrxViEvent.NONE,
    val triggeringQuotationDisposition: TriggeringQuotationDisposition = TriggeringQuotationDisposition.UNAFFECTED,
)

enum class TradingHaltReason {
    MATERIAL_DISCLOSURE,
    DISCLOSURE_INQUIRY,
    LISTING_MAINTENANCE_REVIEW,
    DELISTING_PROCESS,
    INVESTOR_PROTECTION,
    SETTLEMENT_FAILURE,
    CORPORATE_ACTION,
    TECHNICAL_DISRUPTION,
    REGULATORY_ACTION,
    OTHER,
}

data class TradingHaltOrderPolicy(
    val acceptsNewOrders: Boolean,
    val allowsCancellation: Boolean,
    val allowsExecution: Boolean,
    val allowsContinuousTrading: Boolean = false,
)

enum class TradingHaltStatus { ACTIVE, RELEASED }

data class InstrumentTradingHalt(
    val stockId: String,
    val reason: TradingHaltReason,
    val detail: String,
    val startedAt: Instant,
    val policy: TradingHaltOrderPolicy,
    val scheduledReleaseAt: Instant? = null,
    val status: TradingHaltStatus = TradingHaltStatus.ACTIVE,
    val releasedAt: Instant? = null,
    val releaseNote: String? = null,
) {
    init {
        require(stockId.isNotBlank())
        require(detail.isNotBlank())
        require(scheduledReleaseAt == null || scheduledReleaseAt >= startedAt)
        if (status == TradingHaltStatus.RELEASED) require(releasedAt != null)
    }
}

enum class InvestmentAlertLevel { CAUTION, WARNING, DANGER }

data class TradingDayWindow(
    val startsOn: LocalDate,
    val endsOnInclusive: LocalDate,
) {
    init {
        require(endsOnInclusive >= startsOn)
    }

    operator fun contains(date: LocalDate): Boolean = date in startsOn..endsOnInclusive
}

enum class InvestmentAlertStatus { ACTIVE, RELEASED }

/** KRX 공시의 지정 사유군별 해제 상승률 기준을 저장한다. */
enum class InvestmentAlertReleaseRule {
    CAUTION_PRICE_VOLUME,
    WARNING_45_75,
    WARNING_60_100,
    DANGER_60_100,
}

/**
 * KRX alert criteria vary by designation reason. The listing policy supplies already-calculated
 * trading-day windows, while this primitive records and enforces release/re-designation timing.
 */
data class InvestmentAlertDesignation(
    val stockId: String,
    val level: InvestmentAlertLevel,
    val reasonCodes: Set<String>,
    val summary: String,
    val designatedAt: Instant,
    val designatedOn: LocalDate,
    val releaseReviewWindow: TradingDayWindow,
    val redesignationWindow: TradingDayWindow? = null,
    val releaseRule: InvestmentAlertReleaseRule,
    /** 투자경고 재지정 판단에 쓰는 최초 투자경고 지정 전일 종가. */
    val preDesignationClose: Double? = null,
    /** 투자경고 재지정 판단에 쓰는 투자경고 해제 전일 종가. */
    val preReleaseClose: Double? = null,
    val redesignationReleaseRule: InvestmentAlertReleaseRule? = null,
    /** 상위 경보 지정예고 효력일과 그 가격 규칙. */
    val escalationNoticeOn: LocalDate? = null,
    val escalationNoticeReasons: Set<String> = emptySet(),
    /** 새 지정 효력일 전까지 화면에 유지할 직전 경보 단계. */
    val priorLevelUntilEffective: InvestmentAlertLevel? = null,
    val isRedesignation: Boolean = false,
    val status: InvestmentAlertStatus = InvestmentAlertStatus.ACTIVE,
    val releasedAt: Instant? = null,
    val releasedOn: LocalDate? = null,
    /** 해제 판단 공시의 다음 거래일부터 화면·규제가 바뀐다. */
    val releaseEffectiveOn: LocalDate? = null,
    val releaseReason: String? = null,
) {
    init {
        require(stockId.isNotBlank())
        require(reasonCodes.isNotEmpty() && reasonCodes.none { it.isBlank() })
        require(summary.isNotBlank())
        require(designatedOn <= releaseReviewWindow.endsOnInclusive)
        require(preDesignationClose == null || preDesignationClose > 0.0 && preDesignationClose.isFinite())
        require(preReleaseClose == null || preReleaseClose > 0.0 && preReleaseClose.isFinite())
        require((escalationNoticeOn == null) == escalationNoticeReasons.isEmpty())
        if (status == InvestmentAlertStatus.RELEASED) {
            require(releasedAt != null && releasedOn != null)
            require(releaseEffectiveOn == null || releaseEffectiveOn >= releasedOn)
        }
    }
}

enum class UsMwcbLevel(val declineRate: Double) {
    LEVEL_1(0.07),
    LEVEL_2(0.13),
    LEVEL_3(0.20),
}

enum class UsMwcbPhase { NORMAL, HALTED, REOPENING_AUCTIONS, CLOSED_FOR_DAY }

enum class UsMwcbVenuePhase { CONTINUOUS, HALTED, REOPENING_AUCTION, REOPENED, CLOSED }

data class UsMwcbVenueStatus(
    val market: Market,
    val phase: UsMwcbVenuePhase,
    val phaseStartedAt: Instant,
) {
    init {
        require(market.isUnitedStates)
    }
}

data class UsMwcbState(
    val tradingDate: LocalDate,
    val phase: UsMwcbPhase = UsMwcbPhase.NORMAL,
    val triggeredLevels: Set<UsMwcbLevel> = emptySet(),
    val activeLevel: UsMwcbLevel? = null,
    val triggeredAt: Instant? = null,
    val haltEndsAt: Instant? = null,
    /** Per-primary-listing-market reopening metadata; auctions do not all finish simultaneously. */
    val venueStatuses: Map<Market, UsMwcbVenueStatus> = emptyMap(),
) {
    init {
        require(venueStatuses.keys.all { it.isUnitedStates })
        require(venueStatuses.all { (market, status) -> market == status.market })
        when (phase) {
            UsMwcbPhase.NORMAL -> Unit
            UsMwcbPhase.HALTED -> require(activeLevel != null && triggeredAt != null && haltEndsAt != null)
            UsMwcbPhase.REOPENING_AUCTIONS -> require(activeLevel == UsMwcbLevel.LEVEL_1 || activeLevel == UsMwcbLevel.LEVEL_2)
            UsMwcbPhase.CLOSED_FOR_DAY -> require(activeLevel == UsMwcbLevel.LEVEL_3)
        }
    }
}

data class UsMwcbObservation(
    val tradingDate: LocalDate,
    val observedAt: Instant,
    val easternTime: LocalTime,
    val sp500Value: Double,
    val previousClose: Double,
) {
    init {
        require(sp500Value > 0.0 && sp500Value.isFinite())
        require(previousClose > 0.0 && previousClose.isFinite())
    }

    val declineRate: Double get() = (previousClose - sp500Value) / previousClose
}

enum class UsMwcbEvent {
    NONE,
    SESSION_RESET,
    LEVEL_1_TRIGGERED,
    LEVEL_2_TRIGGERED,
    LEVEL_3_TRIGGERED,
    REOPENING_AUCTIONS_STARTED,
    VENUE_REOPENED,
    ALL_VENUES_REOPENED,
}

data class UsMwcbTransition(
    val state: UsMwcbState,
    val event: UsMwcbEvent = UsMwcbEvent.NONE,
)

enum class UsLuldTier { TIER_1, TIER_2 }

enum class UsLuldLimitSide { UPPER, LOWER }

enum class UsLuldPhase {
    NORMAL,
    LIMIT_STATE,
    TRADING_PAUSE,
    REOPENING_AUCTION,
    CLOSING_AUCTION_ONLY,
    CLOSED_FOR_DAY,
}

data class UsLuldBands(
    val referencePrice: Double,
    val lower: Double,
    val upper: Double,
    val bandAmount: Double,
    val doubledForClosingWindow: Boolean,
) {
    init {
        require(referencePrice > 0.0 && lower >= 0.0 && upper > referencePrice)
        require(lower < referencePrice && bandAmount > 0.0)
    }

    operator fun contains(price: Double): Boolean = price in lower..upper
}

data class UsLuldState(
    val stockId: String,
    val primaryMarket: Market,
    val tradingDate: LocalDate,
    val tier: UsLuldTier,
    val previousClose: Double,
    val referencePrice: Double,
    val referencePriceEffectiveAt: Instant,
    val bands: UsLuldBands,
    val phase: UsLuldPhase = UsLuldPhase.NORMAL,
    val limitSide: UsLuldLimitSide? = null,
    val limitStateStartedAt: Instant? = null,
    val limitStateDeadline: Instant? = null,
    val pauseStartedAt: Instant? = null,
    val pauseEndsAt: Instant? = null,
    val pauseExtensionCount: Int = 0,
    val reopeningStartedAt: Instant? = null,
) {
    init {
        require(stockId.isNotBlank())
        require(primaryMarket.isUnitedStates)
        require(previousClose > 0.0 && referencePrice > 0.0)
        require(bands.referencePrice == referencePrice)
        require(pauseExtensionCount in 0..1)
        if (phase == UsLuldPhase.LIMIT_STATE) {
            require(limitSide != null && limitStateStartedAt != null && limitStateDeadline != null)
        }
        if (phase == UsLuldPhase.TRADING_PAUSE) require(pauseStartedAt != null && pauseEndsAt != null)
        if (phase == UsLuldPhase.REOPENING_AUCTION) require(reopeningStartedAt != null)
    }
}

data class UsLuldObservation(
    val observedAt: Instant,
    val easternTime: LocalTime,
    /** SIP-determined limit side; null means no Limit State quotation is present. */
    val limitSide: UsLuldLimitSide? = null,
    /** True only when the entire size of every limit-state quotation has executed or cancelled. */
    val allLimitStateQuotationsCleared: Boolean = false,
)

enum class UsLuldEvent {
    NONE,
    LIMIT_STATE_ENTERED,
    LIMIT_STATE_CLEARED,
    TRADING_PAUSE_STARTED,
    TRADING_PAUSE_EXTENDED,
    REOPENING_AUCTION_STARTED,
    REOPENED,
    CLOSING_AUCTION_ONLY,
    SESSION_CLOSED,
    REFERENCE_PRICE_UPDATED,
}

data class UsLuldTransition(
    val state: UsLuldState,
    val event: UsLuldEvent = UsLuldEvent.NONE,
)

/** Actions queried through the single protection permission API. */
enum class TradingProtectionAction {
    SUBMIT_ORDER,
    CANCEL_ORDER,
    EXECUTE_TRADE,
    PROGRAM_TRADE_FLOW,
    CONTINUOUS_TRADING,
}

enum class TradingExecutionMode {
    CONTINUOUS,
    CALL_AUCTION_ONLY,
    CLOSING_AUCTION_ONLY,
    PAUSED,
    CLOSED,
}

enum class TradingRestrictionSource {
    KRX_MARKET_CIRCUIT_BREAKER,
    US_MARKET_WIDE_CIRCUIT_BREAKER,
    INSTRUMENT_TRADING_HALT,
    KRX_VOLATILITY_INTERRUPTION,
    US_LIMIT_UP_LIMIT_DOWN,
    KRX_SIDECAR,
}

data class TradingRestriction(
    val source: TradingRestrictionSource,
    val code: String,
    val message: String,
    val endsAt: Instant? = null,
) {
    init {
        require(code.isNotBlank() && message.isNotBlank())
    }
}

data class TradingProtectionSnapshot(
    val krxCircuitBreakers: Map<Market, KrxCircuitBreakerState> = emptyMap(),
    val krxSidecars: Map<Market, KrxSidecarState> = emptyMap(),
    val krxVolatilityInterruptions: Map<String, KrxViState> = emptyMap(),
    val instrumentTradingHalts: Map<String, InstrumentTradingHalt> = emptyMap(),
    /** 다음 정규장에 효력이 생기는 KRX 전일 공시 정지. */
    val scheduledInstrumentTradingHalts: Map<String, InstrumentTradingHalt> = emptyMap(),
    val investmentAlerts: Map<String, InvestmentAlertDesignation> = emptyMap(),
    val usMarketWideCircuitBreaker: UsMwcbState? = null,
    val usLuldStates: Map<String, UsLuldState> = emptyMap(),
)

data class TradingProtectionRequest(
    val market: Market,
    val action: TradingProtectionAction,
    val stockId: String? = null,
    val isProgramOrder: Boolean = false,
    val programOrderSide: ProgramOrderSide? = null,
    val proposedExecutionPrice: Double? = null,
    /** Auction-eligible orders may be accepted while continuous matching is paused. */
    val isAuctionEligibleOrder: Boolean = false,
) {
    init {
        require(stockId == null || stockId.isNotBlank())
        require(!isProgramOrder || programOrderSide != null)
        require(proposedExecutionPrice == null || proposedExecutionPrice > 0.0)
    }
}

data class TradingPermissionDecision(
    val allowed: Boolean,
    val executionMode: TradingExecutionMode,
    val controllingRestriction: TradingRestriction? = null,
    val restrictions: List<TradingRestriction> = emptyList(),
) {
    init {
        require((controllingRestriction == null) == restrictions.isEmpty())
        require(controllingRestriction == null || controllingRestriction == restrictions.first())
        require(allowed || restrictions.isNotEmpty())
    }
}
