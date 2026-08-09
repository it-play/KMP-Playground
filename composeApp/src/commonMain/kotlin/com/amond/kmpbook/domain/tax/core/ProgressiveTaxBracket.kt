package com.amond.kmpbook.domain.tax.core

/** A null upper bound is the final bracket. */
data class ProgressiveTaxBracket(
    val upperBoundKrw: Long?,
    val rate: TaxRate,
) {
    init {
        require(upperBoundKrw == null || upperBoundKrw > 0L) { "A bracket bound must be positive." }
    }
}
