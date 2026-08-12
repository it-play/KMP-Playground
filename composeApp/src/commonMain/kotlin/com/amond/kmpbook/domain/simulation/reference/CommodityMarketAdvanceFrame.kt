package com.amond.kmpbook.domain.simulation.reference

import kotlin.time.Instant

/** Complete deterministic input batch for advancing every shared commodity reference once. */
data class CommodityMarketAdvanceFrame(
    val spotInputs: List<CommoditySpotAdvanceInput>,
    val futuresInputs: List<FuturesAdvanceInput>,
    val from: Instant,
    val to: Instant,
) {
    init {
        require(to > from)
        require(spotInputs.isNotEmpty() || futuresInputs.isNotEmpty())
        require(spotInputs == spotInputs.sortedBy { it.state.benchmarkRef })
        require(futuresInputs == futuresInputs.sortedBy { it.state.benchmarkRef })
        require(spotInputs.map { it.state.benchmarkRef }.distinct().size == spotInputs.size)
        require(futuresInputs.map { it.state.benchmarkRef }.distinct().size == futuresInputs.size)
        require(spotInputs.all { it.state.asOf == from && it.to == to })
        require(futuresInputs.all { it.state.asOf == from && it.to == to })
    }
}
