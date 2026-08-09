package com.amond.kmpbook.domain.tax.assessment


data class FinancialIncomeAssessment(
    val ordinaryFinancialIncomeGrossKrw: Long,
    val electedHighDividendIncomeKrw: Long,
    val amountCountedForThresholdKrw: Long,
    val thresholdKrw: Long,
    val exceedsComprehensiveThreshold: Boolean,
    val isEstimate: Boolean,
    val warnings: List<String>,
)
