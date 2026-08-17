package com.amond.kmpbook.domain.simulation.protection

import com.amond.kmpbook.domain.model.listing.alert.InvestmentAlertDesignation
import com.amond.kmpbook.domain.model.listing.alert.InvestmentAlertLevel
import com.amond.kmpbook.domain.model.listing.alert.InvestmentAlertStatus
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.market.MarketMoveDirection
import com.amond.kmpbook.domain.model.protection.core.InstrumentTradingHalt
import com.amond.kmpbook.domain.model.protection.core.ProgramOrderSide
import com.amond.kmpbook.domain.model.protection.core.TradingHaltOrderPolicy
import com.amond.kmpbook.domain.model.protection.core.TradingHaltReason
import com.amond.kmpbook.domain.model.protection.core.TradingHaltStatus
import com.amond.kmpbook.domain.model.protection.core.TradingPermissionDecision
import com.amond.kmpbook.domain.model.protection.core.TradingProtectionAction
import com.amond.kmpbook.domain.model.protection.core.TradingProtectionRequest
import com.amond.kmpbook.domain.model.protection.core.TradingProtectionSnapshot
import com.amond.kmpbook.domain.model.protection.core.TradingRestriction
import com.amond.kmpbook.domain.model.protection.core.TradingRestrictionSource
import com.amond.kmpbook.domain.model.protection.core.TriggeringQuotationDisposition
import com.amond.kmpbook.domain.model.protection.krx.KrxCircuitBreakerEvent
import com.amond.kmpbook.domain.model.protection.krx.KrxCircuitBreakerLevel
import com.amond.kmpbook.domain.model.protection.krx.KrxCircuitBreakerObservation
import com.amond.kmpbook.domain.model.protection.krx.KrxCircuitBreakerPhase
import com.amond.kmpbook.domain.model.protection.krx.KrxCircuitBreakerState
import com.amond.kmpbook.domain.model.protection.krx.KrxCircuitBreakerTransition
import com.amond.kmpbook.domain.model.protection.krx.KrxSidecarEvent
import com.amond.kmpbook.domain.model.protection.krx.KrxSidecarObservation
import com.amond.kmpbook.domain.model.protection.krx.KrxSidecarPhase
import com.amond.kmpbook.domain.model.protection.krx.KrxSidecarReleaseReason
import com.amond.kmpbook.domain.model.protection.krx.KrxSidecarState
import com.amond.kmpbook.domain.model.protection.krx.KrxSidecarTransition
import com.amond.kmpbook.domain.model.protection.krx.KrxViDirection
import com.amond.kmpbook.domain.model.protection.krx.KrxViEvent
import com.amond.kmpbook.domain.model.protection.krx.KrxViKind
import com.amond.kmpbook.domain.model.protection.krx.KrxViObservation
import com.amond.kmpbook.domain.model.protection.krx.KrxViPhase
import com.amond.kmpbook.domain.model.protection.krx.KrxViProductClass
import com.amond.kmpbook.domain.model.protection.krx.KrxViSession
import com.amond.kmpbook.domain.model.protection.krx.KrxViState
import com.amond.kmpbook.domain.model.protection.krx.KrxViTransition
import com.amond.kmpbook.domain.model.protection.us.UsLuldBands
import com.amond.kmpbook.domain.model.protection.us.UsLuldEvent
import com.amond.kmpbook.domain.model.protection.us.UsLuldObservation
import com.amond.kmpbook.domain.model.protection.us.UsLuldPhase
import com.amond.kmpbook.domain.model.protection.us.UsLuldState
import com.amond.kmpbook.domain.model.protection.us.UsLuldTier
import com.amond.kmpbook.domain.model.protection.us.UsLuldTransition
import com.amond.kmpbook.domain.model.protection.us.UsMwcbEvent
import com.amond.kmpbook.domain.model.protection.us.UsMwcbLevel
import com.amond.kmpbook.domain.model.protection.us.UsMwcbObservation
import com.amond.kmpbook.domain.model.protection.us.UsMwcbPhase
import com.amond.kmpbook.domain.model.protection.us.UsMwcbState
import com.amond.kmpbook.domain.model.protection.us.UsMwcbTransition
import com.amond.kmpbook.domain.model.protection.us.UsMwcbVenuePhase
import com.amond.kmpbook.domain.model.protection.us.UsMwcbVenueStatus
import com.amond.kmpbook.domain.model.trading.TradingExecutionMode
import com.amond.kmpbook.domain.simulation.market.MarketMicrostructure
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/** Pure, deterministic state transitions for market-wide and single-security protections. */
object TradingProtectionEngine {
    private const val RATE_EPSILON = 1e-12

    fun initialKrxCircuitBreaker(market: Market, tradingDate: LocalDate): KrxCircuitBreakerState =
        KrxCircuitBreakerState(market = market, tradingDate = tradingDate)

    /**
     * Evaluates a turn-end observation. If an hourly bar can locate the threshold crossing,
     * supply [KrxCircuitBreakerObservation.conditionSatisfiedSince] so the official one-minute
     * persistence is evaluated inside that bar instead of being rounded to one game turn.
     */
    fun evaluateKrxCircuitBreaker(
        state: KrxCircuitBreakerState,
        observation: KrxCircuitBreakerObservation,
    ): KrxCircuitBreakerTransition {
        require(state.market == observation.market)
        var current = if (state.tradingDate == observation.tradingDate) {
            state
        } else {
            initialKrxCircuitBreaker(observation.market, observation.tradingDate)
        }
        val sessionWasReset = state.tradingDate != observation.tradingDate
        val advanced = advanceKrxCircuitBreaker(current, observation.observedAt)
        current = advanced.state
        if (current.phase != KrxCircuitBreakerPhase.NORMAL) {
            return if (advanced.event != KrxCircuitBreakerEvent.NONE) advanced else KrxCircuitBreakerTransition(
                state = current,
                event = if (sessionWasReset) KrxCircuitBreakerEvent.SESSION_RESET else KrxCircuitBreakerEvent.NONE,
            )
        }

        val candidate = krxCircuitBreakerCandidate(current, observation)
        if (candidate == null) {
            val hadPending = current.pendingLevel != null
            return KrxCircuitBreakerTransition(
                state = current.copy(pendingLevel = null, conditionSince = null),
                event = when {
                    hadPending -> KrxCircuitBreakerEvent.PERSISTENCE_CLEARED
                    advanced.event != KrxCircuitBreakerEvent.NONE -> advanced.event
                    sessionWasReset -> KrxCircuitBreakerEvent.SESSION_RESET
                    else -> KrxCircuitBreakerEvent.NONE
                },
            )
        }

        if (current.pendingLevel != candidate) {
            val since = observation.conditionSatisfiedSince ?: observation.observedAt
            current = current.copy(pendingLevel = candidate, conditionSince = since)
        } else if (observation.conditionSatisfiedSince != null && observation.conditionSatisfiedSince < current.conditionSince!!) {
            current = current.copy(conditionSince = observation.conditionSatisfiedSince)
        }
        val conditionSince = requireNotNull(current.conditionSince)
        if (observation.observedAt - conditionSince < TradingProtectionRules.KRX_CB_PERSISTENCE) {
            return KrxCircuitBreakerTransition(current, KrxCircuitBreakerEvent.PERSISTENCE_STARTED)
        }

        val triggeredLevels = current.triggeredLevels + candidate
        val triggerValues = current.triggerIndexValues + (candidate to observation.indexValue)
        return if (candidate == KrxCircuitBreakerLevel.LEVEL_3) {
            KrxCircuitBreakerTransition(
                state = current.copy(
                    phase = KrxCircuitBreakerPhase.CLOSED_FOR_DAY,
                    triggeredLevels = triggeredLevels,
                    triggerIndexValues = triggerValues,
                    pendingLevel = null,
                    conditionSince = null,
                    activeLevel = candidate,
                    triggeredAt = observation.observedAt,
                    haltEndsAt = null,
                    reopeningEndsAt = null,
                ),
                event = KrxCircuitBreakerEvent.LEVEL_3_TRIGGERED,
            )
        } else {
            val haltEndsAt = observation.observedAt + TradingProtectionRules.KRX_CB_HALT
            KrxCircuitBreakerTransition(
                state = current.copy(
                    phase = KrxCircuitBreakerPhase.HALTED,
                    triggeredLevels = triggeredLevels,
                    triggerIndexValues = triggerValues,
                    pendingLevel = null,
                    conditionSince = null,
                    activeLevel = candidate,
                    triggeredAt = observation.observedAt,
                    haltEndsAt = haltEndsAt,
                    reopeningEndsAt = haltEndsAt + TradingProtectionRules.KRX_CB_REOPENING_CALL,
                ),
                event = if (candidate == KrxCircuitBreakerLevel.LEVEL_1) {
                    KrxCircuitBreakerEvent.LEVEL_1_TRIGGERED
                } else {
                    KrxCircuitBreakerEvent.LEVEL_2_TRIGGERED
                },
            )
        }
    }

