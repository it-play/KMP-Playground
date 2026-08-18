package com.amond.kmpbook.presentation.portfolio

/** Execution path that determines commissions and immediate Korean sale tax. */
enum class CanonicalTradeCostMode {
    REGULAR_EXCHANGE,
    CONTRACTUAL_CASH_SETTLEMENT,
    CORPORATE_ACTION_CASH_IN_LIEU,
}
