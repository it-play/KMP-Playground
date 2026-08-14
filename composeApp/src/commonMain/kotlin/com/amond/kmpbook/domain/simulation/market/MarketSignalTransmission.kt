package com.amond.kmpbook.domain.simulation.market

import com.amond.kmpbook.domain.model.causal.CausalMarketTransmissionTrace
import com.amond.kmpbook.domain.model.causal.CausalSignalSeed
import com.amond.kmpbook.domain.model.causal.MAX_CAUSAL_MARKET_RESPONSE_INTENSITY
import com.amond.kmpbook.domain.model.market.Market

/**
 * 한 seed의 시장층 결과다. [transmittedSeed]는 [originalSeed]와 같은 요인·방향을 유지하며,
 * 그 강도는 reach와 도착시장 반응이 이미 한 번 반영된 유효 강도다. 다음 인과 그래프는 이 값을
 * 원본 강도에 다시 곱하지 않고 새 시작 강도로 사용한다.
 */
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
