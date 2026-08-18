package com.amond.kmpbook.presentation.portfolio

import com.amond.kmpbook.domain.tax.fee.FeeBreakdown
import com.amond.kmpbook.domain.tax.liability.TaxBreakdown

/** Deterministic commission and immediate sale-tax projection for one trade. */
data class CanonicalTradeCostResult(
    val commission: Double,
    val saleTax: Double,
    val feeBreakdown: FeeBreakdown?,
    val taxBreakdown: TaxBreakdown?,
)
