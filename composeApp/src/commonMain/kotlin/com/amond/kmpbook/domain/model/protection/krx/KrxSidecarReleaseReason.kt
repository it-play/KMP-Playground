package com.amond.kmpbook.domain.model.protection.krx

import com.amond.kmpbook.domain.model.protection.krx.KrxSidecarReleaseReason

enum class KrxSidecarReleaseReason {
    FIVE_MINUTES_ELAPSED,
    CLOSING_WINDOW,
    CIRCUIT_BREAKER_RESUMPTION,
    MARKET_CLOSED,
}
