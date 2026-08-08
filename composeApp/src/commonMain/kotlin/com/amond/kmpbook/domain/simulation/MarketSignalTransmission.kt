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

data class MarketSignalTransmission(
    val originalSeed: CausalSignalSeed,
    val transmittedSeed: CausalSignalSeed,
    val reach: Double,
    /** 원신호 강도에 대한 도착시장 반응비. 확률이 아니므로 취약 국면에는 1을 넘을 수 있다. */
    val responseIntensity: Double,
    val representativePath: List<Market>,
    val dominantPathContribution: Double,
    val directExposure: Boolean,
) {
    init {
        require(originalSeed.factor == transmittedSeed.factor)
        require(originalSeed.direction == transmittedSeed.direction)
        require(reach.isFinite() && reach > 0.0 && reach <= 1.0)
        require(
            responseIntensity.isFinite() &&
                responseIntensity > 0.0 &&
                responseIntensity <= MAX_CAUSAL_MARKET_RESPONSE_INTENSITY,
        )
        require(representativePath.isNotEmpty())
        require(dominantPathContribution.isFinite() && dominantPathContribution > 0.0)
        require(dominantPathContribution <= reach)
    }

    val trace: CausalMarketTransmissionTrace
        get() = CausalMarketTransmissionTrace(
            markets = representativePath,
            reach = reach,
            dominantPathContribution = dominantPathContribution,
            responseIntensity = responseIntensity,
        )
}
