package com.amond.kmpbook.domain.simulation.fund

/**
 * 재구성 기준일에 알려진 synthetic 원천자료 스냅샷이다.
 *
 * 방법론 신호는 아래 계산 프로퍼티에서 원천값으로부터 매번 산출한다. 파생 비율을 별도 상태로
 * 진화시키지 않으므로 분자·분모와 신호가 서로 어긋날 수 없다.
 */
internal data class SimulatedReferenceEquitySnapshot(
    val definition: SimulatedReferenceEquity,
    val floatMarketCap: Double,
    val investableWeightFactor: Double,
    /** 관찰일 직전 3개월의 일별 거래대금 평균이다. */
    val threeMonthAverageDailyValueTraded: Double,
    /** 관찰일 직전 3개월의 일별 거래대금 중앙값이다. */
    val threeMonthMedianDailyValueTraded: Double,
    /** 관찰일 직전 125거래일의 일별 거래대금 평균이다. */
    val trailing125TradingDayAverageDailyValueTraded: Double,
    /** 관찰일 직전 12개월의 일별 거래대금 평균이다. */
    val twelveMonthAverageDailyValueTraded: Double,
    /** 관찰일 직전 6개월 중 최소 월간 주식 거래량이다. */
    val minimumSixMonthMonthlyShareVolume: Double,
    val latestQuarterGaapNetIncome: Double,
    val trailingFourQuarterGaapNetIncome: Double,
    val dividendPaymentYears: Int,
    val cashFlowFromOperations: Double,
    /** 양수로 기록한 자본적 지출 현금 유출액이다. */
    val capitalExpenditures: Double,
    val totalDebt: Double,
    val basicEarningsPerShare: Double,
    val bookValuePerShare: Double,
    val sharePrice: Double,
    /** 특별배당을 제외한 현재 정규 고정배당의 연환산 DPS다. */
    val regularFixedAnnualDividendPerShare: Double,
    /** 관찰 회계연도부터 역순인 최근 5개년 특별배당 제외 정규 DPS다. */
    val annualRegularDividendPerShareNewestFirst: List<Double>,
    val listingAgeYears: Int,
    val kospi200FinancialMember: Boolean,
    val threeYearAverageDividendPayoutRatio: Double,
    val threeYearAverageReturnOnEquity: Double,
    val oneMonthAverageDailyValueTraded: Double,
    val oneMonthAveragePriceToBookRatio: Double,
    val oneMonthAverageDividendYield: Double,
    val oneMonthAverageMarketCap: Double,
    val trailingFourQuarterTotalCashDividends: Double,
    val futureEarningsPerShare: Double,
    val salesPerShare: Double,
    val futureLongTermEarningsGrowth: Double,
    val futureShortTermEarningsGrowth: Double,
    val threeYearHistoricalEarningsGrowth: Double,
    val threeYearHistoricalSalesGrowth: Double,
    val currentInvestmentToAssets: Double,
    val returnOnAssets: Double,
) {
    init {
        require(floatMarketCap.isFinite() && floatMarketCap > 0.0)
        require(investableWeightFactor.isFinite() && investableWeightFactor in 0.0..1.0)
        require(investableWeightFactor > 0.0)
        require(
            threeMonthAverageDailyValueTraded.isFinite() &&
                threeMonthAverageDailyValueTraded >= 0.0,
        )
        require(
            threeMonthMedianDailyValueTraded.isFinite() &&
                threeMonthMedianDailyValueTraded >= 0.0,
        )
        require(
            trailing125TradingDayAverageDailyValueTraded.isFinite() &&
                trailing125TradingDayAverageDailyValueTraded >= 0.0,
        )
        require(
            twelveMonthAverageDailyValueTraded.isFinite() &&
                twelveMonthAverageDailyValueTraded >= 0.0,
        )
        require(
            minimumSixMonthMonthlyShareVolume.isFinite() &&
                minimumSixMonthMonthlyShareVolume >= 0.0,
        )
        require(latestQuarterGaapNetIncome.isFinite())
        require(trailingFourQuarterGaapNetIncome.isFinite())
        require(cashFlowFromOperations.isFinite())
        require(capitalExpenditures.isFinite() && capitalExpenditures >= 0.0)
        require(totalDebt.isFinite() && totalDebt >= 0.0)
        require(basicEarningsPerShare.isFinite())
        require(bookValuePerShare.isFinite() && bookValuePerShare != 0.0)
        require(sharePrice.isFinite() && sharePrice > 0.0)
        require(
            regularFixedAnnualDividendPerShare.isFinite() &&
                regularFixedAnnualDividendPerShare >= 0.0,
        )
        require(annualRegularDividendPerShareNewestFirst.size == DIVIDEND_HISTORY_YEARS)
        require(
            annualRegularDividendPerShareNewestFirst.all { dividend ->
                dividend.isFinite() && dividend >= 0.0
            },
        )
        require(annualRegularDividendPerShareNewestFirst.sum() > 0.0)
        require(listingAgeYears >= 0)
        require(
            threeYearAverageDividendPayoutRatio.isFinite() &&
                threeYearAverageDividendPayoutRatio >= 0.0,
        )
        require(threeYearAverageReturnOnEquity.isFinite())
        require(oneMonthAverageDailyValueTraded.isFinite() && oneMonthAverageDailyValueTraded >= 0.0)
        require(oneMonthAveragePriceToBookRatio.isFinite() && oneMonthAveragePriceToBookRatio >= 0.0)
        require(oneMonthAverageDividendYield.isFinite() && oneMonthAverageDividendYield >= 0.0)
        require(oneMonthAverageMarketCap.isFinite() && oneMonthAverageMarketCap > 0.0)
        require(
            trailingFourQuarterTotalCashDividends.isFinite() &&
                trailingFourQuarterTotalCashDividends > 0.0,
        )
        require(futureEarningsPerShare.isFinite())
        require(salesPerShare.isFinite() && salesPerShare > 0.0)
        require(futureLongTermEarningsGrowth.isFinite())
        require(futureShortTermEarningsGrowth.isFinite())
        require(threeYearHistoricalEarningsGrowth.isFinite())
        require(threeYearHistoricalSalesGrowth.isFinite())
        require(currentInvestmentToAssets.isFinite())
        require(returnOnAssets.isFinite())
    }

    /** 특별배당을 더하지 않은 정규 고정 IAD / 관찰일 주가다. */
    val indicatedDividendYield: Double
        get() = regularFixedAnnualDividendPerShare / sharePrice

    /** 이 synthetic universe는 회사마다 주상장 보통주 한 종목만 두므로 회사 총시가총액을 역산한다. */
    val totalCompanyMarketCap: Double
        get() = floatMarketCap / investableWeightFactor

    /** S&P 미국 주가지수 방법론의 연간 거래대금 / 유동시가총액(FALR) 정의다. */
    val floatAdjustedLiquidityRatio: Double
        get() = twelveMonthAverageDailyValueTraded * TRADING_DAYS_PER_YEAR / floatMarketCap

    val latestQuarterGaapNetIncomePositive: Boolean
        get() = latestQuarterGaapNetIncome > 0.0

    val trailingFourQuarterGaapNetIncomePositive: Boolean
        get() = trailingFourQuarterGaapNetIncome > 0.0

    val zeroTotalDebt: Boolean
        get() = totalDebt == 0.0

    /** 총부채가 0인 후보는 별도 신호로 우선순위를 정하므로 이 비율은 0으로 둔다. */
    val freeCashFlowToDebt: Double
        get() = if (zeroTotalDebt) 0.0 else (cashFlowFromOperations - capitalExpenditures) / totalDebt

    val returnOnEquity: Double
        get() = basicEarningsPerShare / bookValuePerShare

    val fiveYearDividendGrowth: Double
        get() {
            val fiveYearAverage = annualRegularDividendPerShareNewestFirst.average()
            return annualRegularDividendPerShareNewestFirst.first() / fiveYearAverage - 1.0
        }

    val negativeBookValuePerShare: Boolean
        get() = bookValuePerShare < 0.0

    val bookToPrice: Double
        get() = bookValuePerShare / sharePrice

    val futureEarningsToPrice: Double
        get() = futureEarningsPerShare / sharePrice

    val historicalEarningsToPrice: Double
        get() = basicEarningsPerShare / sharePrice

    /** Morningstar style DP: trailing four-quarter company cash dividends / company market cap. */
    val dividendToPrice: Double
        get() = trailingFourQuarterTotalCashDividends / totalCompanyMarketCap

    val salesToPrice: Double
        get() = salesPerShare / sharePrice

    private companion object {
        const val DIVIDEND_HISTORY_YEARS: Int = 5
        const val TRADING_DAYS_PER_YEAR: Double = 252.0
    }
}
