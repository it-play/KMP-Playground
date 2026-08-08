package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Market
import kotlinx.datetime.LocalDate

data class DomesticTransactionTaxRule(
    val market: Market,
    val securitiesTransactionTaxRate: TaxRate,
    val specialRuralTaxRate: TaxRate,
    val effectiveRange: EffectiveDateRange,
    val transactionTaxSource: RuleSource,
    val specialRuralTaxSource: RuleSource?,
)
