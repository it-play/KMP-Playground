package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

enum class KrxSidecarReleaseReason {
    FIVE_MINUTES_ELAPSED,
    CLOSING_WINDOW,
    CIRCUIT_BREAKER_RESUMPTION,
    MARKET_CLOSED,
}
