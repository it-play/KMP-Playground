package com.amond.kmpbook.domain.model.protection.core

import com.amond.kmpbook.domain.model.protection.core.TradingProtectionAction

/** Actions queried through the single protection permission API. */
enum class TradingProtectionAction {
    SUBMIT_ORDER,
    CANCEL_ORDER,
    EXECUTE_TRADE,
    PROGRAM_TRADE_FLOW,
    CONTINUOUS_TRADING,
}
