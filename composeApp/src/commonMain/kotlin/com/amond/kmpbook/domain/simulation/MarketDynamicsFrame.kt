package com.amond.kmpbook.domain.simulation

import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.Sector

/** 한 시간 동안 모든 가격·뉴스·호가 소비자가 공유하는 불변 해석 프레임이다. */
data class MarketDynamicsFrame(
    val effectiveForces: ExternalMarketForces,
    val regimeProbabilities: MarketRegimeProbabilities,
    val volatilityRegime: Double,
    val newsHazardMultiplier: Double,
    val liquidityStress: Double,
    val retailFlow: Double,
    val institutionalFlow: Double,
    val crossMarketCorrelation: Double,
    val marketReturns: Map<Market, Double>,
    val sectorReturns: Map<Sector, Double>,
) {
    init {
        require(volatilityRegime.isFinite() && volatilityRegime in 0.5..4.0)
        require(newsHazardMultiplier.isFinite() && newsHazardMultiplier in 0.25..3.5)
        require(liquidityStress.isFinite() && liquidityStress in 0.0..1.0)
        require(retailFlow.isFinite() && retailFlow in -1.0..1.0)
        require(institutionalFlow.isFinite() && institutionalFlow in -1.0..1.0)
        require(crossMarketCorrelation.isFinite() && crossMarketCorrelation in 0.0..1.0)
        require(marketReturns.keys == Market.entries.toSet() && marketReturns.values.all(Double::isFinite))
        require(sectorReturns.keys == Sector.entries.toSet() && sectorReturns.values.all(Double::isFinite))
    }
}
