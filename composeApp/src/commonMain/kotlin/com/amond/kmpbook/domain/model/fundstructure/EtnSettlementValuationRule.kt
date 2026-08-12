package com.amond.kmpbook.domain.model.fundstructure

/** Contractual observation window used to turn indicative values into a settlement base. */
data class EtnSettlementValuationRule(
    val method: EtnSettlementValuationMethod,
    val observationCount: Int,
) {
    init {
        require(observationCount in 1..MAX_OBSERVATIONS)
        if (method == EtnSettlementValuationMethod.LAST_INDICATIVE_VALUE) {
            require(observationCount == 1)
        }
    }

    companion object {
        private const val MAX_OBSERVATIONS: Int = 31
    }
}
