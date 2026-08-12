package com.amond.kmpbook.domain.model.fundstructure

import kotlin.time.Instant

/**
 * Persistable legal balance sheet of an exchange-listed CEF. Shares remain fixed unless an
 * explicit tender, buyback, or rights event is posted by [ClosedEndFundEngine][com.amond.kmpbook.domain.simulation.fundstructure.ClosedEndFundEngine].
 */
data class ClosedEndFundState(
    val fundId: String,
    val grossAssets: Double,
    val commonSharesOutstanding: Double,
    val debtLiability: Double,
    val preferredShareLiability: Double,
    val navPerCommonShare: Double,
    val undistributedNetInvestmentIncome: Double,
    val distributionReserve: Double,
    val marketDiscountRate: Double,
    val cumulativeUnitAdjustmentFactor: Double = 1.0,
    val lastCorporateActionAccountingSequence: Long? = null,
    val asOf: Instant,
    val revision: Long,
) {
    init {
        requireFundStructureId(fundId, "fundId")
        requirePositiveAmount(grossAssets, "grossAssets")
        requirePositiveAmount(commonSharesOutstanding, "commonSharesOutstanding")
        requireNonNegativeAmount(debtLiability, "debtLiability")
        requireNonNegativeAmount(preferredShareLiability, "preferredShareLiability")
        requirePositiveAmount(navPerCommonShare, "navPerCommonShare")
        require(
            undistributedNetInvestmentIncome.isFinite() &&
                undistributedNetInvestmentIncome in -MAX_FUND_STRUCTURE_VALUE..MAX_FUND_STRUCTURE_VALUE,
        )
        requireNonNegativeAmount(distributionReserve, "distributionReserve")
        require(marketDiscountRate.isFinite() && marketDiscountRate in -0.99..MAX_RATE)
        require(revision >= 0L)
        require(cumulativeUnitAdjustmentFactor.isFinite() && cumulativeUnitAdjustmentFactor > 0.0)
        require(lastCorporateActionAccountingSequence == null || lastCorporateActionAccountingSequence > 0L)
        if (lastCorporateActionAccountingSequence == null) require(cumulativeUnitAdjustmentFactor == 1.0)
        val commonNetAssets = grossAssets - debtLiability - preferredShareLiability
        require(commonNetAssets > 0.0)
        require(amountsAreClose(commonNetAssets, navPerCommonShare * commonSharesOutstanding)) {
            "gross assets minus debt and preferred liabilities must equal NAV times common shares."
        }
        require(
            marketPricePerCommonShare.isFinite() &&
                marketPricePerCommonShare in MIN_FUND_STRUCTURE_VALUE..MAX_FUND_STRUCTURE_VALUE,
        )
    }

    val marketPricePerCommonShare: Double
        get() = navPerCommonShare * (1.0 + marketDiscountRate)
}
