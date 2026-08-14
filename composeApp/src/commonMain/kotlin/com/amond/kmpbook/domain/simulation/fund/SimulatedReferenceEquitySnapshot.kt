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
    /** 관찰일 직전 3개월의 일별 거래대금 평균이다. */
    val threeMonthAverageDailyValueTraded: Double,
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
) {
    init {
        require(floatMarketCap.isFinite() && floatMarketCap > 0.0)
        require(
            threeMonthAverageDailyValueTraded.isFinite() &&
                threeMonthAverageDailyValueTraded >= 0.0,
        )
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
    }

    /** 특별배당을 더하지 않은 정규 고정 IAD / 관찰일 주가다. */
    val indicatedDividendYield: Double
        get() = regularFixedAnnualDividendPerShare / sharePrice

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

    private companion object {
        const val DIVIDEND_HISTORY_YEARS: Int = 5
    }
}
