package com.amond.kmpbook.domain.model.fund

/** A composition or weight action applied to one shared benchmark reference portfolio. */
enum class ReferencePortfolioActionKind {
    SCHEDULED_RECONSTITUTION,
    SCHEDULED_REWEIGHT,
    CONSTRAINT_REWEIGHT,
    EXTRAORDINARY_REMOVAL,
}
