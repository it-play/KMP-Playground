package com.amond.kmpbook.domain.model.listing.lifecycle

import com.amond.kmpbook.domain.model.listing.lifecycle.ListingRiskSeverity

enum class ListingRiskSeverity(val level: Int) {
    NONE(0),
    LOW(1),
    MODERATE(2),
    HIGH(3),
    CRITICAL(4),
}
