package com.amond.kmpbook.domain.model.fund

/** Coarse, executable boundary of an equity reference universe. */
enum class EquityEligibleUniverse {
    BROAD_MARKET,
    ALL_CAP,
    LARGE_CAP,
    MID_CAP,
    SMALL_CAP,
    SECTOR_INDUSTRY,
    THEMATIC,
    SINGLE_SECURITY,
    ACTIVE_DISCRETIONARY,
    UNVERIFIED,
}
