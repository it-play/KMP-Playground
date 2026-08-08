package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Currency
import kotlinx.datetime.LocalDate
import kotlin.math.floor

data class DividendTaxResult(
    val breakdown: TaxBreakdown,
    val netCash: MoneyAmount,
    val grossIncomeKrw: Long,
    val financialIncomeAssessment: FinancialIncomeAssessment,
)
