package com.amond.kmpbook.domain.model.instrument

import kotlin.time.Instant

/** 한 분기의 원시 재무 실적이다. 금액 단위는 종목의 상장 통화다. */
data class QuarterlyFinancialReport(
    val periodId: String,
    val reportedAt: Instant,
    val revenue: Double,
    val netIncome: Double,
    val dilutedShares: Double,
    val sourceOccurrenceId: String? = null,
) {
    init {
        require(periodId.isNotBlank())
        require(revenue.isFinite() && revenue >= 0.0)
        require(netIncome.isFinite())
        require(dilutedShares.isFinite() && dilutedShares > 0.0)
        require(sourceOccurrenceId == null || sourceOccurrenceId.isNotBlank())
    }
}
