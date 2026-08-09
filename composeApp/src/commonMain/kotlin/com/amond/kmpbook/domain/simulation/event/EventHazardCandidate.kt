package com.amond.kmpbook.domain.simulation.event

internal data class EventHazardCandidate(
    val template: EventTemplate,
    val targets: List<SelectedEventTarget>,
    val hourlyHazard: Double,
)
