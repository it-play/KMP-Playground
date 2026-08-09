package com.amond.kmpbook.domain.model.protection.krx

import com.amond.kmpbook.domain.model.protection.krx.KrxSidecarEvent

enum class KrxSidecarEvent {
    NONE,
    SESSION_RESET,
    NOTICE_STARTED,
    NOTICE_CANCELLED,
    ACTIVATED,
    CIRCUIT_BREAKER_TAKES_PRECEDENCE,
    RELEASED,
}
