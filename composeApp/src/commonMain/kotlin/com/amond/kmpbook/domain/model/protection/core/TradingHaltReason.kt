package com.amond.kmpbook.domain.model.protection.core

import com.amond.kmpbook.domain.model.protection.core.TradingHaltReason

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
