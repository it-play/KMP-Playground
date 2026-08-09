package com.amond.kmpbook.domain.simulation.causal

import com.amond.kmpbook.domain.model.causal.CausalStockImpact

data class CausalPropagationResult(
    val impactsByStockId: Map<String, CausalStockImpact>,
    val decay: Double,
    val maxDepth: Int,
) {
    fun impactFor(stockId: String): CausalStockImpact? = impactsByStockId[stockId]
}
