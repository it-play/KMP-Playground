package com.amond.kmpbook.domain.simulation.fund

import com.amond.kmpbook.domain.model.market.Sector
import com.amond.kmpbook.domain.model.fund.MethodologyEquitySector

/**
 * 거래 카탈로그에 노출되지 않는 캠페인 내부 미국 주식 후보다.
 * 미래의 실제 기업을 예언하는 데이터가 아니라 버전된 편입 규칙을 실행하기 위한 기준자산이다.
 *
 * 이 synthetic universe에서는 한 [companyId]가 정확히 한 [assetId] (주상장 종목)에 대응한다.
 * 따라서 회사 단위 기초자료가 여러 주식 클래스나 상장시장에 중복 반영되지 않는다.
 */
internal data class SimulatedReferenceEquity(
    val companyId: String,
    val assetId: String,
    val displaySymbol: String,
    val displayName: String,
    val sector: Sector,
    val methodologySector: MethodologyEquitySector,
    val gicsClassificationCode: Int,
    val baseFloatMarketCap: Double,
    val baseThreeMonthAverageDailyValueTraded: Double,
    val baseDividendPaymentYears: Int,
    val dividendPaymentsPerYear: Int,
    val firstDividendPaymentMonth: Int,
    val baseCashFlowFromOperations: Double,
    val baseCapitalExpenditures: Double,
    val baseTotalDebt: Double,
    val baseBasicEarningsPerShare: Double,
    val baseBookValuePerShare: Double,
    val baseSharePrice: Double,
    val baseRegularFixedAnnualDividendPerShare: Double,
    /** 기준연도부터 역순인 최근 5개 회계연도의 특별배당 제외 정규 DPS다. */
    val baseAnnualRegularDividendPerShareNewestFirst: List<Double>,
    val beta: Double,
    val annualVolatility: Double,
    val quality: Double,
    val value: Double,
)
