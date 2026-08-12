package com.amond.kmpbook.domain.model.reference

import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import com.amond.kmpbook.domain.model.market.ReferenceCurrency
import kotlin.math.abs
import kotlin.time.Instant

/** Persisted shared benchmark state; product-specific fees and overlays do not belong here. */
data class FuturesReferenceState(
    val benchmarkRef: BenchmarkRef,
    val baseCurrency: ReferenceCurrency,
    val portfolioStyle: FuturesPortfolioStyle,
    val allocationMode: FuturesAllocationMode,
    val currentReferenceLevel: Double,
    val sleeves: List<FuturesSleeveState>,
    val revision: Long,
    val asOf: Instant,
) {
    init {
        require(currentReferenceLevel.isFinite() && currentReferenceLevel in MIN_LEVEL..MAX_LEVEL)
        require(sleeves.isNotEmpty() && sleeves.size <= MAX_SLEEVES)
        require(sleeves == sleeves.sortedBy(FuturesSleeveState::sleeveId))
        require(sleeves.map(FuturesSleeveState::sleeveId).distinct().size == sleeves.size)
        require(sleeves.map(FuturesSleeveState::curveId).distinct().size == sleeves.size)
        require(abs(sleeves.sumOf(FuturesSleeveState::currentWeight) - 1.0) <= WEIGHT_EPSILON)
        require(abs(sleeves.sumOf(FuturesSleeveState::targetWeight) - 1.0) <= WEIGHT_EPSILON)
        require(revision >= 0L)
    }

    companion object {
        private const val MIN_LEVEL: Double = 1e-12
        private const val MAX_LEVEL: Double = 1e24
        private const val MAX_SLEEVES: Int = 128
        private const val WEIGHT_EPSILON: Double = 1e-8
    }
}
