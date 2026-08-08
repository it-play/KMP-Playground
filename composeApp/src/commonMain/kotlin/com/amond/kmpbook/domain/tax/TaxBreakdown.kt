package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Currency
import kotlinx.datetime.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

data class TaxBreakdown(
    val policyId: String,
    val calculatedOn: LocalDate,
    val taxableBase: MoneyAmount,
    val items: List<TaxLineItem>,
    val warnings: List<String> = emptyList(),
) {
    init {
        require(items.all { it.amount.currency == taxableBase.currency }) {
            "Tax lines and their taxable base must use the same currency."
        }
    }

    val totalTax: MoneyAmount
        get() = items.fold(MoneyAmount.zero(taxableBase.currency)) { total, item -> total + item.amount }
}
