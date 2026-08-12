package com.amond.kmpbook.domain.simulation.fundstructure

internal data class ClosedEndFundCapitalResult(
    val grossAssets: Double,
    val commonShares: Double,
    val grossAssetsDelta: Double,
    val commonSharesDelta: Double,
    val cashToCommonShareholders: Double,
)
