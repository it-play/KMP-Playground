package com.amond.kmpbook.domain.simulation.causal

internal data class SourceReach(
    val weightedReach: Double,
    val representative: MarketContagionPath,
    val representativeEffectiveContribution: Double,
)
