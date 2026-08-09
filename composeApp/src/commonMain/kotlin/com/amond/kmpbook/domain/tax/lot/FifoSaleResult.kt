package com.amond.kmpbook.domain.tax.lot

import kotlinx.datetime.LocalDate

data class FifoSaleResult(
    val stockId: String,
    val soldOn: LocalDate,
    val soldQuantity: Double,
    val grossProceedsKrw: Long,
    val allocatedCostBasisKrw: Long,
    val directSellingCostsKrw: Long,
    val realizedGainKrw: Long,
    val consumedLots: List<ConsumedTaxLot>,
    val updatedBook: FifoCostBasisBook,
)
