package com.amond.kmpbook.domain.model.reference

import com.amond.kmpbook.domain.model.fund.AlternativeRiskPremiaStrategyFamily

/** Signed driver exposure and bounded EWMA sufficient statistics. */
data class AlternativeRiskPremiaDriverPosition(
    val driverId: String,
    val strategyFamily: AlternativeRiskPremiaStrategyFamily,
    val currentSignedWeight: Double,
    val targetSignedWeight: Double,
    val annualizedVariance: Double,
    val trendSignal: Double,
    val lastSourceLogReturn: Double,
    /** Once false, this driver is permanently represented by base-currency cash. */
    val sourceAvailable: Boolean,
    val sourceAnnualIncomeYield: Double,
    val sourceDurationYears: Double,
) {
    init {
        require(DRIVER_ID_PATTERN.matches(driverId))
        require(currentSignedWeight.isFinite() && currentSignedWeight in -10.0..10.0)
        require(targetSignedWeight.isFinite() && targetSignedWeight in -10.0..10.0)
        require(
            annualizedVariance.isFinite() &&
                annualizedVariance in CompositeReferenceSleevePosition.MIN_VARIANCE..
                CompositeReferenceSleevePosition.MAX_VARIANCE,
        )
        require(trendSignal.isFinite() && trendSignal in -100.0..100.0)
        require(lastSourceLogReturn.isFinite())
        require(sourceAnnualIncomeYield.isFinite() && sourceAnnualIncomeYield in 0.0..1.0)
        require(sourceDurationYears.isFinite() && sourceDurationYears in -50.0..50.0)
        if (!sourceAvailable) {
            require(sourceAnnualIncomeYield == 0.0)
            require(sourceDurationYears == 0.0)
        }
    }

    companion object {
        private val DRIVER_ID_PATTERN = Regex("[a-z0-9][a-z0-9._-]{2,119}")
    }
}
