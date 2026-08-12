package com.amond.kmpbook.domain.model.fund

/** Frequency of reference reconstitution/reweighting; exact months are stored separately. */
enum class EquityRebalanceCalendar {
    CONTINUOUS_ACTIVE,
    MONTHLY,
    QUARTERLY,
    SEMI_ANNUAL,
    ANNUAL,
    UNVERIFIED,
}
