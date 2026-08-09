package com.amond.kmpbook.domain.simulation.event

internal data class GeneratedEventSamples(
    val durationHours: Int,
    val shockReturn: Double,
    val hourlyDrift: Double,
    val volatilityMultiplier: Double,
    val volumeMultiplier: Double,
    val liquidityMultiplier: Double,
    val sentiment: Double,
    val accelerationRecoveryRate: Double?,
)
