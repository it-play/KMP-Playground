package com.amond.kmpbook.domain.simulation.causal

import com.amond.kmpbook.domain.model.causal.CausalSignalSeed
import com.amond.kmpbook.domain.simulation.market.MarketSignalTransmission

data class MarketContagionResult(
    val transmissions: List<MarketSignalTransmission>,
) {
    val transmittedSeeds: List<CausalSignalSeed>
        get() = transmissions.map(MarketSignalTransmission::transmittedSeed)
}
