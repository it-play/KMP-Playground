package com.amond.kmpbook.domain.simulation.reference

/** Unsigned magnitudes keyed by sleeve/driver plus their derived signed portfolio totals. */
internal data class SignedExposureTargets(
    val magnitudes: Map<String, Double>,
    val grossExposure: Double,
    val netExposure: Double,
)
