package com.amond.kmpbook.domain.model.fund

/** Maximum constituent weight through [lastRankInclusive] after methodology ranking. */
data class FundOfFundsRankedWeightCapTier(
    val lastRankInclusive: Int,
    val maximumWeight: Double,
) {
    init {
        require(lastRankInclusive > 0)
        require(maximumWeight.isFinite() && maximumWeight in MIN_WEIGHT..1.0)
    }

    companion object {
        private const val MIN_WEIGHT: Double = 1e-6
    }
}
