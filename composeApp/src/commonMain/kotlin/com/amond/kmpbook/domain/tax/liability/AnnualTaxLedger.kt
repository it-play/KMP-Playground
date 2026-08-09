package com.amond.kmpbook.domain.tax.liability

data class AnnualTaxLedger(
    val taxYear: Int,
    val policyId: String,
    val taxableDomesticGainKrw: Long,
    val foreignGainKrw: Long,
    val currentYearNetStockGainKrw: Long,
    val sharedStockBasicDeductionKrw: Long,
    val stockTaxableBaseKrw: Long,
    val expiredStockLossKrw: Long,
    val financialIncomeGrossKrw: Long,
    val highDividendIncomeKrw: Long,
    val foreignTaxPaidKrw: Long,
    val withholdingCreditsKrw: Long,
    val liabilities: List<TaxLiability>,
    val warnings: List<String> = emptyList(),
) {
    init {
        require(taxYear >= 1900) { "The tax year is invalid." }
        require(sharedStockBasicDeductionKrw >= 0L && stockTaxableBaseKrw >= 0L) {
            "A deduction and taxable base cannot be negative."
        }
        require(expiredStockLossKrw >= 0L) { "An expired loss cannot be negative." }
        require(financialIncomeGrossKrw >= 0L && highDividendIncomeKrw >= 0L) {
            "Financial income cannot be negative."
        }
        require(foreignTaxPaidKrw >= 0L && withholdingCreditsKrw >= 0L) {
            "Tax paid and credits cannot be negative."
        }
    }

    val totalPayableKrw: Long get() = liabilities.sumOf { it.payableKrw }
    val totalRefundableKrw: Long get() = liabilities.sumOf { it.refundableKrw }
}
