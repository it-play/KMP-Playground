package com.amond.kmpbook.domain.model.fundstructure

import kotlinx.datetime.LocalDate

/** One official dated ETN indicative-value observation retained for contractual settlement. */
data class EtnIndicativeValueObservation(
    val observationDate: LocalDate,
    val indicativeValuePerNote: Double,
) {
    init {
        requireNonNegativeAmount(indicativeValuePerNote, "indicativeValuePerNote")
    }
}
