package com.amond.kmpbook.domain.model.protection.krx

import com.amond.kmpbook.domain.model.protection.krx.KrxCircuitBreakerPhase

enum class KrxCircuitBreakerPhase {
    NORMAL,
    HALTED,
    REOPENING_CALL_AUCTION,
    CLOSED_FOR_DAY,
}
