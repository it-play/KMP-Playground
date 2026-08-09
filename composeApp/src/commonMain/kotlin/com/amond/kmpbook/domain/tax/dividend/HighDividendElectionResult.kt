package com.amond.kmpbook.domain.tax.dividend

import com.amond.kmpbook.domain.tax.liability.TaxLiability

data class HighDividendElectionResult(
    val isApplied: Boolean,
    val excludedFromFinancialIncomeThresholdKrw: Long,
    val liability: TaxLiability?,
    val reasons: List<String>,
)
