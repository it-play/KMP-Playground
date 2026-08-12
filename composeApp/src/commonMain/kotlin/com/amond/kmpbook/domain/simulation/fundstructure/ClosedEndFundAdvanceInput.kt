package com.amond.kmpbook.domain.simulation.fundstructure

import com.amond.kmpbook.domain.model.fundstructure.ClosedEndFundCapitalAction
import com.amond.kmpbook.domain.model.fundstructure.ClosedEndFundCapitalActionKind
import com.amond.kmpbook.domain.model.fundstructure.ClosedEndFundDistribution
import com.amond.kmpbook.domain.model.fundstructure.ClosedEndFundFinancingAction
import com.amond.kmpbook.domain.model.fundstructure.ClosedEndFundFinancingActionKind
import com.amond.kmpbook.domain.model.fundstructure.MAX_FUND_STRUCTURE_VALUE
import com.amond.kmpbook.domain.model.fundstructure.MAX_RATE
import com.amond.kmpbook.domain.model.fundstructure.MAX_YEAR_FRACTION
import com.amond.kmpbook.domain.model.fundstructure.requireNonNegativeAmount
import kotlin.time.Instant

/**
 * Deterministic CEF interval inputs. [assetTotalLogReturn] already includes asset income; the
 * separate [grossInvestmentIncome] is used only to classify UNII and is never added to assets a
 * second time. Accounting order is asset/expense accrual, distribution, capital action, financing.
 */
data class ClosedEndFundAdvanceInput(
    val effectiveAt: Instant,
    val elapsedYearFraction: Double,
    val assetTotalLogReturn: Double,
    val grossInvestmentIncome: Double,
    val annualBorrowingRate: Double,
    val annualPreferredDistributionRate: Double,
    val operatingExpenses: Double,
    val realizedGainReserveChange: Double,
    val marketDiscountShock: Double,
    val distribution: ClosedEndFundDistribution = ClosedEndFundDistribution.NONE,
    val capitalAction: ClosedEndFundCapitalAction = ClosedEndFundCapitalAction.NONE,
    val financingAction: ClosedEndFundFinancingAction = ClosedEndFundFinancingAction.NONE,
) {
    init {
        val hasCashOrLegalEvent = distribution.totalPerShare > 0.0 ||
            capitalAction.kind != ClosedEndFundCapitalActionKind.NONE ||
            financingAction.kind != ClosedEndFundFinancingActionKind.NONE
        require(
            elapsedYearFraction.isFinite() &&
                elapsedYearFraction >= 0.0 &&
                elapsedYearFraction <= MAX_YEAR_FRACTION,
        )
        require(elapsedYearFraction > 0.0 || hasCashOrLegalEvent) {
            "A zero-time CEF interval must contain a distribution, capital, or financing event."
        }
        require(assetTotalLogReturn.isFinite() && assetTotalLogReturn in -MAX_RATE..MAX_RATE)
        requireNonNegativeAmount(grossInvestmentIncome, "grossInvestmentIncome")
        require(annualBorrowingRate.isFinite() && annualBorrowingRate in 0.0..MAX_RATE)
        require(
            annualPreferredDistributionRate.isFinite() &&
                annualPreferredDistributionRate in 0.0..MAX_RATE,
        )
        requireNonNegativeAmount(operatingExpenses, "operatingExpenses")
        require(
            realizedGainReserveChange.isFinite() &&
                realizedGainReserveChange in -MAX_FUND_STRUCTURE_VALUE..MAX_FUND_STRUCTURE_VALUE,
        )
        require(marketDiscountShock.isFinite() && marketDiscountShock in -MAX_RATE..MAX_RATE)
        if (elapsedYearFraction == 0.0) {
            require(assetTotalLogReturn == 0.0)
            require(grossInvestmentIncome == 0.0)
            require(annualBorrowingRate == 0.0)
            require(annualPreferredDistributionRate == 0.0)
            require(operatingExpenses == 0.0)
            require(realizedGainReserveChange == 0.0)
            require(marketDiscountShock == 0.0)
        }
    }
}
