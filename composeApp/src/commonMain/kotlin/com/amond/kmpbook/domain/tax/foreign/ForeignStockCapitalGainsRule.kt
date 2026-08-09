package com.amond.kmpbook.domain.tax.foreign

import com.amond.kmpbook.domain.tax.core.EffectiveDateRange
import com.amond.kmpbook.domain.tax.core.RuleSource
import com.amond.kmpbook.domain.tax.core.TaxRate

data class ForeignStockCapitalGainsRule(
    val nationalRate: TaxRate,
    val localRate: TaxRate,
    val annualBasicDeductionKrw: Long,
    val lossCarryForwardYears: Int,
    val effectiveRange: EffectiveDateRange,
    val source: RuleSource,
)
