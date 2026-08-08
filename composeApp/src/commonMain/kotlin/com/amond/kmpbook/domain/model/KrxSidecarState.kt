package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

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
