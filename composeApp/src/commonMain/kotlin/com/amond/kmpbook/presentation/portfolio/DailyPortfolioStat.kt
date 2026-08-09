package com.amond.kmpbook.presentation.portfolio

import kotlinx.datetime.LocalDate

data class DailyPortfolioStat(
    val date: LocalDate,
    val totalAssetsKrw: Double,
    val cashValueKrw: Double,
    val stockValueKrw: Double,
    val dailyReturn: Double,
    val drawdown: Double,
    val benchmarkValue: Double,
    val usdKrw: Double,
)
