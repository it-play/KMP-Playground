package com.amond.kmpbook.domain.simulation.event

import com.amond.kmpbook.domain.model.event.GameEvent

data class EventEngineSnapshot(
    val randomState: Long,
    val sequence: Long,
    val lastTriggeredEpochSeconds: Map<String, Long>,
    val activeEvents: List<GameEvent>,
)
