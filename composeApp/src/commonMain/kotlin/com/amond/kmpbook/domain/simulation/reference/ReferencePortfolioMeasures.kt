package com.amond.kmpbook.domain.simulation.reference

/** Derived signed portfolio metrics persisted with each reference state. */
internal data class ReferencePortfolioMeasures(
    val gross: Double,
    val net: Double,
    val income: Double,
    val duration: Double,
)
