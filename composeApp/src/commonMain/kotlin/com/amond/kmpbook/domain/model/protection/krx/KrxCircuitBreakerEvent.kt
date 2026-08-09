package com.amond.kmpbook.domain.model.protection.krx

import com.amond.kmpbook.domain.model.protection.krx.KrxCircuitBreakerEvent

enum class KrxCircuitBreakerEvent {
    NONE,
    SESSION_RESET,
    PERSISTENCE_STARTED,
    PERSISTENCE_CLEARED,
    LEVEL_1_TRIGGERED,
    LEVEL_2_TRIGGERED,
    LEVEL_3_TRIGGERED,
    HALT_ENDED_REOPENING_STARTED,
    REOPENING_COMPLETED,
}
