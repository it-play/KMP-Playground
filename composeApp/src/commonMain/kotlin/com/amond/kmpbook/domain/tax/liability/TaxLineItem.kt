package com.amond.kmpbook.domain.tax.liability

import com.amond.kmpbook.domain.tax.core.EffectiveDateRange
import com.amond.kmpbook.domain.tax.core.MoneyAmount
import com.amond.kmpbook.domain.tax.core.RuleSource
import com.amond.kmpbook.domain.tax.core.TaxCategory
import com.amond.kmpbook.domain.tax.core.TaxJurisdiction

data class TaxLineItem(
    val id: String,
    val label: String,
    val amount: MoneyAmount,
    val jurisdiction: TaxJurisdiction,
    val category: TaxCategory,
    val source: RuleSource,
    val effectiveRange: EffectiveDateRange,
) {
    init {
        require(id.isNotBlank() && label.isNotBlank()) { "A tax line needs an id and label." }
        require(amount.minorUnits >= 0L) { "A tax line cannot be negative." }
    }
}
