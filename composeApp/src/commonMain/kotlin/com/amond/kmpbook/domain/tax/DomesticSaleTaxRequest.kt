package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.Market
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
