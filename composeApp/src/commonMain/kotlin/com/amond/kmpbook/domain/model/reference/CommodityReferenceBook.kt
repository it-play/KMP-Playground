package com.amond.kmpbook.domain.model.reference

import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import kotlin.time.Instant

/** Unique shared spot and futures states; multiple products consume one benchmark calculation. */
data class CommodityReferenceBook(
    val spotStates: Map<BenchmarkRef, CommoditySpotReferenceState>,
    val futuresStates: Map<BenchmarkRef, FuturesReferenceState>,
) {
    init {
        require(spotStates.isNotEmpty() || futuresStates.isNotEmpty())
        require(spotStates.size + futuresStates.size <= MAX_REFERENCES)
        require(spotStates.keys.intersect(futuresStates.keys).isEmpty())
        require(spotStates.all { (ref, state) -> state.benchmarkRef == ref })
        require(futuresStates.all { (ref, state) -> state.benchmarkRef == ref })
        require((spotStates.values.map { it.asOf } + futuresStates.values.map { it.asOf }).distinct().size == 1)
    }

    val benchmarkRefs: Set<BenchmarkRef>
        get() = (spotStates.keys + futuresStates.keys).sorted().toSet()

    val asOf: Instant
        get() = spotStates.values.firstOrNull()?.asOf ?: futuresStates.values.first().asOf

    companion object {
        private const val MAX_REFERENCES: Int = 4_096
    }
}
