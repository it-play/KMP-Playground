package com.amond.kmpbook.domain.model.causal

import com.amond.kmpbook.domain.model.market.Market

/**
 * 사건이 발생한 순간의 시장 취약도 입력이다. 이벤트에 고정해 가격 엔진과 과거 뉴스가
 * 같은 비선형 반응을 재현하도록 한다.
 */
data class CausalMarketRegimeSnapshot(
    val riskSentiment: Double = 0.0,
    val volatilityRegime: Double = 1.0,
    val usdKrwChangeRate: Double = 0.0,
    val marketHourlyReturns: Map<Market, Double> = emptyMap(),
    val marketChangeFromPreviousClose: Map<Market, Double> = emptyMap(),
) {
    init {
        require(riskSentiment.isFinite() && riskSentiment in -1.0..1.0)
        require(volatilityRegime.isFinite() && volatilityRegime in 0.1..10.0)
        require(usdKrwChangeRate.isFinite() && usdKrwChangeRate in -0.25..0.25)
        require(marketHourlyReturns.values.all(Double::isFinite))
        require(marketChangeFromPreviousClose.values.all(Double::isFinite))
    }
}
