package com.amond.kmpbook.domain.model.reference

import kotlin.math.abs

/** Exact return decomposition plus any roll/allocation ledger entries created in the interval. */
data class FuturesReferenceAdvance(
    val state: FuturesReferenceState,
    val grossReferenceLogReturn: Double,
    val spotProxyLogReturn: Double,
    val curveAndRollLogReturn: Double,
    val collateralLogReturn: Double,
    val rollRecords: List<FuturesRollRecord>,
    val allocationRecord: FuturesAllocationRecord?,
) {
    init {
        require(grossReferenceLogReturn.isFinite())
        require(spotProxyLogReturn.isFinite())
        require(curveAndRollLogReturn.isFinite())
        require(collateralLogReturn.isFinite())
        require(
            abs(
                grossReferenceLogReturn - spotProxyLogReturn -
                    curveAndRollLogReturn - collateralLogReturn,
            ) <= RETURN_EPSILON,
        )
        require(rollRecords == rollRecords.sortedBy(FuturesRollRecord::revision))
        val revisions = buildList {
            addAll(rollRecords.map(FuturesRollRecord::revision))
            allocationRecord?.let { add(it.revision) }
        }
        require(revisions == revisions.distinct().sorted())
        require(revisions.isEmpty() || revisions.last() == state.revision)
    }

    companion object {
        private const val RETURN_EPSILON: Double = 1e-10
    }
}
