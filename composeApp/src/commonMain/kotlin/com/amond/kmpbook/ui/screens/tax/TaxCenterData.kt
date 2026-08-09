package com.amond.kmpbook.ui.screens.tax


data class TaxCenterData(
    val currentYear: Int,
    val years: List<TaxYearDisplay>,
    val brokerFeesKrw: Double,
    val secFinraFeesKrw: Double,
    val financialIncomeGrossKrw: Double,
    val highDividendEligibleKrw: Double,
    val nextDueDate: String,
) {
    val current: TaxYearDisplay
        get() = years.firstOrNull { it.year == currentYear } ?: TaxYearDisplay(
            currentYear, 0.0, 0.0, 2_500_000.0, 0.0, 0.0, 0.0, 0.0, 0.0,
        )
}
