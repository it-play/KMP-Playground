package com.amond.kmpbook.domain.model.fund

/** Pure result for one reference-market interval. Product tracking error is intentionally absent. */
data class ReferencePortfolioAdvance(
    val state: ReferencePortfolioState,
    val grossReferenceLogReturn: Double,
    val record: ReferencePortfolioRecord? = null,
) {
    init {
        require(grossReferenceLogReturn.isFinite())
        require(record == null || record.portfolioId == state.portfolioId)
        require(record == null || record.benchmarkRef == state.benchmarkRef)
        require(record == null || record.revision == state.revision)
    }
}
