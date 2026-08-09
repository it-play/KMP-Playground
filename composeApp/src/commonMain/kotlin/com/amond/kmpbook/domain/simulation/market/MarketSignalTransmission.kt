package com.amond.kmpbook.domain.simulation.market

import com.amond.kmpbook.domain.model.causal.CausalMarketTransmissionTrace
import com.amond.kmpbook.domain.model.causal.CausalSignalSeed
import com.amond.kmpbook.domain.model.causal.MAX_CAUSAL_MARKET_RESPONSE_INTENSITY
import com.amond.kmpbook.domain.model.market.Market

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
