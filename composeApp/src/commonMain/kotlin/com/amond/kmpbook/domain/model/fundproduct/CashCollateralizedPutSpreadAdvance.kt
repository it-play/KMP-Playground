package com.amond.kmpbook.domain.model.fundproduct

import kotlin.math.abs
import kotlin.math.ln

/**
 * One deterministic mark-to-market interval and, optionally, one close-and-reopen roll.
 *
 * [settlementCashFlow] is intentionally excluded from the NAV-change equation: closing the old
 * package exchanges its option mark for cash at the same fair value. By contrast, opening premium,
 * implementation cost and [openingOptionMark] enter the equation exactly once.
 */
data class CashCollateralizedPutSpreadAdvance(
    val state: CashCollateralizedPutSpreadState,
    val previousNav: Double,
    val productLogReturn: Double,
    val rolled: Boolean,
    val cashReturnContribution: Double,
    val optionMarkChangeBeforeSettlement: Double,
    val settlementCashFlow: Double,
    val grossPremiumReceived: Double,
    val grossPremiumPaid: Double,
    val openingOptionMark: Double,
    val implementationCost: Double,
    val terminalLiquidationAdjustment: Double,
) {
    init {
        require(previousNav.isFinite() && previousNav in CashCollateralizedPutSpreadState.MIN_NAV..CashCollateralizedPutSpreadState.MAX_NAV)
        require(productLogReturn.isFinite())
        require(cashReturnContribution.isFinite())
        require(optionMarkChangeBeforeSettlement.isFinite())
        require(settlementCashFlow.isFinite() && settlementCashFlow <= tolerance(settlementCashFlow))
        require(grossPremiumReceived.isFinite() && grossPremiumReceived >= 0.0)
        require(grossPremiumPaid.isFinite() && grossPremiumPaid >= 0.0)
        require(openingOptionMark.isFinite() && openingOptionMark <= tolerance(openingOptionMark))
        require(implementationCost.isFinite() && implementationCost >= 0.0)
        require(terminalLiquidationAdjustment.isFinite())
        if (!rolled) {
            require(grossPremiumReceived == 0.0)
            require(grossPremiumPaid == 0.0)
            require(openingOptionMark == 0.0)
            require(implementationCost == 0.0)
            if (settlementCashFlow != 0.0) {
                require(state.lifecycle == CashCollateralizedPutSpreadLifecycle
                    .AWAITING_PRODUCT_LIQUIDATION)
            }
        } else {
            require(state.lifecycle == CashCollateralizedPutSpreadLifecycle.ACTIVE)
        }
        if (state.lifecycle == CashCollateralizedPutSpreadLifecycle.AWAITING_PRODUCT_LIQUIDATION) {
            require(!rolled)
        }
        if (terminalLiquidationAdjustment != 0.0) {
            require(state.lifecycle == CashCollateralizedPutSpreadLifecycle.VALUE_EXHAUSTED)
        }

        val expectedNav =
            previousNav +
                cashReturnContribution +
                optionMarkChangeBeforeSettlement +
                grossPremiumReceived -
                grossPremiumPaid +
                openingOptionMark -
                implementationCost +
                terminalLiquidationAdjustment
        require(approximatelyEqual(expectedNav, state.currentNav)) {
            "현금수익·옵션 mark·roll 현금흐름의 합이 최종 NAV와 일치해야 합니다."
        }
        require(approximatelyEqual(productLogReturn, ln(state.currentNav / previousNav)))
    }

    private fun approximatelyEqual(
        left: Double,
        right: Double,
    ): Boolean = abs(left - right) <= ACCOUNTING_EPSILON * maxOf(1.0, abs(left), abs(right))

    private fun tolerance(value: Double): Double = ACCOUNTING_EPSILON * maxOf(1.0, abs(value))

    companion object {
        private const val ACCOUNTING_EPSILON: Double = 1e-9
    }
}
