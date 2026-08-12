package com.amond.kmpbook.domain.simulation.reference

import com.amond.kmpbook.domain.model.fund.FundOfFundsCategory
import com.amond.kmpbook.domain.model.fund.FundOfFundsUniverse

/** Stable base characteristics of a non-tradable underlying-fund candidate. */
internal data class FundOfFundsCandidate(
    val candidateFundId: String,
    val universe: FundOfFundsUniverse,
    val category: FundOfFundsCategory,
    val baseNetAssetValue: Double,
    val baseMarketDiscountRate: Double,
    val baseDistributionYield: Double,
    val baseLeverageRatio: Double,
    val baseLiquidityScore: Double,
    val baseQualityScore: Double,
    val baseExpenseRate: Double,
    val annualResidualVolatility: Double,
)
