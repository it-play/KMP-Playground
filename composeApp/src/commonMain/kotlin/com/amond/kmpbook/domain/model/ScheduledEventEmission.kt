package com.amond.kmpbook.domain.model

import kotlin.time.Instant

/**
 * [newsEvent] keeps the public release time. [impactEvent] can start later so a premarket or
 * closed-session release retains its shock until the next regular session.
 */
data class ScheduledEventEmission(
    val occurrence: ScheduledEventOccurrence,
    val outcome: ScheduledEventOutcome,
    val newsEvent: GameEvent,
    val impactEvent: GameEvent,
) {
    init {
        val reference = ScheduledEventReference.from(occurrence)
        require(newsEvent.id == occurrence.id && impactEvent.id == occurrence.id)
        require(newsEvent.startsAt == occurrence.scheduledAt)
        require(impactEvent.startsAt >= occurrence.scheduledAt)
        require(newsEvent.scheduledEventReference == reference)
        require(impactEvent.scheduledEventReference == reference)
    }

    val impactStartsAt: Instant get() = impactEvent.startsAt
}
