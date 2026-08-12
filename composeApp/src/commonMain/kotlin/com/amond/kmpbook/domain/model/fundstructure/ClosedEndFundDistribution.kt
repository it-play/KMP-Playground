package com.amond.kmpbook.domain.model.fundstructure

/**
 * Board-declared distribution classification per opening common share. Return of capital is
 * explicit rather than being mislabeled as investment income.
 */
data class ClosedEndFundDistribution(
    val netInvestmentIncomePerShare: Double,
    val realizedGainPerShare: Double,
    val returnOfCapitalPerShare: Double,
) {
    init {
        requireNonNegativeAmount(netInvestmentIncomePerShare, "netInvestmentIncomePerShare")
        requireNonNegativeAmount(realizedGainPerShare, "realizedGainPerShare")
        requireNonNegativeAmount(returnOfCapitalPerShare, "returnOfCapitalPerShare")
        requireNonNegativeAmount(totalPerShare, "totalPerShare")
    }

    val totalPerShare: Double
        get() = netInvestmentIncomePerShare + realizedGainPerShare + returnOfCapitalPerShare

    companion object {
        val NONE = ClosedEndFundDistribution(
            netInvestmentIncomePerShare = 0.0,
            realizedGainPerShare = 0.0,
            returnOfCapitalPerShare = 0.0,
        )
    }
}
