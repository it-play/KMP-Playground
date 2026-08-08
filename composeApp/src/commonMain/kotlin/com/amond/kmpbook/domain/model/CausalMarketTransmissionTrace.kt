package com.amond.kmpbook.domain.model

/**
 * 경제 요인 그래프에 들어오기 전에 신호가 거래소 사이를 이동한 대표 경로다.
 *
 * [reach]는 가능한 시장 경로들을 noisy-OR로 합성한 전염도이고,
 * [dominantPathContribution]은 화면에 보존한 대표 경로 하나의 기여도다.
 */
data class CausalMarketTransmissionTrace(
    val markets: List<Market>,
    val reach: Double,
    val dominantPathContribution: Double,
    /** 도착시장 취약도를 반영한 반응강도. 확률이 아니므로 1을 넘을 수 있다. */
    val responseIntensity: Double = reach,
) {
    init {
        require(markets.isNotEmpty()) { "시장 전염 경로에는 시장이 하나 이상 필요합니다." }
        require(markets.zipWithNext().none { (left, right) -> left == right }) {
            "시장 전염 경로는 같은 시장을 연속해서 방문할 수 없습니다."
        }
        require(reach.isFinite() && reach > 0.0 && reach <= 1.0) {
            "시장 전염도는 0보다 크고 1 이하여야 합니다."
        }
        require(
            dominantPathContribution.isFinite() &&
                dominantPathContribution > 0.0 &&
                dominantPathContribution <= reach,
        ) { "대표 시장 경로 기여도는 0보다 크고 전체 전염도 이하여야 합니다." }
        require(
            responseIntensity.isFinite() &&
                responseIntensity > 0.0 &&
                responseIntensity <= MAX_CAUSAL_MARKET_RESPONSE_INTENSITY,
        ) {
            "시장 반응강도는 0보다 크고 1.5 이하여야 합니다."
        }
    }

    val isCrossMarket: Boolean get() = markets.size > 1
    val labels: List<String> get() = markets.map(Market::displayName)
    val responseGain: Double get() = responseIntensity / reach
}
