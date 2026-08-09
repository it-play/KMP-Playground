package com.amond.kmpbook.domain.model.protection.krx

import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.protection.krx.KrxCircuitBreakerLevel
import com.amond.kmpbook.domain.model.protection.krx.KrxCircuitBreakerPhase
import com.amond.kmpbook.domain.model.protection.krx.KrxCircuitBreakerState
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

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
