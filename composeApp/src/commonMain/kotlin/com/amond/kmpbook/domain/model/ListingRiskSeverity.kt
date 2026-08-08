package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate

enum class ListingRiskSeverity(val level: Int) {
    NONE(0),
    LOW(1),
    MODERATE(2),
    HIGH(3),
    CRITICAL(4),
}
