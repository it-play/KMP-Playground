package com.amond.kmpbook.domain.tax.liability

import com.amond.kmpbook.domain.tax.core.MoneyAmount
import kotlinx.datetime.LocalDate

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
