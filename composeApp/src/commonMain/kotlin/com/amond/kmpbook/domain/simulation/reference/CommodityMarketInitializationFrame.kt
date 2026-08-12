package com.amond.kmpbook.domain.simulation.reference

import com.amond.kmpbook.domain.model.reference.CommoditySpotInitialization
import com.amond.kmpbook.domain.model.reference.FuturesInitialization
import kotlin.time.Instant

/** Canonical market marks from which the shared commodity reference book is bootstrapped. */
data class CommodityMarketInitializationFrame(
    val spotInitializations: List<CommoditySpotInitialization>,
    val futuresInitializations: List<FuturesInitialization>,
    val asOf: Instant,
) {
    init {
        require(spotInitializations.isNotEmpty() || futuresInitializations.isNotEmpty())
        require(spotInitializations == spotInitializations.sortedBy { it.terms.benchmarkRef })
        require(futuresInitializations == futuresInitializations.sortedBy { it.terms.benchmarkRef })
        val spotRefs = spotInitializations.map { it.terms.benchmarkRef }
        val futuresRefs = futuresInitializations.map { it.terms.benchmarkRef }
        require(spotRefs.distinct().size == spotRefs.size)
        require(futuresRefs.distinct().size == futuresRefs.size)
        require(spotRefs.toSet().intersect(futuresRefs.toSet()).isEmpty())
        require(futuresInitializations.flatMap { it.curvesBySleeveId.values }.all { it.asOf == asOf })
    }
}
