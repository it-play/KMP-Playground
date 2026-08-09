package com.amond.kmpbook.domain.tax.fee

import com.amond.kmpbook.domain.model.market.Currency
import com.amond.kmpbook.domain.tax.core.MoneyAmount
import kotlinx.datetime.LocalDate

data class FeeBreakdown(
    val calculatedOn: LocalDate,
    val currency: Currency,
    val items: List<FeeLineItem>,
    val warnings: List<String> = emptyList(),
) {
    init {
        require(items.all { it.amount.currency == currency }) {
            "Every fee line must use the breakdown currency."
        }
    }

    val totalFees: MoneyAmount
        get() = items.fold(MoneyAmount.zero(currency)) { total, item -> total + item.amount }
}
