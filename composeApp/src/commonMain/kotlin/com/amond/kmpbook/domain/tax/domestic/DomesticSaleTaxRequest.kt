package com.amond.kmpbook.domain.tax.domestic

import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.tax.core.MoneyRoundingPolicy
import kotlinx.datetime.LocalDate

data class DomesticSaleTaxRequest(
    val market: Market,
    val grossProceedsKrw: Long,
    val soldOn: LocalDate,
    val roundingPolicy: MoneyRoundingPolicy = MoneyRoundingPolicy.TAX_WON_DOWN,
) {
    init {
        require(market == Market.KOSPI || market == Market.KOSDAQ) {
            "Immediate Korean transaction tax only supports KOSPI and KOSDAQ."
        }
        require(grossProceedsKrw >= 0L) { "Gross sale proceeds cannot be negative." }
    }
}
