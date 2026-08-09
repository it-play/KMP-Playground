package com.amond.kmpbook.domain.model.instrument

import kotlin.time.Instant

const val MIN_FUND_REFERENCE_VALUE: Double = 0.000001
const val MAX_FUND_REFERENCE_VALUE: Double = 1.0e18

/** 저장되는 ETF·ETN·폐쇄형 펀드의 원시 좌수와 기준가 상태다. */
data class FundFinancialState(
    val stockId: String,
    val navPerUnit: Double,
    val indicativeValuePerUnit: Double,
    val unitsOrNotesOutstanding: Double,
    val lastNetFlow: Double,
    val asOf: Instant,
) {
    init {
        require(stockId.isNotBlank())
        require(navPerUnit in MIN_FUND_REFERENCE_VALUE..MAX_FUND_REFERENCE_VALUE)
        require(indicativeValuePerUnit in MIN_FUND_REFERENCE_VALUE..MAX_FUND_REFERENCE_VALUE)
        require(unitsOrNotesOutstanding.isFinite() && unitsOrNotesOutstanding > 0.0)
        require(lastNetFlow.isFinite())
    }
}
