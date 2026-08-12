package com.amond.kmpbook.domain.simulation.fund

/** 연도별 재구성 기준일에 사용 가능했던 비거래 후보의 입력 스냅샷이다. */
internal data class SimulatedReferenceEquitySnapshot(
    val definition: SimulatedReferenceEquity,
    val floatMarketCap: Double,
    val averageDailyValueTraded: Double,
    val dividendPaymentYears: Int,
    val indicatedDividendYield: Double,
    val freeCashFlowToDebt: Double,
    val returnOnEquity: Double,
    val fiveYearDividendGrowth: Double,
)
