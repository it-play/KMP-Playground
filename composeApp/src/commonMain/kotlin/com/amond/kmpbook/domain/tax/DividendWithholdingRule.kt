package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Market
import kotlinx.datetime.LocalDate

data class DividendWithholdingRule(
    val nationalRate: TaxRate,
    val localRate: TaxRate,
    val effectiveRange: EffectiveDateRange,
    val source: RuleSource,
)
