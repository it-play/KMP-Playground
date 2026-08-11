package com.amond.kmpbook.modding.api

/** 모드가 원장을 재계산하지 않고 사용할 수 있는 포트폴리오 집계다. */
data class ModPortfolioSummary(
    val initialCapitalKrw: Double,
    val cashValueKrw: Double,
    val instrumentValueKrw: Double,
    val totalAssetsKrw: Double,
    val unrealizedProfitKrw: Double,
    val realizedProfitKrw: Double,
    val totalReturnRate: Double,
    val totalCommissionKrw: Double,
    val totalSaleTaxKrw: Double,
    val totalTransactionCostKrw: Double,
    val totalDividendKrw: Double,
    val peakAssetsKrw: Double,
    val maximumDrawdown: Double,
    val benchmarkReturn: Double,
    val holdingCount: Int,
    val openOrderCount: Int,
    val tradeCount: Int,
)
