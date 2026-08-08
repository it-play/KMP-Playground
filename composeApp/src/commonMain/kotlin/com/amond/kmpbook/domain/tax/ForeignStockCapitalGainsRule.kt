package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Market
import kotlinx.datetime.LocalDate

data class ForeignStockCapitalGainsRule(
    val nationalRate: TaxRate,
    val localRate: TaxRate,
    val annualBasicDeductionKrw: Long,
    val lossCarryForwardYears: Int,
    val effectiveRange: EffectiveDateRange,
    val source: RuleSource,
)
