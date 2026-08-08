package com.amond.kmpbook.domain.simulation

import com.amond.kmpbook.domain.data.CausalExposureCatalog
import com.amond.kmpbook.domain.data.CausalStockExposure
import com.amond.kmpbook.domain.model.CausalEconomicFactor
import com.amond.kmpbook.domain.model.CausalImpactTrace
import com.amond.kmpbook.domain.model.CausalSignalDirection
import com.amond.kmpbook.domain.model.CausalSignalSeed
import com.amond.kmpbook.domain.model.CausalStockImpact
import com.amond.kmpbook.domain.model.CausalTraceNode
import com.amond.kmpbook.domain.model.CausalTraceNodeKind
import com.amond.kmpbook.domain.model.ImpactDirection
import com.amond.kmpbook.domain.model.StockDefinition
import kotlin.math.abs
import kotlin.math.pow

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
