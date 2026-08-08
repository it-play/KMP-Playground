package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

enum class TradingExecutionMode {
    CONTINUOUS,
    CALL_AUCTION_ONLY,
    CLOSING_AUCTION_ONLY,
    PAUSED,
    CLOSED,
}
