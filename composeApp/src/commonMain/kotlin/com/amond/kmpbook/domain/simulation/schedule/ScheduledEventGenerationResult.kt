package com.amond.kmpbook.domain.simulation.schedule

import com.amond.kmpbook.domain.model.event.GameEvent
import com.amond.kmpbook.domain.model.schedule.ScheduledEventEmission
import kotlinx.datetime.plus

data class ScheduledEventGenerationResult(
    val emissions: List<ScheduledEventEmission>,
) {
    val newEvents: List<GameEvent> get() = emissions.map(ScheduledEventEmission::newsEvent)
}
