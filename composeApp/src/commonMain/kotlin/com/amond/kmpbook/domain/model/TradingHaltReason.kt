package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

enum class TradingHaltReason {
    MATERIAL_DISCLOSURE,
    DISCLOSURE_INQUIRY,
    LISTING_MAINTENANCE_REVIEW,
    DELISTING_PROCESS,
    INVESTOR_PROTECTION,
    SETTLEMENT_FAILURE,
    CORPORATE_ACTION,
    TECHNICAL_DISRUPTION,
    REGULATORY_ACTION,
    OTHER,
}
