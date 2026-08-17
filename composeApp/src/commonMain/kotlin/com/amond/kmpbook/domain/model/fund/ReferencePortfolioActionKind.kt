package com.amond.kmpbook.domain.model.fund

/** A composition or weight action applied to one shared benchmark reference portfolio. */
enum class ReferencePortfolioActionKind {
    SCHEDULED_RECONSTITUTION,
    /** Partial membership/weight application before a scheduled reconstitution completes. */
    SCHEDULED_RECONSTITUTION_TRANSITION,
    SCHEDULED_REWEIGHT,
    CONSTRAINT_REWEIGHT,
    EXTRAORDINARY_REMOVAL,
    CONSTITUENT_MERGER,
    SPIN_OFF_ADDITION,
    SPIN_OFF_REMOVAL,
    TERMINAL_REMOVAL,
    /** Partial 30/70 replacement execution before a merger/removal completes. */
    CORPORATE_ACTION_TRANSITION,
}
