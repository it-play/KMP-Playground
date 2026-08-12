package com.amond.kmpbook.domain.model.fund

/** A composition or weight action applied to one shared benchmark reference portfolio. */
enum class ReferencePortfolioActionKind {
    ANNUAL_RECONSTITUTION,
    QUARTERLY_REBALANCE,
    DAILY_CAP_REBALANCE,
    EXTRAORDINARY_DELETION,
}
