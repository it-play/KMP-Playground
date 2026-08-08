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

data class MarketContagionPath(
    val markets: List<Market>,
    val contribution: Double,
) {
    init {
        require(markets.size >= 2) { "해외 시장 전염 경로에는 출발과 도착 시장이 필요합니다." }
        require(markets.distinct().size == markets.size) { "시장 전염 경로는 시장을 재방문할 수 없습니다." }
        require(contribution.isFinite() && contribution > 0.0 && contribution <= 1.0)
    }
}
