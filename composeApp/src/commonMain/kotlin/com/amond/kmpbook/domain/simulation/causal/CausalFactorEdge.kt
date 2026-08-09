package com.amond.kmpbook.domain.simulation.causal

import com.amond.kmpbook.domain.model.causal.CausalEconomicFactor
import kotlin.math.abs

data class CausalFactorEdge(
    val from: CausalEconomicFactor,
    val to: CausalEconomicFactor,
    val weight: Double,
) {
    init {
        require(from != to) { "인과 그래프의 자기 간선은 허용하지 않습니다." }
        require(weight.isFinite() && weight != 0.0 && abs(weight) <= 1.0) {
            "인과 간선 가중치의 절댓값은 1 이하여야 합니다."
        }
    }
}
