package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

/** Actions queried through the single protection permission API. */
enum class TradingProtectionAction {
    SUBMIT_ORDER,
    CANCEL_ORDER,
    EXECUTE_TRADE,
    PROGRAM_TRADE_FLOW,
    CONTINUOUS_TRADING,
}