    fun advanceKrxCircuitBreaker(
        state: KrxCircuitBreakerState,
        at: Instant,
    ): KrxCircuitBreakerTransition = when (state.phase) {
        KrxCircuitBreakerPhase.HALTED -> {
            val haltEndsAt = requireNotNull(state.haltEndsAt)
            val reopeningEndsAt = requireNotNull(state.reopeningEndsAt)
            when {
                at >= reopeningEndsAt -> KrxCircuitBreakerTransition(
                    state.copy(
                        phase = KrxCircuitBreakerPhase.NORMAL,
                        activeLevel = null,
                        triggeredAt = null,
                        haltEndsAt = null,
                        reopeningEndsAt = null,
                    ),
                    KrxCircuitBreakerEvent.REOPENING_COMPLETED,
                )
                at >= haltEndsAt -> KrxCircuitBreakerTransition(
                    state.copy(phase = KrxCircuitBreakerPhase.REOPENING_CALL_AUCTION),
                    KrxCircuitBreakerEvent.HALT_ENDED_REOPENING_STARTED,
                )
                else -> KrxCircuitBreakerTransition(state)
            }
        }
        KrxCircuitBreakerPhase.REOPENING_CALL_AUCTION -> {
            if (at >= requireNotNull(state.reopeningEndsAt)) {
                KrxCircuitBreakerTransition(
                    state.copy(
                        phase = KrxCircuitBreakerPhase.NORMAL,
                        activeLevel = null,
                        triggeredAt = null,
                        haltEndsAt = null,
                        reopeningEndsAt = null,
                    ),
                    KrxCircuitBreakerEvent.REOPENING_COMPLETED,
                )
            } else {
                KrxCircuitBreakerTransition(state)
            }
        }
        else -> KrxCircuitBreakerTransition(state)
    }

    private fun krxCircuitBreakerCandidate(
        state: KrxCircuitBreakerState,
        observation: KrxCircuitBreakerObservation,
    ): KrxCircuitBreakerLevel? {
        val decline = observation.declineRate
        val beforeCutoff = observation.minutesUntilClose > TradingProtectionRules.KRX_CB_CUTOFF_MINUTES_BEFORE_CLOSE
        val level1Value = state.triggerIndexValues[KrxCircuitBreakerLevel.LEVEL_1]
        val level2Value = state.triggerIndexValues[KrxCircuitBreakerLevel.LEVEL_2]
        return when {
            KrxCircuitBreakerLevel.LEVEL_3 !in state.triggeredLevels &&
                KrxCircuitBreakerLevel.LEVEL_2 in state.triggeredLevels &&
                decline + RATE_EPSILON >= TradingProtectionRules.KRX_CB_LEVEL_3_DECLINE &&
                observation.indexValue <= requireNotNull(level2Value) * (1.0 - TradingProtectionRules.KRX_CB_ADDITIONAL_DECLINE) + RATE_EPSILON ->
                KrxCircuitBreakerLevel.LEVEL_3
            beforeCutoff && KrxCircuitBreakerLevel.LEVEL_2 !in state.triggeredLevels &&
                KrxCircuitBreakerLevel.LEVEL_1 in state.triggeredLevels &&
                decline + RATE_EPSILON >= TradingProtectionRules.KRX_CB_LEVEL_2_DECLINE &&
                observation.indexValue <= requireNotNull(level1Value) * (1.0 - TradingProtectionRules.KRX_CB_ADDITIONAL_DECLINE) + RATE_EPSILON ->
                KrxCircuitBreakerLevel.LEVEL_2
            beforeCutoff && KrxCircuitBreakerLevel.LEVEL_1 !in state.triggeredLevels &&
                decline + RATE_EPSILON >= TradingProtectionRules.KRX_CB_LEVEL_1_DECLINE ->
                KrxCircuitBreakerLevel.LEVEL_1
            else -> null
        }
    }

    fun initialKrxSidecar(market: Market, tradingDate: LocalDate): KrxSidecarState =
        KrxSidecarState(market = market, tradingDate = tradingDate)

