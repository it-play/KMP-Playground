package com.amond.kmpbook.domain.tax.lot

import kotlinx.datetime.LocalDate

data class TaxLot(
    val lotId: String,
    val stockId: String,
    val acquiredOn: LocalDate,
    val remainingQuantity: Double,
    /** Purchase price plus directly attributable purchase costs, translated to KRW. */
    val remainingCostBasisKrw: Long,
) {
    init {
        require(lotId.isNotBlank() && stockId.isNotBlank()) { "A tax lot needs a lot and stock id." }
        require(remainingQuantity > 0.0) { "A tax-lot quantity must be positive." }
        require(remainingCostBasisKrw >= 0L) { "A tax-lot cost basis cannot be negative." }
    }
}
