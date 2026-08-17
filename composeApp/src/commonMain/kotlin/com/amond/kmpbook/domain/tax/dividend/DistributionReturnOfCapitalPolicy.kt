package com.amond.kmpbook.domain.tax.dividend

import com.amond.kmpbook.domain.model.fund.FundLegalStructure
import com.amond.kmpbook.domain.model.instrument.StockDefinition

/**
 * Separates a product's economic distribution-coverage signal from tax return-of-capital status.
 * The current tax model supports ROC only for U.S.-listed open-end ETFs and closed-end funds.
 */
object DistributionReturnOfCapitalPolicy {
    fun isEligible(stock: StockDefinition): Boolean {
        val legalStructure = stock.fundProductProfile?.legalStructure
        return stock.market.isUnitedStates &&
            (legalStructure == FundLegalStructure.OPEN_END_ETF ||
                legalStructure == FundLegalStructure.CLOSED_END_FUND)
    }

    fun modeledTaxableCoverageRatio(stock: StockDefinition): Double =
        if (isEligible(stock)) {
            stock.behavior.distributionCoverageRatio.coerceIn(0.0, 1.0)
        } else {
            1.0
        }

    fun taxableCoverageRatio(
        stock: StockDefinition,
        grossPerUnit: Double,
        classifiedReturnOfCapitalPerUnit: Double?,
    ): Double {
        require(grossPerUnit.isFinite() && grossPerUnit > 0.0)
        if (!isEligible(stock)) return 1.0
        return classifiedReturnOfCapitalPerUnit?.let { returnOfCapitalPerUnit ->
            require(
                returnOfCapitalPerUnit.isFinite() &&
                    returnOfCapitalPerUnit in 0.0..grossPerUnit,
            )
            ((grossPerUnit - returnOfCapitalPerUnit) / grossPerUnit).coerceIn(0.0, 1.0)
        } ?: modeledTaxableCoverageRatio(stock)
    }
}
