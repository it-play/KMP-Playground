package com.amond.kmpbook.domain.model

import kotlin.time.Instant

/** Seeded game data keyed to one immutable occurrence id. */
data class ScheduledEventOutcome(
    val surpriseScore: Double,
    val comparison: ScheduledOutcomeComparison,
    val metrics: List<ScheduledEventMetric>,
    val direction: ImpactDirection,
    val severity: EventSeverity,
    val impact: GameEventImpact,
) {
    init {
        require(surpriseScore in -1.0..1.0)
        require(metrics.isNotEmpty())
        require(impact.direction == direction)
    }
}
