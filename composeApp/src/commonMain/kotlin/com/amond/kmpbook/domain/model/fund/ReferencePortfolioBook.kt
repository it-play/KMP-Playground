package com.amond.kmpbook.domain.model.fund

/** All unique benchmark reference portfolios in one campaign. */
data class ReferencePortfolioBook(
    val states: Map<String, ReferencePortfolioState>,
) {
    init {
        require(states.isNotEmpty() && states.size <= MAX_REFERENCE_PORTFOLIOS)
        require(states.all { (portfolioId, state) -> portfolioId == state.portfolioId })
        require(states.values.map(ReferencePortfolioState::benchmarkRef).distinct().size == states.size) {
            "A benchmark reference may have only one campaign portfolio state."
        }
    }

    companion object {
        const val MAX_REFERENCE_PORTFOLIOS: Int = 4_096
    }
}
