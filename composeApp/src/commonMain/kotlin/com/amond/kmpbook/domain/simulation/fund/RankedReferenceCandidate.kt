package com.amond.kmpbook.domain.simulation.fund

internal data class RankedReferenceCandidate(
    val snapshot: SimulatedReferenceEquitySnapshot,
    val compositeRank: Int,
)
