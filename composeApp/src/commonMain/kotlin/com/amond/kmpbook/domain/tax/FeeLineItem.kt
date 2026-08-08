package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.OrderSide
import kotlinx.datetime.LocalDate
import kotlin.math.min

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
