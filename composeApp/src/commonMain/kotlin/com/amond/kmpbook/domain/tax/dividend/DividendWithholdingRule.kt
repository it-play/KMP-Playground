package com.amond.kmpbook.domain.tax.dividend

import com.amond.kmpbook.domain.tax.core.EffectiveDateRange
import com.amond.kmpbook.domain.tax.core.RuleSource
import com.amond.kmpbook.domain.tax.core.TaxRate

data class DividendWithholdingRule(
    val nationalRate: TaxRate,
    val localRate: TaxRate,
    val effectiveRange: EffectiveDateRange,
    val source: RuleSource,
)
