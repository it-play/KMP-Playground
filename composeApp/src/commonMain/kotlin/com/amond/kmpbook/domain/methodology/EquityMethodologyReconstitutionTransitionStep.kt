package com.amond.kmpbook.domain.methodology

import kotlinx.datetime.LocalDate

/** One partial membership-change step before a scheduled reconstitution is fully effective. */
data class EquityMethodologyReconstitutionTransitionStep(
    val effectiveDate: LocalDate,
    val completionFraction: Double,
) {
    init {
        require(completionFraction.isFinite() && completionFraction > 0.0 && completionFraction < 1.0)
    }
}
