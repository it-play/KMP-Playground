package com.amond.kmpbook.domain.tax.domestic

import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.tax.core.EffectiveDateRange
import com.amond.kmpbook.domain.tax.core.RuleSource
import com.amond.kmpbook.domain.tax.core.TaxRate

data class DomesticTransactionTaxRule(
    val market: Market,
    val securitiesTransactionTaxRate: TaxRate,
    val specialRuralTaxRate: TaxRate,
    val effectiveRange: EffectiveDateRange,
    val transactionTaxSource: RuleSource,
    val specialRuralTaxSource: RuleSource?,
)
