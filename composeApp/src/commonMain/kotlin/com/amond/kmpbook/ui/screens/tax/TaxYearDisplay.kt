package com.amond.kmpbook.ui.screens.tax

data class TaxYearDisplay(
    val year: Int,
    val taxableStockGainKrw: Double,
    val stockLossKrw: Double,
    val basicDeductionKrw: Double,
    val capitalGainsTaxKrw: Double,
    val securitiesTransactionTaxKrw: Double,
    val ruralSpecialTaxKrw: Double,
    val financialIncomeGrossKrw: Double,
    val financialIncomeWithheldKrw: Double,
    val paidKrw: Double = 0.0,
)
