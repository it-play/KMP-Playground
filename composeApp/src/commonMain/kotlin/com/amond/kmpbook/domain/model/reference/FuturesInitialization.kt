package com.amond.kmpbook.domain.model.reference

import kotlinx.datetime.LocalDate

/** Bootstrap curves and local trading dates for one shared futures reference. */
data class FuturesInitialization(
    val terms: FuturesReferenceTerms,
    val curvesBySleeveId: Map<String, FuturesCurveSnapshot>,
    val referenceTradingDates: Map<FuturesRollCalendar, LocalDate>,
    val referenceLevel: Double,
) {
    init {
        require(referenceLevel.isFinite() && referenceLevel > 0.0)
        require(curvesBySleeveId.keys == terms.sleeves.mapTo(linkedSetOf()) { it.sleeveId })
        require(referenceTradingDates.keys == terms.sleeves.mapTo(linkedSetOf()) { it.rollCalendar })
    }
}
