package com.amond.kmpbook.domain.model.protection.krx

import com.amond.kmpbook.domain.model.protection.krx.KrxViEvent

enum class KrxViEvent {
    NONE,
    TRIGGERED,
    AUCTION_COMPLETED,
    CANCELLED_BY_MARKET_CIRCUIT_BREAKER,
}
