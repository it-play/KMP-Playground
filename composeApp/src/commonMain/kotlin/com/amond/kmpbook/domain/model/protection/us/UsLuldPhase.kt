package com.amond.kmpbook.domain.model.protection.us

import com.amond.kmpbook.domain.model.protection.us.UsLuldPhase

enum class UsLuldPhase {
    NORMAL,
    LIMIT_STATE,
    TRADING_PAUSE,
    REOPENING_AUCTION,
    CLOSING_AUCTION_ONLY,
    CLOSED_FOR_DAY,
}
