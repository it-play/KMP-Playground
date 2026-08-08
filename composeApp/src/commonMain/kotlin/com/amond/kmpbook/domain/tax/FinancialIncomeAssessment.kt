package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Currency
import kotlinx.datetime.LocalDate
import kotlin.math.floor

data class FinancialIncomeAssessment(
    val ordinaryFinancialIncomeGrossKrw: Long,
    val electedHighDividendIncomeKrw: Long,
    val amountCountedForThresholdKrw: Long,
    val thresholdKrw: Long,
    val exceedsComprehensiveThreshold: Boolean,
    val isEstimate: Boolean,
    val warnings: List<String>,
)
