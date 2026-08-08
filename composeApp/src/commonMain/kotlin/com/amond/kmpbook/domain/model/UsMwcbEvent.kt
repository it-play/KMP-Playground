package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

enum class UsMwcbEvent {
    NONE,
    SESSION_RESET,
    LEVEL_1_TRIGGERED,
    LEVEL_2_TRIGGERED,
    LEVEL_3_TRIGGERED,
    REOPENING_AUCTIONS_STARTED,
    VENUE_REOPENED,
    ALL_VENUES_REOPENED,
}
