package com.amond.kmpbook.domain.simulation.event

import com.amond.kmpbook.domain.model.event.GameEvent
import com.amond.kmpbook.domain.model.event.GameEventImpact
import com.amond.kmpbook.domain.model.event.ImpactDirection
import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.news.isDirectProductImpactFor
import com.amond.kmpbook.domain.model.news.resolvedImpactFor
import com.amond.kmpbook.domain.simulation.causal.MarketContagionEngine
import com.amond.kmpbook.domain.simulation.price.PriceImpulse
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.time.Instant

/** Converts active event level shocks and decaying liquidity effects to one price step. */
object EventShockCalculator {
    fun levelShockAt(event: GameEvent, time: Instant): Double =
        levelShockAt(event, event.impact, time)

    private fun levelShockAt(event: GameEvent, impact: GameEventImpact, time: Instant): Double {
        if (!event.isActiveAt(time)) return 0.0
        val elapsedHours = (time - event.startsAt).inWholeNanoseconds / NANOS_PER_HOUR
        return impact.shockReturn * decayWeight(event, elapsedHours)
    }

    fun returnBetween(event: GameEvent, from: Instant, to: Instant): Double =
        returnBetween(event, event.impact, from, to)

    private fun returnBetween(
        event: GameEvent,
        impact: GameEventImpact,
        from: Instant,
        to: Instant,
    ): Double {
        require(to >= from) { "Event return interval cannot run backwards" }
        if (to == from || to <= event.startsAt || from >= event.endsAt) return 0.0

        // The left boundary represents the state immediately before events at
        // that exact instant, so a newly starting event contributes its shock.
        val fromLevel = if (from <= event.startsAt) 0.0 else levelShockAt(event, impact, from)
        val toLevel = if (to >= event.endsAt) 0.0 else levelShockAt(event, impact, to)
        val levelReturn = (1.0 + toLevel) / (1.0 + fromLevel) - 1.0

        val overlapStart = maxOf(from, event.startsAt)
        val overlapEnd = minOf(to, event.endsAt)
        val overlapHours = if (overlapEnd <= overlapStart) 0.0 else {
            (overlapEnd - overlapStart).inWholeNanoseconds / NANOS_PER_HOUR
        }
        val driftReturn = exp(impact.hourlyDrift * overlapHours) - 1.0
        return ((1.0 + levelReturn) * (1.0 + driftReturn) - 1.0).coerceAtLeast(-0.95)
    }

    fun aggregate(
        events: Iterable<GameEvent>,
        stock: StockDefinition,
        from: Instant,
        to: Instant,
    ): PriceImpulse {
        require(to >= from)
        var returnLogSum = 0.0
        var directProductReturnLogSum = 0.0
        var volatilityLogSum = 0.0
        var volumeLogSum = 0.0

        for (event in events) {
            if (!event.affects(stock)) continue
            val stockSpecificImpact = event.impactFor(stock)
            val eventReturn = returnBetween(event, stockSpecificImpact, from, to)
            returnLogSum += ln(1.0 + eventReturn)
            if (event.isDirectProductImpactFor(stock)) {
                directProductReturnLogSum += ln(1.0 + eventReturn)
            }
            val weight = effectWeightBetween(event, from, to)
            volatilityLogSum += ln(
                1.0 + (stockSpecificImpact.volatilityMultiplier - 1.0) * weight,
            )
            volumeLogSum += ln(1.0 + (stockSpecificImpact.volumeMultiplier - 1.0) * weight)
        }
        val boundedReturnLog = softBoundLogReturn(returnLogSum)
        val boundedDirectProductLog = softBoundLogReturn(directProductReturnLogSum)
        return PriceImpulse(
            returnRate = exp(boundedReturnLog) - 1.0,
            directProductReturnRate = exp(boundedDirectProductLog) - 1.0,
            volatilityMultiplier = exp(
                softBoundSigned(
                    volatilityLogSum,
                    negativeLimit = -ln(MIN_EVENT_VOLATILITY_MULTIPLIER),
                    positiveLimit = ln(MAX_EVENT_VOLATILITY_MULTIPLIER),
                    knee = ln(EVENT_VOLATILITY_SOFT_KNEE),
                ),
            ),
            volumeMultiplier = exp(
                softBoundSigned(
                    volumeLogSum,
                    negativeLimit = -ln(MIN_EVENT_VOLUME_MULTIPLIER),
                    positiveLimit = ln(MAX_EVENT_VOLUME_MULTIPLIER),
                    knee = ln(EVENT_VOLUME_SOFT_KNEE),
                ),
            ),
        )
    }

