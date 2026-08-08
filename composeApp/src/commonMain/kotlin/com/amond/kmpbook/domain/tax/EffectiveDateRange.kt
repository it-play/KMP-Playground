package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Currency
import kotlinx.datetime.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

/** Inclusive effective-date range for a tax or fee rule. */
data class EffectiveDateRange(
    val validFrom: LocalDate,
    val validThrough: LocalDate? = null,
) {
    init {
        require(validThrough == null || validThrough >= validFrom) {
            "The effective-date range cannot end before it starts."
        }
    }

    operator fun contains(date: LocalDate): Boolean =
        date >= validFrom && (validThrough == null || date <= validThrough)
}
