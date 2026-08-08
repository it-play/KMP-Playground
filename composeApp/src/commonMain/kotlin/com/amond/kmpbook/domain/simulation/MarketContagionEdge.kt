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

/** 거래소 사이에서 같은 방향의 금융 신호가 이동하는 통로다. */
data class MarketContagionEdge(
    val from: Market,
    val to: Market,
    val weight: Double,
) {
    init {
        require(from != to) { "시장 전염 그래프의 자기 간선은 허용하지 않습니다." }
        require(weight.isFinite() && weight > 0.0 && weight <= 1.0) {
            "시장 연결 가중치는 0보다 크고 1 이하여야 합니다."
        }
    }
}
