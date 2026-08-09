package com.amond.kmpbook.domain.simulation.causal

import com.amond.kmpbook.domain.model.market.Market

/** 거래소 사이에서 같은 방향의 금융 신호가 이동하는 통로다. */
data class MarketContagionEdge(
    val from: Market,
    val to: Market,
    val weight: Double,
) {
    init {
        require(from != to) { "시장 전염 그래프의 자기 간선은 허용하지 않습니다." }
        require(weight.isFinite() && weight > 0.0 && weight <= 1.0) {
            "시장 연결 가중치는 0보다 크고 1 이하여야 합니다."
        }
    }
}
