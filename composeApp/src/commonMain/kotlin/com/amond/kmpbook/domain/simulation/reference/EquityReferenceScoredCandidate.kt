package com.amond.kmpbook.domain.simulation.reference

internal data class EquityReferenceScoredCandidate(
    val snapshot: EquityReferenceCandidateSnapshot,
    val score: Double,
)