    /** Instantaneous liquidity used by point-in-time order-book snapshots. */
    fun liquidityMultiplierAt(
        events: Iterable<GameEvent>,
        stock: StockDefinition,
        time: Instant,
    ): Double {
        var logSum = 0.0
        for (event in events) {
            if (!event.affects(stock)) continue
            val weight = effectWeightAt(event, time)
            val stockSpecificImpact = event.impactFor(stock)
            logSum += ln(1.0 + (stockSpecificImpact.liquidityMultiplier - 1.0) * weight)
        }
        return exp(softBoundLiquidityLog(logSum))
    }

    /** Time-weighted liquidity over a simulation interval. */
    fun liquidityMultiplierBetween(
        events: Iterable<GameEvent>,
        stock: StockDefinition,
        from: Instant,
        to: Instant,
    ): Double {
        require(to >= from) { "Event liquidity interval cannot run backwards" }
        var logSum = 0.0
        for (event in events) {
            if (!event.affects(stock)) continue
            val weight = effectWeightBetween(event, from, to)
            val stockSpecificImpact = event.impactFor(stock)
            logSum += ln(1.0 + (stockSpecificImpact.liquidityMultiplier - 1.0) * weight)
        }
        return exp(softBoundLiquidityLog(logSum))
    }

    /**
     * 관련 사건은 로그 공간에서 실제로 누적한다. 다만 시장의 유한한 가격발견·호가흡수
     * 능력을 넘는 구간만 매끄럽게 포화시켜, 임의의 hard clip 경계에서 흐름이 꺾이지 않는다.
     */
    private fun softBoundLogReturn(value: Double): Double = softBoundSigned(
        value = value,
        negativeLimit = -ln(MIN_EVENT_RETURN_FACTOR),
        positiveLimit = ln(MAX_EVENT_RETURN_FACTOR),
        knee = EVENT_RETURN_LOG_SOFT_KNEE,
    )

    private fun softBoundLiquidityLog(value: Double): Double = softBoundSigned(
        value = value,
        negativeLimit = -ln(MIN_EVENT_LIQUIDITY_MULTIPLIER),
        positiveLimit = ln(MAX_EVENT_LIQUIDITY_MULTIPLIER),
        knee = ln(EVENT_LIQUIDITY_SOFT_KNEE),
    )

    private fun softBoundSigned(
        value: Double,
        negativeLimit: Double,
        positiveLimit: Double,
        knee: Double,
    ): Double {
        val limit = if (value < 0.0) negativeLimit else positiveLimit
        val magnitude = kotlin.math.abs(value)
        if (magnitude <= knee) return value
        val remaining = (limit - knee).coerceAtLeast(1e-9)
        val boundedMagnitude = knee + remaining * kotlin.math.tanh((magnitude - knee) / remaining)
        return if (value < 0.0) -boundedMagnitude else boundedMagnitude
    }

    private fun effectWeightAt(event: GameEvent, time: Instant): Double {
        if (!event.isActiveAt(time)) return 0.0
        val elapsedHours = (time - event.startsAt).inWholeNanoseconds / NANOS_PER_HOUR
        return decayWeight(event, elapsedHours)
    }

    /**
     * Average decaying exposure over [from, to). Time outside the event contributes zero, so an
     * event covering only part of a turn is weighted by that exact overlap instead of an endpoint
     * sample. A zero-length interval remains a point-in-time query for deterministic callers.
     */
    private fun effectWeightBetween(event: GameEvent, from: Instant, to: Instant): Double {
        require(to >= from) { "Event effect interval cannot run backwards" }
        if (to == from) return effectWeightAt(event, from)

        val overlapStart = maxOf(from, event.startsAt)
        val overlapEnd = minOf(to, event.endsAt)
        if (overlapEnd <= overlapStart) return 0.0

        val intervalHours = (to - from).inWholeNanoseconds / NANOS_PER_HOUR
        val overlapStartHours = (overlapStart - event.startsAt).inWholeNanoseconds / NANOS_PER_HOUR
        val overlapEndHours = (overlapEnd - event.startsAt).inWholeNanoseconds / NANOS_PER_HOUR
        val halfLife = max(1.0, event.durationHours / 3.0)
        val decayRate = LN_2 / halfLife
        val integratedWeight = (
            exp(-decayRate * overlapStartHours) - exp(-decayRate * overlapEndHours)
            ) / decayRate
        return (integratedWeight / intervalHours).coerceIn(0.0, 1.0)
    }

