package com.amond.kmpbook.domain.model.fundproduct

/** Lifecycle of a cash-collateralized short-put/long-put spread product. */
enum class CashCollateralizedPutSpreadLifecycle {
    ACTIVE,
    AWAITING_PRODUCT_LIQUIDATION,
    VALUE_EXHAUSTED,
}
