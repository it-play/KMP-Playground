package com.amond.kmpbook.domain.model.fundproduct

/** Direct covered-call contract used by systematic overwrite products such as QYLD-style funds. */
data class CoveredCallStrategyTerms(
    val overwriteRatio: Double,
    val callStrikeMoneyness: Double,
) {
    init {
        require(overwriteRatio.isFinite() && overwriteRatio in MIN_POSITIVE_RATIO..1.0)
        require(callStrikeMoneyness.isFinite() && callStrikeMoneyness in 0.50..2.0)
    }

    companion object {
        private const val MIN_POSITIVE_RATIO: Double = 1e-9
    }
}