    fun evaluateKrxSidecar(
        state: KrxSidecarState,
        observation: KrxSidecarObservation,
    ): KrxSidecarTransition {
        require(state.market == observation.market)
        var current = if (state.tradingDate == observation.tradingDate) {
            state
        } else {
            initialKrxSidecar(observation.market, observation.tradingDate)
        }
        val reset = state.tradingDate != observation.tradingDate

        if (current.phase == KrxSidecarPhase.PROGRAM_FLOW_SUSPENDED) {
            if (observation.circuitBreakerPhase == KrxCircuitBreakerPhase.CLOSED_FOR_DAY) {
                return releaseSidecar(current, observation.observedAt, KrxSidecarReleaseReason.MARKET_CLOSED)
            }
            if (current.releaseOnCircuitBreakerResume && observation.circuitBreakerPhase == KrxCircuitBreakerPhase.NORMAL) {
                return releaseSidecar(current, observation.observedAt, KrxSidecarReleaseReason.CIRCUIT_BREAKER_RESUMPTION)
            }
            if (observation.circuitBreakerPhase != KrxCircuitBreakerPhase.NORMAL) {
                return KrxSidecarTransition(
                    current.copy(releaseOnCircuitBreakerResume = true),
                    KrxSidecarEvent.CIRCUIT_BREAKER_TAKES_PRECEDENCE,
                )
            }
            if (observation.minutesUntilClose <= TradingProtectionRules.KRX_SIDECAR_CUTOFF_MINUTES_BEFORE_CLOSE) {
                return releaseSidecar(current, observation.observedAt, KrxSidecarReleaseReason.CLOSING_WINDOW)
            }
            if (observation.observedAt >= requireNotNull(current.suspensionEndsAt)) {
                return releaseSidecar(current, observation.observedAt, KrxSidecarReleaseReason.FIVE_MINUTES_ELAPSED)
            }
            return KrxSidecarTransition(current)
        }
        if (current.activationUsed) return KrxSidecarTransition(current)

        val eligibleTime = observation.minutesAfterOpen >= TradingProtectionRules.KRX_SIDECAR_FIRST_ELIGIBLE_MINUTE &&
            observation.minutesUntilClose > TradingProtectionRules.KRX_SIDECAR_CUTOFF_MINUTES_BEFORE_CLOSE
        val protectionAvailable = observation.circuitBreakerPhase == KrxCircuitBreakerPhase.NORMAL &&
            !observation.futuresTradingHalted && eligibleTime
        val direction = if (protectionAvailable) sidecarDirection(observation) else null
        if (direction == null) {
            val hadNotice = current.phase == KrxSidecarPhase.NOTICE
            current = current.copy(
                phase = KrxSidecarPhase.IDLE,
                pendingDirection = null,
                conditionSince = null,
            )
            return KrxSidecarTransition(
                current,
                when {
                    hadNotice -> KrxSidecarEvent.NOTICE_CANCELLED
                    reset -> KrxSidecarEvent.SESSION_RESET
                    else -> KrxSidecarEvent.NONE
                },
            )
        }

        if (current.pendingDirection != direction) {
            current = current.copy(
                phase = KrxSidecarPhase.NOTICE,
                pendingDirection = direction,
                conditionSince = observation.conditionSatisfiedSince ?: observation.observedAt,
            )
        } else if (observation.conditionSatisfiedSince != null && observation.conditionSatisfiedSince < current.conditionSince!!) {
            current = current.copy(conditionSince = observation.conditionSatisfiedSince)
        }
        if (observation.observedAt - requireNotNull(current.conditionSince) < TradingProtectionRules.KRX_SIDECAR_PERSISTENCE) {
            return KrxSidecarTransition(current, KrxSidecarEvent.NOTICE_STARTED)
        }

        return KrxSidecarTransition(
            state = current.copy(
                phase = KrxSidecarPhase.PROGRAM_FLOW_SUSPENDED,
                activationUsed = true,
                pendingDirection = null,
                conditionSince = null,
                triggeredDirection = direction,
                suspendedProgramSide = if (direction == MarketMoveDirection.UP) ProgramOrderSide.BUY else ProgramOrderSide.SELL,
                triggeredAt = observation.observedAt,
                suspensionEndsAt = observation.observedAt + TradingProtectionRules.KRX_SIDECAR_SUSPENSION,
            ),
            event = KrxSidecarEvent.ACTIVATED,
        )
    }

    private fun sidecarDirection(observation: KrxSidecarObservation): MarketMoveDirection? {
        val futures = observation.futuresChangeRate
        if (observation.market == Market.KOSPI) {
            return when {
                futures + RATE_EPSILON >= TradingProtectionRules.KOSPI_SIDECAR_FUTURES_RATE -> MarketMoveDirection.UP
                futures - RATE_EPSILON <= -TradingProtectionRules.KOSPI_SIDECAR_FUTURES_RATE -> MarketMoveDirection.DOWN
                else -> null
            }
        }
        val spot = requireNotNull(observation.spotIndexChangeRate)
        return when {
            futures + RATE_EPSILON >= TradingProtectionRules.KOSDAQ_SIDECAR_FUTURES_RATE &&
                spot + RATE_EPSILON >= TradingProtectionRules.KOSDAQ_SIDECAR_SPOT_RATE -> MarketMoveDirection.UP
            futures - RATE_EPSILON <= -TradingProtectionRules.KOSDAQ_SIDECAR_FUTURES_RATE &&
                spot - RATE_EPSILON <= -TradingProtectionRules.KOSDAQ_SIDECAR_SPOT_RATE -> MarketMoveDirection.DOWN
            else -> null
        }
    }

    private fun releaseSidecar(
        state: KrxSidecarState,
        at: Instant,
        reason: KrxSidecarReleaseReason,
    ): KrxSidecarTransition = KrxSidecarTransition(
        state.copy(
            phase = KrxSidecarPhase.FINISHED_FOR_DAY,
            releaseOnCircuitBreakerResume = false,
            releasedAt = at,
            releaseReason = reason,
        ),
        KrxSidecarEvent.RELEASED,
    )

    fun initialKrxVi(stockId: String, market: Market): KrxViState = KrxViState(stockId = stockId, market = market)

    /** Returns the official KRX rate, or null when that VI kind does not apply to the session. */
    fun krxViRate(
        kind: KrxViKind,
        productClass: KrxViProductClass,
        session: KrxViSession,
        isEquityDerivativesExpirationClosingAuction: Boolean = false,
    ): Double? {
        if (kind == KrxViKind.STATIC) {
            return if (session == KrxViSession.AFTER_HOURS_PERIODIC_CALL_AUCTION) null
            else TradingProtectionRules.KRX_VI_STATIC_RATE
        }
        if (session == KrxViSession.OPENING_CALL_AUCTION) return null
        if (session == KrxViSession.CLOSING_CALL_AUCTION && isEquityDerivativesExpirationClosingAuction) {
            return TradingProtectionRules.KRX_VI_DERIVATIVES_EXPIRATION_CLOSE_RATE
        }
        val lower = productClass.usesLowerDynamicRate
        return when (session) {
            KrxViSession.CONTINUOUS_AUCTION,
            KrxViSession.AFTER_HOURS_PERIODIC_CALL_AUCTION,
            -> if (lower) 0.03 else 0.06
            KrxViSession.CLOSING_CALL_AUCTION -> if (lower) 0.02 else 0.04
            KrxViSession.OPENING_CALL_AUCTION -> null
        }
    }

    fun evaluateKrxVi(state: KrxViState, observation: KrxViObservation): KrxViTransition {
        require(state.stockId == observation.stockId && state.market == observation.market)
        var current = state
        var completed = false
        if (current.phase == KrxViPhase.CALL_AUCTION && observation.observedAt >= requireNotNull(current.auctionEndsAt)) {
            current = idleVi(current)
            completed = true
        }
        if (observation.circuitBreakerPhase != KrxCircuitBreakerPhase.NORMAL) {
            return if (current.phase == KrxViPhase.CALL_AUCTION) {
                KrxViTransition(idleVi(current), KrxViEvent.CANCELLED_BY_MARKET_CIRCUIT_BREAKER)
            } else {
                KrxViTransition(current, if (completed) KrxViEvent.AUCTION_COMPLETED else KrxViEvent.NONE)
            }
        }
        if (current.phase == KrxViPhase.CALL_AUCTION || observation.isViExcluded) {
            return KrxViTransition(current, if (completed) KrxViEvent.AUCTION_COMPLETED else KrxViEvent.NONE)
        }
        val rate = krxViRate(
            observation.kind,
            observation.productClass,
            observation.session,
            observation.isEquityDerivativesExpirationClosingAuction,
        ) ?: return KrxViTransition(current, if (completed) KrxViEvent.AUCTION_COMPLETED else KrxViEvent.NONE)
        val upper = observation.referencePrice * (1.0 + rate)
        val lower = observation.referencePrice * (1.0 - rate)
        val direction = when {
            observation.potentialExecutionPrice + RATE_EPSILON >= upper -> KrxViDirection.UPPER
            observation.potentialExecutionPrice - RATE_EPSILON <= lower -> KrxViDirection.LOWER
            else -> null
        } ?: return KrxViTransition(current, if (completed) KrxViEvent.AUCTION_COMPLETED else KrxViEvent.NONE)

        val auctionBase = observation.existingCallAuctionEndsAt ?: observation.observedAt
        return KrxViTransition(
            state = current.copy(
                phase = KrxViPhase.CALL_AUCTION,
                kind = observation.kind,
                session = observation.session,
                referencePrice = observation.referencePrice,
                triggerRate = rate,
                direction = direction,
                triggeredAt = observation.observedAt,
                auctionEndsAt = auctionBase + TradingProtectionRules.KRX_VI_CALL_AUCTION,
                triggerCount = current.triggerCount + 1,
            ),
            event = KrxViEvent.TRIGGERED,
            triggeringQuotationDisposition = TriggeringQuotationDisposition.NOT_EXECUTED_AND_ENTERED_INTO_CALL_AUCTION,
        )
    }

