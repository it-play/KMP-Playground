package com.amond.kmpbook.domain.model.fundstructure

/** Explicit board-authorized change to a CEF's otherwise fixed common-share count. */
data class ClosedEndFundCapitalAction(
    val kind: ClosedEndFundCapitalActionKind,
    val commonShares: Double,
    val cashPricePerShare: Double,
    val transactionCosts: Double,
) {
    init {
        requireNonNegativeAmount(commonShares, "commonShares")
        requireNonNegativeAmount(cashPricePerShare, "cashPricePerShare")
        requireNonNegativeAmount(transactionCosts, "transactionCosts")
        if (kind == ClosedEndFundCapitalActionKind.NONE) {
            require(commonShares == 0.0)
            require(cashPricePerShare == 0.0)
            require(transactionCosts == 0.0)
        } else {
            require(commonShares > 0.0)
            require(cashPricePerShare > 0.0)
        }
    }

    companion object {
        val NONE = ClosedEndFundCapitalAction(
            kind = ClosedEndFundCapitalActionKind.NONE,
            commonShares = 0.0,
            cashPricePerShare = 0.0,
            transactionCosts = 0.0,
        )
    }
}
