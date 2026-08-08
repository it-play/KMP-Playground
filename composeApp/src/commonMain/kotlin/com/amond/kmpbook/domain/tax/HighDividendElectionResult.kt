package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Currency
import kotlinx.datetime.LocalDate
import kotlin.math.floor

data class HighDividendElectionResult(
    val isApplied: Boolean,
    val excludedFromFinancialIncomeThresholdKrw: Long,
    val liability: TaxLiability?,
    val reasons: List<String>,
)
