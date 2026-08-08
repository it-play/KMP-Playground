package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Market
import kotlinx.datetime.LocalDate

data class DomesticMajorShareholderCapitalGainsRule(
    val generalLowerRate: TaxRate,
    val generalUpperRate: TaxRate,
    val upperRateStartsAboveKrw: Long,
    val nonSmeShortTermRate: TaxRate,
    val localIncomeTaxRateOnNationalTax: TaxRate,
    val effectiveRange: EffectiveDateRange,
    val source: RuleSource,
)
