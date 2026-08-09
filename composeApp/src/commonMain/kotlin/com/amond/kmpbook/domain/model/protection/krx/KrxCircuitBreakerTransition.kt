package com.amond.kmpbook.domain.model.protection.krx

import com.amond.kmpbook.domain.model.protection.krx.KrxCircuitBreakerEvent
import com.amond.kmpbook.domain.model.protection.krx.KrxCircuitBreakerState
import com.amond.kmpbook.domain.model.protection.krx.KrxCircuitBreakerTransition

data class KrxCircuitBreakerTransition(
    val state: KrxCircuitBreakerState,
    val event: KrxCircuitBreakerEvent = KrxCircuitBreakerEvent.NONE,
)
