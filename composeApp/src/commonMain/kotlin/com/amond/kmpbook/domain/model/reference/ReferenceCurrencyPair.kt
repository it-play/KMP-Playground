package com.amond.kmpbook.domain.model.reference

import com.amond.kmpbook.domain.model.market.ReferenceCurrency

/** FX return of one source currency measured in a different reference currency. */
data class ReferenceCurrencyPair(
    val sourceCurrency: ReferenceCurrency,
    val targetCurrency: ReferenceCurrency,
) : Comparable<ReferenceCurrencyPair> {
    init {
        require(sourceCurrency != targetCurrency)
    }

    override fun compareTo(other: ReferenceCurrencyPair): Int =
        compareValuesBy(this, other, { it.sourceCurrency.ordinal }, { it.targetCurrency.ordinal })
}