    private fun idleVi(state: KrxViState): KrxViState = state.copy(
        phase = KrxViPhase.IDLE,
        kind = null,
        session = null,
        referencePrice = null,
        triggerRate = null,
        direction = null,
        triggeredAt = null,
        auctionEndsAt = null,
    )

    fun startInstrumentTradingHalt(
        occurrenceId: String,
        stockId: String,
        reason: TradingHaltReason,
        detail: String,
        startedAt: Instant,
        scheduledReleaseAt: Instant? = null,
        policy: TradingHaltOrderPolicy = defaultTradingHaltPolicy(reason),
    ): InstrumentTradingHalt = InstrumentTradingHalt(
        occurrenceId = occurrenceId,
        stockId = stockId,
        reason = reason,
        detail = detail,
        startedAt = startedAt,
        policy = policy,
        scheduledReleaseAt = scheduledReleaseAt,
    )

    fun releaseInstrumentTradingHalt(
        state: InstrumentTradingHalt,
        releasedAt: Instant,
        releaseNote: String,
    ): InstrumentTradingHalt {
        require(state.status == TradingHaltStatus.ACTIVE)
        require(releasedAt >= state.startedAt)
        require(releaseNote.isNotBlank())
        return state.copy(
            status = TradingHaltStatus.RELEASED,
            releasedAt = releasedAt,
            releaseNote = releaseNote,
        )
    }

    /** Generic default; venue/listing rules may pass a stricter explicit policy. */
    fun defaultTradingHaltPolicy(reason: TradingHaltReason): TradingHaltOrderPolicy = when (reason) {
        TradingHaltReason.DELISTING_PROCESS -> TradingHaltOrderPolicy(
            acceptsNewOrders = false,
            allowsCancellation = false,
            allowsExecution = false,
        )
        else -> TradingHaltOrderPolicy(
            acceptsNewOrders = false,
            allowsCancellation = true,
            allowsExecution = false,
        )
    }

    /** The active interval is half-open: [startedAt, scheduledReleaseAt). */
    fun isInstrumentHaltActive(state: InstrumentTradingHalt, at: Instant): Boolean =
        state.status == TradingHaltStatus.ACTIVE &&
            at >= state.startedAt &&
            (state.scheduledReleaseAt == null || at < state.scheduledReleaseAt)

    fun designateInvestmentAlert(
        candidate: InvestmentAlertDesignation,
        previous: InvestmentAlertDesignation? = null,
    ): InvestmentAlertDesignation {
        require(candidate.status == InvestmentAlertStatus.ACTIVE)
        val isRedesignation = candidate.isRedesignation ||
            candidate.level == InvestmentAlertLevel.WARNING &&
            previous?.redesignationWindow?.contains(candidate.designatedOn) == true
        return candidate.copy(isRedesignation = isRedesignation)
    }

    fun canReleaseInvestmentAlert(state: InvestmentAlertDesignation, onDate: LocalDate): Boolean =
        state.status == InvestmentAlertStatus.ACTIVE && onDate in state.releaseReviewWindow

    fun releaseInvestmentAlert(
        state: InvestmentAlertDesignation,
        releasedAt: Instant,
        releasedOn: LocalDate,
        reason: String,
        criteriaCleared: Boolean,
        force: Boolean = false,
        releaseEffectiveOn: LocalDate = releasedOn,
    ): InvestmentAlertDesignation {
        require(state.status == InvestmentAlertStatus.ACTIVE)
        require(reason.isNotBlank())
        require(force || criteriaCleared) { "해제 기준 충족 또는 강제 해제 사유가 필요합니다." }
        require(force || releasedOn in state.releaseReviewWindow) { "지정된 거래일 해제 심사 구간이 아닙니다." }
        return state.copy(
            status = InvestmentAlertStatus.RELEASED,
            releasedAt = releasedAt,
            releasedOn = releasedOn,
            releaseEffectiveOn = releaseEffectiveOn,
            releaseReason = reason,
        )
    }

    fun initialUsMwcb(tradingDate: LocalDate, at: Instant): UsMwcbState = UsMwcbState(
        tradingDate = tradingDate,
        venueStatuses = US_MARKETS.associateWith { market ->
            UsMwcbVenueStatus(market, UsMwcbVenuePhase.CONTINUOUS, at)
        },
    )

    fun evaluateUsMwcb(state: UsMwcbState, observation: UsMwcbObservation): UsMwcbTransition {
        var current = if (state.tradingDate == observation.tradingDate) {
            state
        } else {
            initialUsMwcb(observation.tradingDate, observation.observedAt)
        }
        val reset = state.tradingDate != observation.tradingDate
        val advanced = advanceUsMwcb(current, observation.observedAt)
        current = advanced.state
        if (current.phase != UsMwcbPhase.NORMAL) {
            return if (advanced.event != UsMwcbEvent.NONE) advanced else UsMwcbTransition(
                current,
                if (reset) UsMwcbEvent.SESSION_RESET else UsMwcbEvent.NONE,
            )
        }

        val decline = observation.declineRate
        val beforeCutoff = observation.easternTime >= TradingProtectionRules.US_REGULAR_OPEN &&
            observation.easternTime < TradingProtectionRules.usMwcbLevel12Cutoff(
                observation.regularSessionClose,
            )
        val candidate = when {
            decline + RATE_EPSILON >= TradingProtectionRules.US_MWCB_LEVEL_3_DECLINE -> UsMwcbLevel.LEVEL_3
            beforeCutoff && UsMwcbLevel.LEVEL_1 !in current.triggeredLevels &&
                decline + RATE_EPSILON >= TradingProtectionRules.US_MWCB_LEVEL_1_DECLINE -> UsMwcbLevel.LEVEL_1
            beforeCutoff && UsMwcbLevel.LEVEL_1 in current.triggeredLevels &&
                UsMwcbLevel.LEVEL_2 !in current.triggeredLevels &&
                decline + RATE_EPSILON >= TradingProtectionRules.US_MWCB_LEVEL_2_DECLINE -> UsMwcbLevel.LEVEL_2
            else -> null
        } ?: return UsMwcbTransition(current, if (reset) UsMwcbEvent.SESSION_RESET else UsMwcbEvent.NONE)

        val triggered = current.triggeredLevels + candidate
        return if (candidate == UsMwcbLevel.LEVEL_3) {
            UsMwcbTransition(
                current.copy(
                    phase = UsMwcbPhase.CLOSED_FOR_DAY,
                    triggeredLevels = triggered,
                    activeLevel = candidate,
                    triggeredAt = observation.observedAt,
                    haltEndsAt = null,
                    venueStatuses = current.venueStatuses.mapValues { (market, _) ->
                        UsMwcbVenueStatus(market, UsMwcbVenuePhase.CLOSED, observation.observedAt)
                    },
                ),
                UsMwcbEvent.LEVEL_3_TRIGGERED,
            )
        } else {
            UsMwcbTransition(
                current.copy(
                    phase = UsMwcbPhase.HALTED,
                    triggeredLevels = triggered,
                    activeLevel = candidate,
                    triggeredAt = observation.observedAt,
                    haltEndsAt = observation.observedAt + TradingProtectionRules.US_MWCB_HALT,
                    venueStatuses = current.venueStatuses.mapValues { (market, _) ->
                        UsMwcbVenueStatus(market, UsMwcbVenuePhase.HALTED, observation.observedAt)
                    },
                ),
                if (candidate == UsMwcbLevel.LEVEL_1) UsMwcbEvent.LEVEL_1_TRIGGERED else UsMwcbEvent.LEVEL_2_TRIGGERED,
            )
        }
    }

