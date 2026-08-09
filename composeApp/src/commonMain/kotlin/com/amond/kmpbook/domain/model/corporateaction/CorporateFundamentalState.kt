package com.amond.kmpbook.domain.model.corporateaction

import com.amond.kmpbook.domain.model.instrument.QuarterlyFinancialReport
import kotlin.time.Instant

/** 저장되는 기업 원시 재무 상태다. PER·ROE 같은 파생 비율은 포함하지 않는다. */
data class CorporateFundamentalState(
    val stockId: String,
    val quarters: List<QuarterlyFinancialReport>,
    val bookEquity: Double,
    val equityAtTtmStart: Double,
    val appliedEarningsOccurrenceIds: List<String>,
    val asOf: Instant,
) {
    init {
        require(stockId.isNotBlank())
        require(quarters.size == 4) { "기업 재무 상태에는 최근 4개 분기가 필요합니다." }
        require(quarters.zipWithNext().all { (left, right) -> left.reportedAt < right.reportedAt })
        require(bookEquity.isFinite() && bookEquity > 0.0)
        require(equityAtTtmStart.isFinite() && equityAtTtmStart > 0.0)
        require(appliedEarningsOccurrenceIds.all(String::isNotBlank))
        require(appliedEarningsOccurrenceIds.distinct().size == appliedEarningsOccurrenceIds.size)
        require(quarters.last().reportedAt <= asOf)
    }
}
