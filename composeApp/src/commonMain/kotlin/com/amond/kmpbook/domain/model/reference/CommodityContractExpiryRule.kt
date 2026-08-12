package com.amond.kmpbook.domain.model.reference

/** Explicit game-model convention used to turn a delivery month into a contract expiry. */
enum class CommodityContractExpiryRule {
    MONTH_END_TRADING_DAY,
    THIRD_LAST_TRADING_DAY,
    MID_MONTH_PRECEDING_TRADING_DAY,
    LAST_FRIDAY_PRECEDING_TRADING_DAY,
}