    fun advanceUsMwcb(state: UsMwcbState, at: Instant): UsMwcbTransition {
        if (state.phase != UsMwcbPhase.HALTED || at < requireNotNull(state.haltEndsAt)) return UsMwcbTransition(state)
        return UsMwcbTransition(
            state.copy(
                phase = UsMwcbPhase.REOPENING_AUCTIONS,
                venueStatuses = state.venueStatuses.mapValues { (market, _) ->
                    UsMwcbVenueStatus(market, UsMwcbVenuePhase.REOPENING_AUCTION, at)
                },
            ),
            UsMwcbEvent.REOPENING_AUCTIONS_STARTED,
        )
    }

    /**
     * Official rules reopen primary-listed securities through venue auctions. The simulator must
     * acknowledge each venue; there is deliberately no invented fixed auction duration.
     */
    fun completeUsMwcbVenueReopening(
        state: UsMwcbState,
        market: Market,
        at: Instant,
    ): UsMwcbTransition {
        require(state.phase == UsMwcbPhase.REOPENING_AUCTIONS)
        require(market in state.venueStatuses)
        val statuses = state.venueStatuses + (market to UsMwcbVenueStatus(market, UsMwcbVenuePhase.REOPENED, at))
        val complete = statuses.values.all { it.phase == UsMwcbVenuePhase.REOPENED }
        return UsMwcbTransition(
            state.copy(
                phase = if (complete) UsMwcbPhase.NORMAL else UsMwcbPhase.REOPENING_AUCTIONS,
                activeLevel = if (complete) null else state.activeLevel,
                triggeredAt = if (complete) null else state.triggeredAt,
                haltEndsAt = if (complete) null else state.haltEndsAt,
                venueStatuses = statuses,
            ),
            if (complete) UsMwcbEvent.ALL_VENUES_REOPENED else UsMwcbEvent.VENUE_REOPENED,
        )
    }

    fun initialUsLuld(
        stockId: String,
        primaryMarket: Market,
        tradingDate: LocalDate,
        tier: UsLuldTier,
        previousClose: Double,
        referencePrice: Double,
        referencePriceEffectiveAt: Instant,
        easternTime: LocalTime,
        regularSessionClose: LocalTime?,
    ): UsLuldState {
        val canonicalPreviousClose = canonicalUsLuldPreviousClose(previousClose)
        val bands = calculateUsLuldBands(
            tier,
            canonicalPreviousClose,
            referencePrice,
            easternTime,
            regularSessionClose,
        )
        return UsLuldState(
            stockId = stockId,
            primaryMarket = primaryMarket,
            tradingDate = tradingDate,
            tier = tier,
            previousClose = canonicalPreviousClose,
            referencePrice = bands.referencePrice,
            referencePriceEffectiveAt = referencePriceEffectiveAt,
            bands = bands,
        )
    }

    /** Official LULD tier/price buckets, including the scheduled close's final 25 minutes. */
    fun calculateUsLuldBands(
        tier: UsLuldTier,
        previousClose: Double,
        referencePrice: Double,
        easternTime: LocalTime,
        regularSessionClose: LocalTime?,
    ): UsLuldBands {
        val minimumPrice = MarketMicrostructure.minimumPrice(Market.NYSE)
        require(previousClose >= minimumPrice && previousClose.isFinite())
        require(referencePrice >= minimumPrice && referencePrice.isFinite())
        val canonicalPreviousClose = canonicalUsLuldPreviousClose(previousClose)
        val canonicalReferencePrice = canonicalUsLuldReferencePrice(referencePrice)
        val closingWindow = regularSessionClose?.let { close ->
            easternTime >= TradingProtectionRules.usLuldDoubledBandsFrom(close)
        } == true
        val baseAmount = when {
            canonicalPreviousClose < 0.75 -> min(0.15, canonicalReferencePrice * 0.75)
            canonicalPreviousClose <= 3.0 -> canonicalReferencePrice * 0.20
            tier == UsLuldTier.TIER_1 -> canonicalReferencePrice * 0.05
            else -> canonicalReferencePrice * 0.10
        }
        // The current Plan doubles Tier 1 and sub-$3 Tier 2 bands in the final 25 minutes.
        val doubled = closingWindow && (tier == UsLuldTier.TIER_1 || canonicalPreviousClose <= 3.0)
        val amount = if (doubled) baseAmount * 2.0 else baseAmount
        val referenceTick = MarketMicrostructure.tickSize(Market.NYSE, canonicalReferencePrice)
        val lower = MarketMicrostructure.roundDown(
            market = Market.NYSE,
            price = min(
                canonicalReferencePrice - amount,
                canonicalReferencePrice - referenceTick,
            )
                .coerceAtLeast(minimumPrice),
        )
        val upper = MarketMicrostructure.roundUp(
            market = Market.NYSE,
            price = max(
                canonicalReferencePrice + amount,
                canonicalReferencePrice + referenceTick,
            ),
        )
        return UsLuldBands(
            referencePrice = canonicalReferencePrice,
            lower = lower,
            upper = upper,
            bandAmount = amount,
            doubledForClosingWindow = doubled,
        )
    }

    /**
     * Updates a normal-session reference only from a sample window wholly covered by the current
     * reference-price regime. After an opening or reopening, the LULD Plan excludes earlier trades
     * from the pro-forma reference price, so a window crossing [state.referencePriceEffectiveAt]
     * is not eligible.
     */
    fun updateUsLuldReferencePrice(
        state: UsLuldState,
        candidateFiveMinuteMean: Double,
        candidateWindowStartsAt: Instant,
        at: Instant,
        easternTime: LocalTime,
        regularSessionClose: LocalTime?,
    ): UsLuldTransition {
        require(
            candidateFiveMinuteMean >= MarketMicrostructure.minimumPrice(Market.NYSE) &&
                candidateFiveMinuteMean.isFinite(),
        )
        require(candidateWindowStartsAt <= at)
        val current = state.withUsLuldBandsFor(easternTime, regularSessionClose)
        if (current.phase != UsLuldPhase.NORMAL) return UsLuldTransition(current)
        if (candidateWindowStartsAt < current.referencePriceEffectiveAt) return UsLuldTransition(current)
        if (at - current.referencePriceEffectiveAt < TradingProtectionRules.US_LULD_REFERENCE_MINIMUM_AGE) {
            return UsLuldTransition(current)
        }
        val nextBands = calculateUsLuldBands(
            current.tier,
            current.previousClose,
            candidateFiveMinuteMean,
            easternTime,
            regularSessionClose,
        )
        val canonicalCandidate = nextBands.referencePrice
        val change = abs(canonicalCandidate / current.referencePrice - 1.0)
        if (change + RATE_EPSILON < TradingProtectionRules.US_LULD_REFERENCE_MINIMUM_CHANGE) {
            return UsLuldTransition(current)
        }
        return UsLuldTransition(
            current.copy(
                referencePrice = canonicalCandidate,
                referencePriceEffectiveAt = at,
                bands = nextBands,
            ),
            UsLuldEvent.REFERENCE_PRICE_UPDATED,
        )
    }

