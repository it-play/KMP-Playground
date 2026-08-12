package com.amond.kmpbook.domain.model.fundstructure

/** Explicit liability financing; it can never be inferred from an ordinary shareholder flow. */
data class ClosedEndFundFinancingAction(
    val kind: ClosedEndFundFinancingActionKind,
    val principalAmount: Double,
    val transactionCosts: Double,
) {
    init {
        requireNonNegativeAmount(principalAmount, "principalAmount")
        requireNonNegativeAmount(transactionCosts, "transactionCosts")
        if (kind == ClosedEndFundFinancingActionKind.NONE) {
            require(principalAmount == 0.0)
            require(transactionCosts == 0.0)
        } else {
            require(principalAmount > 0.0)
        }
    }

    companion object {
        val NONE = ClosedEndFundFinancingAction(
            kind = ClosedEndFundFinancingActionKind.NONE,
            principalAmount = 0.0,
            transactionCosts = 0.0,
        )
    }
}
