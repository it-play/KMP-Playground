package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Currency
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.floor

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
