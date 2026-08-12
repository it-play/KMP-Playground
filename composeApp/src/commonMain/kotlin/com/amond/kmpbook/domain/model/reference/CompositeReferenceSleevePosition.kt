package com.amond.kmpbook.domain.model.reference

import com.amond.kmpbook.domain.model.fund.CompositeSleeveDirection

/** Path-dependent notional and risk estimates for one typed composite sleeve. */
data class CompositeReferenceSleevePosition(
    val sleeveId: String,
    val direction: CompositeSleeveDirection,
    val currentWeightMagnitude: Double,
    val targetWeightMagnitude: Double,
    val annualizedVariance: Double,
    val trendSignal: Double,
    val lastSourceLogReturn: Double,
    /** Once false, this sleeve is permanently represented by base-currency cash. */
    val sourceAvailable: Boolean,
    val sourceAnnualIncomeYield: Double,
    val sourceDurationYears: Double,
    val conditionalPrepaymentRateAnnual: Double?,
) {
    init {
        require(SLEEVE_ID_PATTERN.matches(sleeveId))
        require(currentWeightMagnitude.isFinite() && currentWeightMagnitude in 0.0..MAX_WEIGHT)
        require(targetWeightMagnitude.isFinite() && targetWeightMagnitude in 0.0..MAX_WEIGHT)
        require(annualizedVariance.isFinite() && annualizedVariance in MIN_VARIANCE..MAX_VARIANCE)
        require(trendSignal.isFinite() && trendSignal in -MAX_SIGNAL..MAX_SIGNAL)
        require(lastSourceLogReturn.isFinite())
        require(sourceAnnualIncomeYield.isFinite() && sourceAnnualIncomeYield in 0.0..1.0)
        require(sourceDurationYears.isFinite() && sourceDurationYears in -50.0..50.0)
        conditionalPrepaymentRateAnnual?.let {
            require(it.isFinite() && it in 0.0..1.0)
        }
        if (!sourceAvailable) {
            require(sourceAnnualIncomeYield == 0.0)
            require(sourceDurationYears == 0.0)
            require(conditionalPrepaymentRateAnnual == null)
        }
    }

    val signedCurrentWeight: Double
        get() = if (direction == CompositeSleeveDirection.LONG) {
            currentWeightMagnitude
        } else {
            -currentWeightMagnitude
        }

    val signedTargetWeight: Double
        get() = if (direction == CompositeSleeveDirection.LONG) {
            targetWeightMagnitude
        } else {
            -targetWeightMagnitude
        }

    companion object {
        const val MIN_VARIANCE: Double = 1e-8
        const val MAX_VARIANCE: Double = 25.0
        private const val MAX_WEIGHT: Double = 10.0
        private const val MAX_SIGNAL: Double = 100.0
        private val SLEEVE_ID_PATTERN = Regex("[a-z0-9][a-z0-9._-]{2,119}")
    }
}
