package com.amond.kmpbook.domain.model.protection.us

import com.amond.kmpbook.domain.model.protection.us.UsMwcbEvent

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
