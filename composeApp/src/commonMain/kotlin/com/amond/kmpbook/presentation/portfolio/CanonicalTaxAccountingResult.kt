package com.amond.kmpbook.presentation.portfolio

import com.amond.kmpbook.domain.tax.lot.FifoCostBasisBook

/** Complete canonical tax-accounting replay output. */
data class CanonicalTaxAccountingResult(
    val fifoCostBasisBook: FifoCostBasisBook,
    val realizedGains: List<RealizedGainRecord>,
    val nativeHoldingsByStockId: Map<String, CanonicalTaxNativeHoldingFact>,
    val originExcessReturnOfCapitalGainKrw: Map<String, Long>,
    val dividendExcessReturnOfCapitalGainKrw: Map<String, Long>,
    val affectedTaxYears: Set<Int>,
)