    /** 가격 방향과 해외 전염 강도를 종목별 충격에 함께 반영한다. */
    private fun GameEvent.impactFor(stock: StockDefinition): GameEventImpact {
        val resolved = resolvedImpactFor(stock)
        fun directional(value: Double): Double = when (resolved.direction) {
            ImpactDirection.POSITIVE -> kotlin.math.abs(value) * resolved.relativeSensitivity
            ImpactDirection.NEGATIVE -> -kotlin.math.abs(value) * resolved.relativeSensitivity
            ImpactDirection.MIXED -> value * resolved.relativeSensitivity
            ImpactDirection.NEUTRAL -> 0.0
        }
        val transmissions = resolved.causalImpact?.marketTransmissionsByFactor.orEmpty().values
        val transmissionScale = transmissions.takeIf { it.isNotEmpty() }?.fold(1.0) { survival, trace ->
            survival * (1.0 - trace.responseIntensity / MarketContagionEngine.MAX_RESPONSE_INTENSITY)
        }?.let { survival ->
            MarketContagionEngine.MAX_RESPONSE_INTENSITY * (1.0 - survival)
        } ?: 1.0
        /** 로그 공간에서 포화해 큰 유한 입력도 Infinity/NaN으로 바뀌지 않게 한다. */
        fun transmittedMultiplier(value: Double, upperBound: Double): Double {
            if (value == 0.0) return 0.0
            val boundedLog = (ln(value) * transmissionScale).coerceAtMost(ln(upperBound))
            return exp(boundedLog).coerceIn(0.0, upperBound)
        }
        return impact.copy(
            shockReturn = directional(impact.shockReturn).coerceAtLeast(-0.95),
            hourlyDrift = directional(impact.hourlyDrift),
            volatilityMultiplier = transmittedMultiplier(impact.volatilityMultiplier, 20.0),
            volumeMultiplier = transmittedMultiplier(impact.volumeMultiplier, 100.0),
            liquidityMultiplier = transmittedMultiplier(impact.liquidityMultiplier, 20.0),
            sentiment = (impact.sentiment * transmissionScale).coerceIn(-1.0, 1.0),
        )
    }

    private fun decayWeight(event: GameEvent, elapsedHours: Double): Double {
        val halfLife = max(1.0, event.durationHours / 3.0)
        return exp(-LN_2 * elapsedHours.coerceAtLeast(0.0) / halfLife)
    }

    private const val NANOS_PER_HOUR: Double = 3_600_000_000_000.0
    private const val MIN_EVENT_RETURN_FACTOR: Double = 0.30
    private const val MAX_EVENT_RETURN_FACTOR: Double = 2.50
    private const val EVENT_RETURN_LOG_SOFT_KNEE: Double = 0.14
    private const val MIN_EVENT_VOLATILITY_MULTIPLIER: Double = 0.35
    private const val MAX_EVENT_VOLATILITY_MULTIPLIER: Double = 5.0
    private const val EVENT_VOLATILITY_SOFT_KNEE: Double = 1.8
    private const val MIN_EVENT_VOLUME_MULTIPLIER: Double = 0.30
    private const val MAX_EVENT_VOLUME_MULTIPLIER: Double = 12.0
    private const val EVENT_VOLUME_SOFT_KNEE: Double = 2.5
    private const val MIN_EVENT_LIQUIDITY_MULTIPLIER: Double = 0.20
    private const val MAX_EVENT_LIQUIDITY_MULTIPLIER: Double = 5.0
    private const val EVENT_LIQUIDITY_SOFT_KNEE: Double = 1.8
    private val LN_2: Double = ln(2.0)
}
