package com.amond.kmpbook.domain.model.reference

import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import kotlin.math.abs
import kotlin.time.Instant

/** Externally compiled dynamic-basket targets applied without inventing a selection methodology. */
data class FuturesAllocationRecord(
    val id: String,
    val benchmarkRef: BenchmarkRef,
    val weightsBefore: Map<String, Double>,
    val weightsAfter: Map<String, Double>,
    val effectiveAt: Instant,
    val revision: Long,
) {
    init {
        require(id.isNotBlank() && id.length <= 512)
        require(weightsBefore.isNotEmpty() && weightsBefore.keys == weightsAfter.keys)
        require(weightsBefore.values.all { it.isFinite() && it in 0.0..1.0 })
        require(weightsAfter.values.all { it.isFinite() && it in 0.0..1.0 })
        require(abs(weightsBefore.values.sum() - 1.0) <= WEIGHT_EPSILON)
        require(abs(weightsAfter.values.sum() - 1.0) <= WEIGHT_EPSILON)
        require(weightsBefore != weightsAfter)
        require(revision > 0L)
    }

    companion object {
        private const val WEIGHT_EPSILON: Double = 1e-8
    }
}
