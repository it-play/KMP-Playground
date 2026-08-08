package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

enum class KrxCircuitBreakerPhase {
    NORMAL,
    HALTED,
    REOPENING_CALL_AUCTION,
    CLOSED_FOR_DAY,
}
