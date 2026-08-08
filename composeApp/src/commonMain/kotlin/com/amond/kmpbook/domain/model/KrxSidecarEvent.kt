package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

enum class KrxSidecarEvent {
    NONE,
    SESSION_RESET,
    NOTICE_STARTED,
    NOTICE_CANCELLED,
    ACTIVATED,
    CIRCUIT_BREAKER_TAKES_PRECEDENCE,
    RELEASED,
}