    fun evaluateUsLuld(state: UsLuldState, observation: UsLuldObservation): UsLuldTransition {
        val current = state.withUsLuldBandsFor(
            easternTime = observation.easternTime,
            regularSessionClose = observation.regularSessionClose,
        )
        return when (current.phase) {
            UsLuldPhase.NORMAL -> {
                val side = observation.limitSide
                if (side == null) UsLuldTransition(current) else {
                    UsLuldTransition(
                        current.copy(
                            phase = UsLuldPhase.LIMIT_STATE,
                            limitSide = side,
                            limitStateStartedAt = observation.observedAt,
                            limitStateDeadline =
                                observation.observedAt + TradingProtectionRules.US_LULD_LIMIT_STATE,
                        ),
                        UsLuldEvent.LIMIT_STATE_ENTERED,
                    )
                }
            }
            UsLuldPhase.LIMIT_STATE -> {
                val deadline = requireNotNull(current.limitStateDeadline)
                if (observation.allLimitStateQuotationsCleared && observation.observedAt < deadline) {
                    UsLuldTransition(clearLuldTransientState(current), UsLuldEvent.LIMIT_STATE_CLEARED)
                } else if (observation.observedAt >= deadline) {
                    if (observation.isInUsLuldCloseOnlyWindow()) {
                        UsLuldTransition(
                            current.copy(
                                phase = UsLuldPhase.CLOSING_AUCTION_ONLY,
                                pauseStartedAt = deadline,
                                pauseEndsAt = null,
                            ),
                            UsLuldEvent.CLOSING_AUCTION_ONLY,
                        )
                    } else {
                        UsLuldTransition(
                            current.copy(
                                phase = UsLuldPhase.TRADING_PAUSE,
                                pauseStartedAt = deadline,
                                pauseEndsAt = deadline + TradingProtectionRules.US_LULD_PAUSE,
                            ),
                            UsLuldEvent.TRADING_PAUSE_STARTED,
                        )
                    }
                } else {
                    UsLuldTransition(current)
                }
            }
            UsLuldPhase.TRADING_PAUSE -> when {
                observation.isInUsLuldCloseOnlyWindow() -> UsLuldTransition(
                    current.copy(phase = UsLuldPhase.CLOSING_AUCTION_ONLY, pauseEndsAt = null),
                    UsLuldEvent.CLOSING_AUCTION_ONLY,
                )
                observation.observedAt >= requireNotNull(current.pauseEndsAt) -> UsLuldTransition(
                    current.copy(
                        phase = UsLuldPhase.REOPENING_AUCTION,
                        reopeningStartedAt = current.pauseEndsAt,
                    ),
                    UsLuldEvent.REOPENING_AUCTION_STARTED,
                )
                else -> UsLuldTransition(current)
            }
            UsLuldPhase.REOPENING_AUCTION,
            UsLuldPhase.CLOSING_AUCTION_ONLY,
            UsLuldPhase.CLOSED_FOR_DAY,
            -> UsLuldTransition(current)
        }
    }

    fun extendUsLuldPause(state: UsLuldState): UsLuldTransition {
        require(state.phase == UsLuldPhase.TRADING_PAUSE)
        require(state.pauseExtensionCount == 0) { "LULD 거래정지는 한 번만 5분 연장할 수 있습니다." }
        return UsLuldTransition(
            state.copy(
                pauseEndsAt = requireNotNull(state.pauseEndsAt) + TradingProtectionRules.US_LULD_OPTIONAL_EXTENSION,
                pauseExtensionCount = 1,
            ),
            UsLuldEvent.TRADING_PAUSE_EXTENDED,
        )
    }

    fun completeUsLuldReopening(
        state: UsLuldState,
        reopeningPrice: Double,
        at: Instant,
        easternTime: LocalTime,
        regularSessionClose: LocalTime?,
    ): UsLuldTransition {
        require(state.phase == UsLuldPhase.REOPENING_AUCTION)
        require(
            reopeningPrice >= MarketMicrostructure.minimumPrice(Market.NYSE) &&
                reopeningPrice.isFinite(),
        )
        val nextBands = calculateUsLuldBands(
            state.tier,
            state.previousClose,
            reopeningPrice,
            easternTime,
            regularSessionClose,
        )
        return UsLuldTransition(
            clearLuldTransientState(state).copy(
                referencePrice = nextBands.referencePrice,
                referencePriceEffectiveAt = at,
                bands = nextBands,
            ),
            UsLuldEvent.REOPENED,
        )
    }

    fun closeUsLuldSession(state: UsLuldState): UsLuldTransition {
        require(state.phase !in setOf(UsLuldPhase.NORMAL, UsLuldPhase.CLOSED_FOR_DAY))
        return UsLuldTransition(state.copy(phase = UsLuldPhase.CLOSED_FOR_DAY), UsLuldEvent.SESSION_CLOSED)
    }

    private fun clearLuldTransientState(state: UsLuldState): UsLuldState = state.copy(
        phase = UsLuldPhase.NORMAL,
        limitSide = null,
        limitStateStartedAt = null,
        limitStateDeadline = null,
        pauseStartedAt = null,
        pauseEndsAt = null,
        pauseExtensionCount = 0,
        reopeningStartedAt = null,
    )

    private fun UsLuldObservation.isInUsLuldCloseOnlyWindow(): Boolean =
        regularSessionClose?.let { close ->
            easternTime >= TradingProtectionRules.usLuldCloseOnlyFrom(close)
        } == true

    private fun UsLuldState.withUsLuldBandsFor(
        easternTime: LocalTime,
        regularSessionClose: LocalTime?,
    ): UsLuldState {
        val canonicalBands = calculateUsLuldBands(
            tier = tier,
            previousClose = previousClose,
            referencePrice = referencePrice,
            easternTime = easternTime,
            regularSessionClose = regularSessionClose,
        )
        return if (bands == canonicalBands) this else copy(bands = canonicalBands)
    }

    private fun canonicalUsLuldPreviousClose(price: Double): Double {
        val minimumPrice = MarketMicrostructure.minimumPrice(Market.NYSE)
        require(price >= minimumPrice && price.isFinite())
        return MarketMicrostructure.roundNearest(Market.NYSE, price)
    }

