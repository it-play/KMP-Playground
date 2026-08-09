package com.amond.kmpbook.domain.tax.dividend

import com.amond.kmpbook.domain.tax.core.EffectiveDateRange
import com.amond.kmpbook.domain.tax.core.ProgressiveTaxBracket
import com.amond.kmpbook.domain.tax.core.RuleSource
import com.amond.kmpbook.domain.tax.core.TaxRate

data class HighDividendSeparateTaxRule(
    val paymentDateRange: EffectiveDateRange,
    val brackets: List<ProgressiveTaxBracket>,
    val localIncomeTaxRateOnNationalTax: TaxRate,
    val source: RuleSource,
)
