package com.amond.kmpbook.domain.simulation.reference

import com.amond.kmpbook.domain.model.reference.CommoditySpotReferenceState
import com.amond.kmpbook.domain.model.reference.CommoditySpotReferenceTerms
import kotlin.time.Instant

/** Injected spot mark, cash rate and elapsed time for one pure holding interval. */
data class CommoditySpotAdvanceInput(
    val state: CommoditySpotReferenceState,
    val terms: CommoditySpotReferenceTerms,
    val currentSpotLevel: Double,
    val cashRateAnnual: Double,
    val elapsedYearFraction: Double,
    val to: Instant,
) {
    init {
        require(state.benchmarkRef == terms.benchmarkRef)
        require(state.assetClass == terms.assetClass && state.baseCurrency == terms.baseCurrency)
        require(currentSpotLevel.isFinite() && currentSpotLevel > 0.0)
        require(cashRateAnnual.isFinite() && cashRateAnnual in -0.10..1.0)
        require(elapsedYearFraction.isFinite() && elapsedYearFraction in 0.0..1.0)
        require(to > state.asOf)
    }
}
