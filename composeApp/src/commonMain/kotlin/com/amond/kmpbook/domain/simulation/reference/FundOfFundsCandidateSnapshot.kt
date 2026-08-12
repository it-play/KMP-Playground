package com.amond.kmpbook.domain.simulation.reference

import com.amond.kmpbook.domain.model.fund.FundOfFundsCategory
import com.amond.kmpbook.domain.model.fund.FundOfFundsUniverse

/** Point-in-time information available to a scheduled fund-of-funds selection. */
internal data class FundOfFundsCandidateSnapshot(
    val candidateFundId: String,
    val universe: FundOfFundsUniverse,
    val category: FundOfFundsCategory,
    val netAssetValue: Double,
    val marketDiscountRate: Double,
    val indicatedAnnualDistributionYield: Double,
    val leverageRatio: Double,
    val liquidityScore: Double,
    val qualityScore: Double,
    val expenseRate: Double,
    val annualResidualVolatility: Double,
    val trailingMomentumScore: Double,
    val isEligible: Boolean,
) {
    init {
        require(netAssetValue.isFinite() && netAssetValue > 0.0)
        require(marketDiscountRate.isFinite() && marketDiscountRate in -0.95..2.0)
        require(indicatedAnnualDistributionYield.isFinite() &&
            indicatedAnnualDistributionYield in 0.0..1.0)
        require(leverageRatio.isFinite() && leverageRatio in 0.0..5.0)
        require(liquidityScore.isFinite() && liquidityScore in 0.0..1.0)
        require(qualityScore.isFinite() && qualityScore in -1.0..1.0)
        require(expenseRate.isFinite() && expenseRate in 0.0..0.25)
        require(annualResidualVolatility.isFinite() && annualResidualVolatility in 0.0..3.0)
        require(trailingMomentumScore.isFinite() && trailingMomentumScore in -1.0..1.0)
    }
}
