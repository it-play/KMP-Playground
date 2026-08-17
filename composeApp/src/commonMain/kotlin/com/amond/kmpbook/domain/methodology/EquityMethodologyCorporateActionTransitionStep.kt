package com.amond.kmpbook.domain.methodology

import kotlinx.datetime.LocalDate

/** One provider-defined completion point for a staged corporate-action replacement. */
data class EquityMethodologyCorporateActionTransitionStep(
    val effectiveDate: LocalDate,
    val completionFraction: Double,
) {
    init {
        require(completionFraction.isFinite() && completionFraction > 0.0 && completionFraction <= 1.0)
    }
}
