package com.amond.kmpbook.domain.model.fund

/** Shared benchmark used to mark one category of simulated underlying funds. */
data class FundOfFundsCategoryReference(
    val category: FundOfFundsCategory,
    val benchmarkRef: BenchmarkRef,
)
