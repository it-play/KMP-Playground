package com.amond.kmpbook.domain.simulation.fund

import com.amond.kmpbook.domain.model.market.Sector
import com.amond.kmpbook.domain.model.fund.MethodologyEquitySector

/**
 * 거래 카탈로그에 노출되지 않는 캠페인 내부 미국 주식 후보다.
 * 미래의 실제 기업을 예언하는 데이터가 아니라 버전된 편입 규칙을 실행하기 위한 기준자산이다.
 */
internal data class SimulatedReferenceEquity(
    val assetId: String,
    val displaySymbol: String,
    val displayName: String,
    val sector: Sector,
    val methodologySector: MethodologyEquitySector,
    val baseFloatMarketCap: Double,
    val baseDailyTurnover: Double,
    val baseDividendPaymentYears: Int,
    val baseDividendYield: Double,
    val baseFreeCashFlowToDebt: Double,
    val baseReturnOnEquity: Double,
    val baseFiveYearDividendGrowth: Double,
    val beta: Double,
    val annualVolatility: Double,
    val quality: Double,
    val value: Double,
    val debtFree: Boolean,
)
