package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

data class KrxCircuitBreakerTransition(
    val state: KrxCircuitBreakerState,
    val event: KrxCircuitBreakerEvent = KrxCircuitBreakerEvent.NONE,
)
