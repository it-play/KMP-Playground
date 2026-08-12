package com.amond.kmpbook.domain.simulation.fundstructure

internal data class ClosedEndFundFinancingResult(
    val grossAssets: Double,
    val debtLiability: Double,
    val preferredLiability: Double,
    val grossAssetsDelta: Double,
    val debtLiabilityDelta: Double,
    val preferredLiabilityDelta: Double,
)
