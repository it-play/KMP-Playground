package com.amond.kmpbook.domain.tax.fee

import com.amond.kmpbook.domain.tax.core.EffectiveDateRange
import com.amond.kmpbook.domain.tax.core.MoneyAmount
import com.amond.kmpbook.domain.tax.core.RuleSource

data class FeeLineItem(
    val id: String,
    val label: String,
    val amount: MoneyAmount,
    val jurisdiction: FeeJurisdiction,
    val category: FeeCategory,
    val source: RuleSource,
    val effectiveRange: EffectiveDateRange,
) {
    init {
        require(id.isNotBlank() && label.isNotBlank()) { "A fee line needs an id and label." }
        require(amount.minorUnits >= 0L) { "A fee cannot be negative." }
    }
}
