package com.amond.kmpbook.domain.model.fund

/** Pure result for one reference-market interval. Product tracking error is intentionally absent. */
data class ReferencePortfolioAdvance(
    val state: ReferencePortfolioState,
    val grossReferenceLogReturn: Double,
    val records: List<ReferencePortfolioRecord> = emptyList(),
) {
    init {
        require(grossReferenceLogReturn.isFinite())
        require(records.all { it.portfolioId == state.portfolioId })
        require(records.all { it.benchmarkRef == state.benchmarkRef })
        require(records.zipWithNext().all { (previous, next) ->
            previous.revision + 1L == next.revision
        })
        require(records.lastOrNull()?.revision == state.revision || records.isEmpty())
    }
}
