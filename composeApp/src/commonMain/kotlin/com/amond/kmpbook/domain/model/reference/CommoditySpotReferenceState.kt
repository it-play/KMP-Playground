package com.amond.kmpbook.domain.model.reference

import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import com.amond.kmpbook.domain.model.market.ReferenceCurrency
import kotlin.math.abs
import kotlin.time.Instant

/** Persisted spot level and the normalized total-return reference level. */
data class CommoditySpotReferenceState(
    val benchmarkRef: BenchmarkRef,
    val assetClass: CommodityAssetClass,
    val baseCurrency: ReferenceCurrency,
    val currentSpotLevel: Double,
    val currentReferenceLevel: Double,
    val currentSpotWeight: Double,
    val currentCollateralWeight: Double,
    val annualizedNetCarryRate: Double,
    val asOf: Instant,
) {
    init {
        require(currentSpotLevel.isFinite() && currentSpotLevel in MIN_LEVEL..MAX_LEVEL)
        require(currentReferenceLevel.isFinite() && currentReferenceLevel in MIN_LEVEL..MAX_LEVEL)
        require(currentSpotWeight.isFinite() && currentSpotWeight in 0.0..1.0)
        require(currentCollateralWeight.isFinite() && currentCollateralWeight in 0.0..1.0)
        require(abs(currentSpotWeight + currentCollateralWeight - 1.0) <= WEIGHT_EPSILON)
        require(annualizedNetCarryRate.isFinite() && annualizedNetCarryRate in MIN_CARRY..MAX_CARRY)
    }

    companion object {
        private const val MIN_LEVEL: Double = 1e-12
        private const val MAX_LEVEL: Double = 1e24
        private const val WEIGHT_EPSILON: Double = 1e-10
        private const val MIN_CARRY: Double = -2.0
        private const val MAX_CARRY: Double = 2.0
    }
}
