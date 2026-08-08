package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Market
import kotlinx.datetime.LocalDate

data class HighDividendSeparateTaxRule(
    val paymentDateRange: EffectiveDateRange,
    val brackets: List<ProgressiveTaxBracket>,
    val localIncomeTaxRateOnNationalTax: TaxRate,
    val source: RuleSource,
)
