package com.amond.kmpbook.domain.simulation.reference

/** Canonical source-currency observation after any typed FX or structural transformation. */
internal data class ReferenceSourceObservation(
    val logReturn: Double,
    val incomeYield: Double,
    val durationYears: Double,
    val conditionalPrepaymentRateAnnual: Double?,
    val sourceAvailable: Boolean,
)
