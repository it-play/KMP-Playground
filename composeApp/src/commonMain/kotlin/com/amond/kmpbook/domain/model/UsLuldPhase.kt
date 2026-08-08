package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

enum class UsLuldPhase {
    NORMAL,
    LIMIT_STATE,
    TRADING_PAUSE,
    REOPENING_AUCTION,
    CLOSING_AUCTION_ONLY,
    CLOSED_FOR_DAY,
}
