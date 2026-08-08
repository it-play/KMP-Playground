package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Currency
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.floor

data class AnnualStockTaxRequest(
    val taxYear: Int,
    val gains: List<RealizedStockGain>,
    val financialIncomeGrossKrw: Long = 0L,
    val highDividendIncomeKrw: Long = 0L,
    val foreignTaxPaidKrw: Long = 0L,
    val withholdingCreditsKrw: Long = 0L,
    val roundingPolicy: MoneyRoundingPolicy = MoneyRoundingPolicy.TAX_WON_DOWN,
) {
    init {
        require(taxYear in 2026..2040) { "The frozen scenario supports tax years 2026 through 2040." }
        require(gains.all { it.realizedOn.year == taxYear }) { "Every gain must belong to taxYear." }
        require(financialIncomeGrossKrw >= 0L && highDividendIncomeKrw >= 0L) {
            "Financial income cannot be negative."
        }
        require(foreignTaxPaidKrw >= 0L && withholdingCreditsKrw >= 0L) {
            "Taxes paid and credits cannot be negative."
        }
    }
}
