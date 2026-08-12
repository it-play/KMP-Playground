package com.amond.kmpbook.domain.model.fundproduct

/**
 * Core-equity plus equity-linked-note sleeve; this is not treated as a direct call overwrite.
 * The sleeve holds cash collateral and marks an ATM long call, ATM short put and capped short call.
 */
data class OptionIncomeStrategyTerms(
    val coreEquityAllocation: Double,
    val optionIncomeAllocation: Double,
    val upsideParticipation: Double,
    val downsideParticipation: Double,
    val callStrikeMoneyness: Double,
) {
    init {
        require(coreEquityAllocation.isFinite() && coreEquityAllocation in 0.0..1.0)
        require(optionIncomeAllocation.isFinite() && optionIncomeAllocation in MIN_POSITIVE_RATIO..1.0)
        require(coreEquityAllocation + optionIncomeAllocation <= 1.0 + WEIGHT_EPSILON)
        require(upsideParticipation.isFinite() && upsideParticipation in 0.0..1.0)
        require(downsideParticipation.isFinite() && downsideParticipation in 0.0..1.0)
        require(upsideParticipation > 0.0 || downsideParticipation > 0.0)
        require(callStrikeMoneyness.isFinite() && callStrikeMoneyness in 1.0..3.0)
    }

    companion object {
        private const val MIN_POSITIVE_RATIO: Double = 1e-9
        private const val WEIGHT_EPSILON: Double = 1e-10
    }
}
