package com.amond.kmpbook.domain.model.reference

import com.amond.kmpbook.domain.model.fund.BenchmarkRef

/** Atomic batch result keyed by shared benchmark identity rather than product identity. */
data class CommodityReferenceBookAdvance(
    val book: CommodityReferenceBook,
    val grossReferenceLogReturns: Map<BenchmarkRef, Double>,
    val futuresRollRecords: List<FuturesRollRecord>,
    val futuresAllocationRecords: List<FuturesAllocationRecord>,
) {
    init {
        require(grossReferenceLogReturns.keys == book.benchmarkRefs)
        require(grossReferenceLogReturns.values.all(Double::isFinite))
        require(futuresRollRecords == futuresRollRecords.sortedWith(
            compareBy<FuturesRollRecord> { it.benchmarkRef }.thenBy { it.revision },
        ))
        require(futuresAllocationRecords == futuresAllocationRecords.sortedWith(
            compareBy<FuturesAllocationRecord> { it.benchmarkRef }.thenBy { it.revision },
        ))
    }
}
