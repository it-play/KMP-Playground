package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.Market
import kotlinx.datetime.LocalDate

data class DomesticMajorCapitalGainsRequest(
    val taxYear: Int,
    /** Tax base after annual stock loss netting and the shared KRW 2.5m deduction. */
    val taxableBaseKrw: Long,
    val isSmallOrMediumEnterprise: Boolean,
    val heldLessThanOneYear: Boolean,
    val calculatedOn: LocalDate,
    val roundingPolicy: MoneyRoundingPolicy = MoneyRoundingPolicy.TAX_WON_DOWN,
) {
    init {
        require(taxYear >= 2026) { "The frozen policy starts in 2026." }
        require(taxableBaseKrw >= 0L) { "Taxable capital gain cannot be negative." }
    }
}
