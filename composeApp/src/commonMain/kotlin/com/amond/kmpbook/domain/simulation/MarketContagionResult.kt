package com.amond.kmpbook.domain.simulation

import com.amond.kmpbook.domain.model.CausalEconomicFactor
import com.amond.kmpbook.domain.model.CausalMarketRegimeSnapshot
import com.amond.kmpbook.domain.model.CausalMarketTransmissionTrace
import com.amond.kmpbook.domain.model.CausalSignalSeed
import com.amond.kmpbook.domain.model.CausalStockImpact
import com.amond.kmpbook.domain.model.CausalTransmissionProfile
import com.amond.kmpbook.domain.model.EtfExposureRegion
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.MAX_CAUSAL_MARKET_RESPONSE_INTENSITY
import com.amond.kmpbook.domain.model.MIN_CAUSAL_SIGNAL_STRENGTH
import com.amond.kmpbook.domain.model.StockDefinition
import kotlin.math.pow

data class MarketContagionResult(
    val transmissions: List<MarketSignalTransmission>,
) {
    val transmittedSeeds: List<CausalSignalSeed>
        get() = transmissions.map(MarketSignalTransmission::transmittedSeed)
}
