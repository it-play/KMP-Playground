package com.amond.kmpbook.domain.tax.lot

import kotlinx.datetime.LocalDate

data class ConsumedTaxLot(
    val lotId: String,
    val acquiredOn: LocalDate,
    val quantity: Double,
    val allocatedCostBasisKrw: Long,
)