    /**
     * The strict band invariant needs one executable tick below the reference. At the absolute
     * $0.0001 quotation floor, $0.0002 is therefore the smallest representable LULD reference;
     * this replaces the former, economically distorting one-cent floor.
     */
    private fun canonicalUsLuldReferencePrice(price: Double): Double {
        val minimumPrice = MarketMicrostructure.minimumPrice(Market.NYSE)
        require(price >= minimumPrice && price.isFinite())
        val minimumReference = minimumPrice + MarketMicrostructure.tickSize(Market.NYSE, minimumPrice)
        return MarketMicrostructure.roundUp(Market.NYSE, price.coerceAtLeast(minimumReference))
    }

    /**
     * One query for every order/matching path. Restrictions are ordered by precedence.
     * A KRX market CB suppresses VI/sidecar checks because KRX cancels overlapping price
     * stabilization facilities when the market-wide CB takes effect.
     */
    fun permission(
        snapshot: TradingProtectionSnapshot,
        request: TradingProtectionRequest,
        at: Instant,
    ): TradingPermissionDecision {
        val restrictions = mutableListOf<TradingRestriction>()
        var executionMode = TradingExecutionMode.CONTINUOUS

        val krxCb = snapshot.krxCircuitBreakers[request.market]
        val krxCbPhase = krxCb?.let { effectiveKrxCircuitBreakerPhase(it, at) }
        val krxMarketProtectionActive = krxCbPhase != null && krxCbPhase != KrxCircuitBreakerPhase.NORMAL
        if (krxMarketProtectionActive) {
            val activePhase = requireNotNull(krxCbPhase)
            executionMode = krxCircuitBreakerExecutionMode(activePhase)
            val allowed = krxCircuitBreakerAllows(activePhase, request.action)
            if (!allowed) restrictions += TradingRestriction(
                source = TradingRestrictionSource.KRX_MARKET_CIRCUIT_BREAKER,
                code = "KRX_CB_${activePhase.name}",
                message = krxCircuitBreakerMessage(activePhase),
                endsAt = krxCb.reopeningEndsAt,
            )
        }

        val usMwcb = snapshot.usMarketWideCircuitBreaker
        if (request.market.isUnitedStates && usMwcb != null) {
            val phase = effectiveUsMwcbPhase(usMwcb, at)
            if (phase != UsMwcbPhase.NORMAL) {
                val venuePhase = effectiveUsMwcbVenuePhase(usMwcb, request.market, at)
                executionMode = if (venuePhase == UsMwcbVenuePhase.REOPENED) {
                    TradingExecutionMode.CONTINUOUS
                } else {
                    usMwcbExecutionMode(phase)
                }
                val allowed = when {
                    phase == UsMwcbPhase.CLOSED_FOR_DAY -> false
                    venuePhase == UsMwcbVenuePhase.REOPENED -> true
                    request.action == TradingProtectionAction.CANCEL_ORDER -> true
                    request.action == TradingProtectionAction.SUBMIT_ORDER && request.isAuctionEligibleOrder -> true
                    else -> false
                }
                if (!allowed) restrictions += TradingRestriction(
                    source = TradingRestrictionSource.US_MARKET_WIDE_CIRCUIT_BREAKER,
                    code = "US_MWCB_${phase.name}",
                    message = if (phase == UsMwcbPhase.CLOSED_FOR_DAY) {
                        "미국 시장 3단계 서킷브레이커로 오늘 거래가 종료됐어요."
                    } else {
                        "미국 시장 서킷브레이커가 적용 중이에요. 재개 경매가 끝날 때까지 체결이 멈춰요."
                    },
                    endsAt = usMwcb.haltEndsAt,
                )
            }
        }

        val stockId = request.stockId
        if (stockId != null) {
            val effectiveHalts = buildList {
                snapshot.instrumentTradingHalts[stockId]?.let(::add)
                snapshot.scheduledInstrumentTradingHalts.values
                    .filterTo(this) { it.stockId == stockId }
            }.distinct().filter { isInstrumentHaltActive(it, at) }
            for (halt in effectiveHalts) {
                executionMode = moreRestrictive(executionMode, TradingExecutionMode.PAUSED)
                val allowed = when (request.action) {
                    TradingProtectionAction.SUBMIT_ORDER -> halt.policy.acceptsNewOrders
                    TradingProtectionAction.CANCEL_ORDER -> halt.policy.allowsCancellation
                    TradingProtectionAction.EXECUTE_TRADE -> halt.policy.allowsExecution
                    TradingProtectionAction.PROGRAM_TRADE_FLOW -> halt.policy.allowsExecution
                    TradingProtectionAction.CONTINUOUS_TRADING -> halt.policy.allowsContinuousTrading
                }
                if (!allowed) restrictions += TradingRestriction(
                    TradingRestrictionSource.INSTRUMENT_TRADING_HALT,
                    "HALT_${halt.reason.name}",
                    "이 종목은 ${halt.detail} 사유로 거래가 정지됐어요.",
                    halt.scheduledReleaseAt,
                )
            }

            if (!krxMarketProtectionActive) {
                val vi = snapshot.krxVolatilityInterruptions[stockId]
                if (vi != null && vi.phase == KrxViPhase.CALL_AUCTION && at < requireNotNull(vi.auctionEndsAt)) {
                    executionMode = moreRestrictive(executionMode, TradingExecutionMode.CALL_AUCTION_ONLY)
                    val allowed = request.action == TradingProtectionAction.SUBMIT_ORDER ||
                        request.action == TradingProtectionAction.CANCEL_ORDER
                    if (!allowed) restrictions += TradingRestriction(
                        TradingRestrictionSource.KRX_VOLATILITY_INTERRUPTION,
                        "KRX_VI_${vi.kind!!.name}",
                        "변동성 완화장치가 발동해 2분 단일가로 전환됐어요.",
                        vi.auctionEndsAt,
                    )
                }
            }

            val luld = snapshot.usLuldStates[stockId]
            if (luld != null) {
                val phase = effectiveUsLuldPhase(luld, at)
                val mode = when (phase) {
                    UsLuldPhase.NORMAL, UsLuldPhase.LIMIT_STATE -> TradingExecutionMode.CONTINUOUS
                    UsLuldPhase.TRADING_PAUSE -> TradingExecutionMode.PAUSED
                    UsLuldPhase.REOPENING_AUCTION -> TradingExecutionMode.CALL_AUCTION_ONLY
                    UsLuldPhase.CLOSING_AUCTION_ONLY -> TradingExecutionMode.CLOSING_AUCTION_ONLY
                    UsLuldPhase.CLOSED_FOR_DAY -> TradingExecutionMode.CLOSED
                }
                executionMode = moreRestrictive(executionMode, mode)
                val allowed = when (phase) {
                    UsLuldPhase.NORMAL, UsLuldPhase.LIMIT_STATE -> {
                        request.action != TradingProtectionAction.EXECUTE_TRADE ||
                            request.proposedExecutionPrice?.let { it in luld.bands } == true
                    }
                    UsLuldPhase.TRADING_PAUSE, UsLuldPhase.REOPENING_AUCTION ->
                        request.action == TradingProtectionAction.SUBMIT_ORDER ||
                            request.action == TradingProtectionAction.CANCEL_ORDER
                    UsLuldPhase.CLOSING_AUCTION_ONLY ->
                        request.action == TradingProtectionAction.CANCEL_ORDER ||
                            (request.action == TradingProtectionAction.SUBMIT_ORDER && request.isAuctionEligibleOrder)
                    UsLuldPhase.CLOSED_FOR_DAY -> false
                }
                if (!allowed) restrictions += TradingRestriction(
                    TradingRestrictionSource.US_LIMIT_UP_LIMIT_DOWN,
                    "US_LULD_${phase.name}",
                    when (phase) {
                        UsLuldPhase.NORMAL, UsLuldPhase.LIMIT_STATE -> "LULD 가격 범위를 벗어나 체결할 수 없어요."
                        UsLuldPhase.CLOSING_AUCTION_ONLY -> "LULD 종가 경매만 진행 중이에요."
                        UsLuldPhase.CLOSED_FOR_DAY -> "이 종목의 정규 거래가 종료됐어요."
                        else -> "급격한 가격 변동으로 이 종목의 거래가 잠시 멈췄어요."
                    },
                    luld.pauseEndsAt,
                )
            }
        }

        if (!krxMarketProtectionActive && request.isProgramOrder) {
            val sidecar = snapshot.krxSidecars[request.market]
            if (sidecar != null && isSidecarSuspended(sidecar, at) &&
                sidecar.suspendedProgramSide == request.programOrderSide
            ) {
                val allowed = request.action == TradingProtectionAction.SUBMIT_ORDER ||
                    request.action == TradingProtectionAction.CONTINUOUS_TRADING
                if (!allowed) restrictions += TradingRestriction(
                    TradingRestrictionSource.KRX_SIDECAR,
                    "KRX_SIDECAR_${sidecar.triggeredDirection!!.name}",
                    "사이드카로 해당 방향 프로그램매매 호가의 효력이 잠시 정지됐어요.",
                    sidecar.suspensionEndsAt,
                )
            }
        }

        return TradingPermissionDecision(
            allowed = restrictions.isEmpty(),
            executionMode = executionMode,
            controllingRestriction = restrictions.firstOrNull(),
            restrictions = restrictions,
        )
    }

