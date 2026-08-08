package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

enum class UsLuldEvent {
    NONE,
    LIMIT_STATE_ENTERED,
    LIMIT_STATE_CLEARED,
    TRADING_PAUSE_STARTED,
    TRADING_PAUSE_EXTENDED,
    REOPENING_AUCTION_STARTED,
    REOPENED,
    CLOSING_AUCTION_ONLY,
    SESSION_CLOSED,
    REFERENCE_PRICE_UPDATED,
}
