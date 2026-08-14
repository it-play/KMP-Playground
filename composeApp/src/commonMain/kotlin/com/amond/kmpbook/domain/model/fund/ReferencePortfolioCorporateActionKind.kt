package com.amond.kmpbook.domain.model.fund

/**
 * Membership/value-transfer actions understood by an executable equity-index methodology.
 * Splits, ordinary share changes and IWF changes are intentionally absent: this reference layer
 * stores float market value rather than shares and price, so those value-neutral actions require
 * no state mutation here.
 */
enum class ReferencePortfolioCorporateActionKind {
    MERGER,
    SPIN_OFF,
    TERMINAL_REMOVAL,
}
