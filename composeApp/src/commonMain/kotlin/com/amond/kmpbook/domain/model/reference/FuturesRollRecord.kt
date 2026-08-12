package com.amond.kmpbook.domain.model.reference

import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import kotlin.math.abs
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** One deterministic front-to-deferred transfer; the curve basis is diagnostic, not extra return. */
data class FuturesRollRecord(
    val id: String,
    val benchmarkRef: BenchmarkRef,
    val sleeveId: String,
    val rollTradingDate: LocalDate,
    val fromContractId: String,
    val toContractId: String,
    val transferredContractWeight: Double,
    val frontWeightBefore: Double,
    val frontWeightAfter: Double,
    val normalizedCurveBasis: Double,
    val promotedDeferredToFront: Boolean,
    val successorContractId: String?,
    val effectiveAt: Instant,
    val revision: Long,
) {
    init {
        require(id.isNotBlank() && id.length <= 512)
        require(sleeveId.isNotBlank())
        require(fromContractId.isNotBlank() && toContractId.isNotBlank())
        require(fromContractId != toContractId)
        require(transferredContractWeight.isFinite() && transferredContractWeight in 0.0..1.0)
        require(transferredContractWeight > 0.0)
        require(frontWeightBefore.isFinite() && frontWeightBefore in 0.0..1.0)
        require(frontWeightAfter.isFinite() && frontWeightAfter in 0.0..1.0)
        require(
            abs(frontWeightBefore - frontWeightAfter - transferredContractWeight) <= WEIGHT_EPSILON,
        )
        require(normalizedCurveBasis.isFinite())
        require(promotedDeferredToFront == (frontWeightAfter <= WEIGHT_EPSILON))
        require(promotedDeferredToFront == (successorContractId != null))
        require(successorContractId == null || successorContractId !in setOf(fromContractId, toContractId))
        require(revision > 0L)
    }

    companion object {
        private const val WEIGHT_EPSILON: Double = 1e-10
    }
}
