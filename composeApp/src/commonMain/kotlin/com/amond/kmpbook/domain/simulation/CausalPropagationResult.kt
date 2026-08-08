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

data class CausalPropagationResult(
    val impactsByStockId: Map<String, CausalStockImpact>,
    val decay: Double,
    val maxDepth: Int,
) {
    fun impactFor(stockId: String): CausalStockImpact? = impactsByStockId[stockId]
}
