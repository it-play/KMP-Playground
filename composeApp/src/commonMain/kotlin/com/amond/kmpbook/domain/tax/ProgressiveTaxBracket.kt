package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Market
import kotlinx.datetime.LocalDate

/** A null upper bound is the final bracket. */
data class ProgressiveTaxBracket(
    val upperBoundKrw: Long?,
    val rate: TaxRate,
) {
    init {
        require(upperBoundKrw == null || upperBoundKrw > 0L) { "A bracket bound must be positive." }
    }
}
