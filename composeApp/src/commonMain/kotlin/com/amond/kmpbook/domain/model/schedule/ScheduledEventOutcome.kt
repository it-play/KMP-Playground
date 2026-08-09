package com.amond.kmpbook.domain.model.schedule

import com.amond.kmpbook.domain.model.event.EventSeverity
import com.amond.kmpbook.domain.model.event.GameEventImpact
import com.amond.kmpbook.domain.model.event.ImpactDirection

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
