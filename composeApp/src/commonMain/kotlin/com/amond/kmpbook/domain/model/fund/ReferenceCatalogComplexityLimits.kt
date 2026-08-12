package com.amond.kmpbook.domain.model.fund

/** Shared pack-wide state/initialization budgets used by both catalog validation and engines. */
object ReferenceCatalogComplexityLimits {
    const val LOW_CONFIDENCE_EQUITY_REPRESENTATIVES: Int = 64
    const val MEDIUM_CONFIDENCE_EQUITY_REPRESENTATIVES: Int = 128
    const val HIGH_CONFIDENCE_EQUITY_REPRESENTATIVES: Int = 256
    const val MAX_TOTAL_EQUITY_REPRESENTATIVE_POSITIONS: Int = 65_536
    const val MAX_TOTAL_FUND_OF_FUNDS_CANDIDATES: Int = 65_536
    const val MAX_TOTAL_FUND_OF_FUNDS_COMPOSITE_AND_ALTERNATIVE_POSITIONS: Int = 65_536

    fun representativeLimit(profile: EquityReferenceProfile): Int = when {
        profile.eligibleUniverse == EquityEligibleUniverse.SINGLE_SECURITY -> 1
        profile.confidence == EquityReferenceConfidence.LOW -> LOW_CONFIDENCE_EQUITY_REPRESENTATIVES
        profile.confidence == EquityReferenceConfidence.MEDIUM -> MEDIUM_CONFIDENCE_EQUITY_REPRESENTATIVES
        else -> HIGH_CONFIDENCE_EQUITY_REPRESENTATIVES
    }
}
