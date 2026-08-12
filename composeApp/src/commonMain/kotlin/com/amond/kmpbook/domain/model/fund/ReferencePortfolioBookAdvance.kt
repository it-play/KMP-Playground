package com.amond.kmpbook.domain.model.fund

/** One batch preview/final result, computed once per unique benchmark reference. */
data class ReferencePortfolioBookAdvance(
    val book: ReferencePortfolioBook,
    val grossReferenceLogReturns: Map<BenchmarkRef, Double>,
    val records: List<ReferencePortfolioRecord>,
) {
    init {
        require(
            grossReferenceLogReturns.keys ==
                book.states.values.mapTo(linkedSetOf(), ReferencePortfolioState::benchmarkRef),
        )
        require(grossReferenceLogReturns.values.all(Double::isFinite))
        require(records == records.sortedBy(ReferencePortfolioRecord::portfolioId))
        require(records.map(ReferencePortfolioRecord::portfolioId).distinct().size == records.size)
        require(records.all { record ->
            book.states[record.portfolioId]?.let { state ->
                state.benchmarkRef == record.benchmarkRef && state.revision == record.revision
            } == true
        })
    }
}