    private fun effectiveKrxCircuitBreakerPhase(state: KrxCircuitBreakerState, at: Instant): KrxCircuitBreakerPhase =
        when (state.phase) {
            KrxCircuitBreakerPhase.HALTED -> when {
                at >= requireNotNull(state.reopeningEndsAt) -> KrxCircuitBreakerPhase.NORMAL
                at >= requireNotNull(state.haltEndsAt) -> KrxCircuitBreakerPhase.REOPENING_CALL_AUCTION
                else -> state.phase
            }
            KrxCircuitBreakerPhase.REOPENING_CALL_AUCTION ->
                if (at >= requireNotNull(state.reopeningEndsAt)) KrxCircuitBreakerPhase.NORMAL else state.phase
            else -> state.phase
        }

    private fun krxCircuitBreakerExecutionMode(phase: KrxCircuitBreakerPhase): TradingExecutionMode = when (phase) {
        KrxCircuitBreakerPhase.NORMAL -> TradingExecutionMode.CONTINUOUS
        KrxCircuitBreakerPhase.HALTED -> TradingExecutionMode.PAUSED
        KrxCircuitBreakerPhase.REOPENING_CALL_AUCTION -> TradingExecutionMode.CALL_AUCTION_ONLY
        KrxCircuitBreakerPhase.CLOSED_FOR_DAY -> TradingExecutionMode.CLOSED
    }

    private fun krxCircuitBreakerAllows(
        phase: KrxCircuitBreakerPhase,
        action: TradingProtectionAction,
    ): Boolean = when (phase) {
        KrxCircuitBreakerPhase.NORMAL -> true
        KrxCircuitBreakerPhase.HALTED -> action == TradingProtectionAction.CANCEL_ORDER
        KrxCircuitBreakerPhase.REOPENING_CALL_AUCTION ->
            action == TradingProtectionAction.SUBMIT_ORDER || action == TradingProtectionAction.CANCEL_ORDER
        KrxCircuitBreakerPhase.CLOSED_FOR_DAY -> false
    }

    private fun krxCircuitBreakerMessage(phase: KrxCircuitBreakerPhase): String = when (phase) {
        KrxCircuitBreakerPhase.NORMAL -> "정상 거래 중이에요."
        KrxCircuitBreakerPhase.HALTED -> "시장 서킷브레이커로 거래가 일시 정지됐어요. 기존 주문 취소만 가능해요."
        KrxCircuitBreakerPhase.REOPENING_CALL_AUCTION -> "서킷브레이커 해제 후 단일가 접수 중이에요. 연속 체결은 잠시 뒤 재개돼요."
        KrxCircuitBreakerPhase.CLOSED_FOR_DAY -> "3단계 서킷브레이커로 오늘 시장이 종료됐어요."
    }

    private fun usMwcbExecutionMode(phase: UsMwcbPhase): TradingExecutionMode = when (phase) {
        UsMwcbPhase.NORMAL -> TradingExecutionMode.CONTINUOUS
        UsMwcbPhase.HALTED -> TradingExecutionMode.PAUSED
        UsMwcbPhase.REOPENING_AUCTIONS -> TradingExecutionMode.CALL_AUCTION_ONLY
        UsMwcbPhase.CLOSED_FOR_DAY -> TradingExecutionMode.CLOSED
    }

    private fun effectiveUsMwcbPhase(state: UsMwcbState, at: Instant): UsMwcbPhase =
        if (state.phase == UsMwcbPhase.HALTED && at >= requireNotNull(state.haltEndsAt)) {
            UsMwcbPhase.REOPENING_AUCTIONS
        } else {
            state.phase
        }

    private fun effectiveUsMwcbVenuePhase(state: UsMwcbState, market: Market, at: Instant): UsMwcbVenuePhase {
        val stored = state.venueStatuses[market]?.phase ?: UsMwcbVenuePhase.CONTINUOUS
        return if (state.phase == UsMwcbPhase.HALTED && at >= requireNotNull(state.haltEndsAt) && stored == UsMwcbVenuePhase.HALTED) {
            UsMwcbVenuePhase.REOPENING_AUCTION
        } else {
            stored
        }
    }

    private fun effectiveUsLuldPhase(state: UsLuldState, at: Instant): UsLuldPhase = when (state.phase) {
        UsLuldPhase.LIMIT_STATE ->
            if (at >= requireNotNull(state.limitStateDeadline)) UsLuldPhase.TRADING_PAUSE else state.phase
        UsLuldPhase.TRADING_PAUSE ->
            if (at >= requireNotNull(state.pauseEndsAt)) UsLuldPhase.REOPENING_AUCTION else state.phase
        else -> state.phase
    }

    private fun isSidecarSuspended(state: KrxSidecarState, at: Instant): Boolean =
        state.phase == KrxSidecarPhase.PROGRAM_FLOW_SUSPENDED && at < requireNotNull(state.suspensionEndsAt)

    private fun moreRestrictive(
        first: TradingExecutionMode,
        second: TradingExecutionMode,
    ): TradingExecutionMode = if (executionModePriority(first) >= executionModePriority(second)) first else second

    private fun executionModePriority(mode: TradingExecutionMode): Int = when (mode) {
        TradingExecutionMode.CONTINUOUS -> 0
        TradingExecutionMode.CALL_AUCTION_ONLY -> 1
        TradingExecutionMode.CLOSING_AUCTION_ONLY -> 2
        TradingExecutionMode.PAUSED -> 3
        TradingExecutionMode.CLOSED -> 4
    }

    private val US_MARKETS: List<Market> = Market.entries.filter(Market::isUnitedStates)
}
