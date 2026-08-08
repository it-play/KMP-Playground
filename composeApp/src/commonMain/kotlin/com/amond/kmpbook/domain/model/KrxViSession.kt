package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

enum class KrxViSession {
    OPENING_CALL_AUCTION,
    CONTINUOUS_AUCTION,
    CLOSING_CALL_AUCTION,
    AFTER_HOURS_PERIODIC_CALL_AUCTION,
}
