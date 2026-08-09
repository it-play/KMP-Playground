package com.amond.kmpbook.domain.simulation.event

import com.amond.kmpbook.domain.model.event.GameEvent

data class EventGenerationResult(
    val newEvents: List<GameEvent>,
    val activeEvents: List<GameEvent>,
    val snapshot: EventEngineSnapshot,
)
