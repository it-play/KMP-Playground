package com.amond.kmpbook.domain.simulation.causal

import com.amond.kmpbook.domain.model.market.Market

data class MarketContagionPath(
    val markets: List<Market>,
    val contribution: Double,
) {
    init {
        require(markets.size >= 2) { "해외 시장 전염 경로에는 출발과 도착 시장이 필요합니다." }
        require(markets.distinct().size == markets.size) { "시장 전염 경로는 시장을 재방문할 수 없습니다." }
        require(contribution.isFinite() && contribution > 0.0 && contribution <= 1.0)
    }
}
