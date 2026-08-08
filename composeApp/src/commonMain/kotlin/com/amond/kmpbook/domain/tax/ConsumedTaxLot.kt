package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Currency
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.floor

data class ConsumedTaxLot(
    val lotId: String,
    val acquiredOn: LocalDate,
    val quantity: Double,
    val allocatedCostBasisKrw: Long,
)
