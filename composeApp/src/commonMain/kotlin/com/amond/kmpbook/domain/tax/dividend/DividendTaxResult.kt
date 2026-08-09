package com.amond.kmpbook.domain.tax.dividend

import com.amond.kmpbook.domain.tax.assessment.FinancialIncomeAssessment
import com.amond.kmpbook.domain.tax.core.MoneyAmount
import com.amond.kmpbook.domain.tax.liability.TaxBreakdown

data class DividendTaxResult(
    val breakdown: TaxBreakdown,
    val netCash: MoneyAmount,
    val grossIncomeKrw: Long,
    val financialIncomeAssessment: FinancialIncomeAssessment,
)
