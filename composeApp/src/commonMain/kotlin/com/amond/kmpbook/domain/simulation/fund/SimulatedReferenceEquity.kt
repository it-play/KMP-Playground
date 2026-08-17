package com.amond.kmpbook.domain.simulation.fund

import com.amond.kmpbook.domain.model.market.Sector
import com.amond.kmpbook.domain.model.fund.FundReferenceUniverse
import com.amond.kmpbook.domain.model.fund.MethodologyEquitySector

/**
 * 거래 카탈로그에 노출되지 않는 캠페인 내부 미국 주식 후보다.
 * 미래의 실제 기업을 예언하는 데이터가 아니라 버전된 편입 규칙을 실행하기 위한 기준자산이다.
 *
 * 이 synthetic universe에서는 한 [companyId]가 정확히 한 [assetId] (주상장 종목)에 대응한다.
 * 따라서 회사 단위 기초자료가 여러 주식 클래스나 상장시장에 중복 반영되지 않는다.
 */
internal data class SimulatedReferenceEquity(
    val referenceUniverse: FundReferenceUniverse,
    val companyId: String,
    val assetId: String,
    val displaySymbol: String,
    val displayName: String,
    val sector: Sector,
    val methodologySector: MethodologyEquitySector,
    val gicsClassificationCode: Int,
    val baseFloatMarketCap: Double,
    /** 기준일 발행주식 중 공개 투자자가 거래할 수 있다고 모델링한 비율이다. */
    val baseInvestableWeightFactor: Double,
    val baseThreeMonthAverageDailyValueTraded: Double,
    /** 관찰일 직전 3개월의 일별 거래대금 중앙값을 위한 독립 원천값이다. */
    val baseThreeMonthMedianDailyValueTraded: Double,
    /** 관찰일 직전 125거래일의 거래대금 평균을 위한 독립 Morningstar 원천값이다. */
    val baseTrailing125TradingDayAverageDailyValueTraded: Double,
    val baseTwelveMonthAverageDailyValueTraded: Double,
    /** 직전 6개월 중 거래량이 가장 작았던 달의 주식 수다. */
    val baseMinimumSixMonthMonthlyShareVolume: Double,
    val baseLatestQuarterGaapNetIncome: Double,
    val baseTrailingFourQuarterGaapNetIncome: Double,
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
    val baseListingAgeYears: Int,
    val baseKospi200FinancialMember: Boolean,
    val baseThreeYearAverageDividendPayoutRatio: Double,
    val baseThreeYearAverageReturnOnEquity: Double,
    val baseOneMonthAverageDailyValueTraded: Double,
    val baseOneMonthAveragePriceToBookRatio: Double,
    val baseOneMonthAverageDividendYield: Double,
    val baseOneMonthAverageMarketCap: Double,
    val baseTrailingFourQuarterTotalCashDividends: Double,
    /** Morningstar style model inputs live on an isolated synthetic-data stream. */
    val baseFutureEarningsPerShare: Double,
    val baseSalesPerShare: Double,
    val baseFutureLongTermEarningsGrowth: Double,
    val baseFutureShortTermEarningsGrowth: Double,
    val baseThreeYearHistoricalEarningsGrowth: Double,
    val baseThreeYearHistoricalSalesGrowth: Double,
    val baseCurrentInvestmentToAssets: Double,
    val baseReturnOnAssets: Double,
    val beta: Double,
    val annualVolatility: Double,
    val quality: Double,
    val value: Double,
)
