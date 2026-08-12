package com.amond.kmpbook.domain.model.fund

/** Source selection and weight rebalancing use separate instances of this calendar. */
enum class CompositeRebalanceCalendar {
    STATIC,
    DAILY,
    CONTINUOUS_ACTIVE,
    MONTHLY,
    QUARTERLY,
    SEMI_ANNUAL,
    ANNUAL,
}
