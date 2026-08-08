package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Currency
import kotlinx.datetime.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

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
