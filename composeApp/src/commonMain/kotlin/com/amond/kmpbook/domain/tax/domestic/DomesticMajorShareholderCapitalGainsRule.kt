package com.amond.kmpbook.domain.tax.domestic

import com.amond.kmpbook.domain.tax.core.EffectiveDateRange
import com.amond.kmpbook.domain.tax.core.RuleSource
import com.amond.kmpbook.domain.tax.core.TaxRate

data class DomesticMajorShareholderCapitalGainsRule(
    val generalLowerRate: TaxRate,
    val generalUpperRate: TaxRate,
    val upperRateStartsAboveKrw: Long,
    val nonSmeShortTermRate: TaxRate,
    val localIncomeTaxRateOnNationalTax: TaxRate,
    val effectiveRange: EffectiveDateRange,
    val source: RuleSource,
)
