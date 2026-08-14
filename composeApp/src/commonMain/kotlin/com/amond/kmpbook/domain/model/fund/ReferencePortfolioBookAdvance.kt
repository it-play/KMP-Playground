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
        require(records == records.sortedWith(RECORD_ORDER))
        records.groupBy(ReferencePortfolioRecord::portfolioId).forEach { (portfolioId, grouped) ->
            val state = requireNotNull(book.states[portfolioId])
            require(grouped.all { it.benchmarkRef == state.benchmarkRef })
            require(grouped.zipWithNext().all { (previous, next) ->
                previous.revision + 1L == next.revision
            })
            require(grouped.last().revision == state.revision)
        }
    }

    companion object {
        private val RECORD_ORDER = compareBy<ReferencePortfolioRecord>(ReferencePortfolioRecord::portfolioId)
            .thenBy(ReferencePortfolioRecord::revision)
    }
}
