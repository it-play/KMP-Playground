package com.amond.kmpbook.domain.simulation.market

/**
 * 게임 밖에서 주입하는 시장 환경의 목표값이다. 모든 값은 0(매우 낮음)부터
 * 1(매우 높음)까지 정규화하며, 가격에는 직접 들어가지 않고 [MarketDynamicsEngine]이
 * 시간에 따라 평활화한 뒤 잠재 변동성·뉴스 강도·수급·유동성으로 변환한다.
 */
data class ExternalMarketForces(
    val chaos: Double = AUGUST_2026_BASELINE_CHAOS,
    val worldTension: Double = AUGUST_2026_BASELINE_WORLD_TENSION,
    val retailBuyingPower: Double = AUGUST_2026_BASELINE_RETAIL_BUYING_POWER,
    val institutionalBuyingPower: Double = AUGUST_2026_BASELINE_INSTITUTIONAL_BUYING_POWER,
    val marketLiquidity: Double = AUGUST_2026_BASELINE_MARKET_LIQUIDITY,
    val economicMomentum: Double = AUGUST_2026_BASELINE_ECONOMIC_MOMENTUM,
) {
    init {
        require(values.all { it.isFinite() && it in MIN_VALUE..MAX_VALUE }) {
            "External market forces must be finite values in [0, 1]"
        }
    }

    val values: List<Double>
        get() = listOf(
            chaos,
            worldTension,
            retailBuyingPower,
            institutionalBuyingPower,
            marketLiquidity,
            economicMomentum,
        )

    fun interpolate(target: ExternalMarketForces, rate: Double): ExternalMarketForces {
        require(rate in 0.0..1.0)
        fun blend(current: Double, next: Double): Double = current + (next - current) * rate
        return ExternalMarketForces(
            chaos = blend(chaos, target.chaos),
            worldTension = blend(worldTension, target.worldTension),
            retailBuyingPower = blend(retailBuyingPower, target.retailBuyingPower),
            institutionalBuyingPower = blend(institutionalBuyingPower, target.institutionalBuyingPower),
            marketLiquidity = blend(marketLiquidity, target.marketLiquidity),
            economicMomentum = blend(economicMomentum, target.economicMomentum),
        )
    }

    companion object {
        const val MIN_VALUE: Double = 0.0
        const val MAX_VALUE: Double = 1.0

        // 2026-08-01의 기본 캠페인은 불안정한 반등 국면이지만 즉시 위기인 상태는 아니다.
        const val AUGUST_2026_BASELINE_CHAOS: Double = 0.56
        const val AUGUST_2026_BASELINE_WORLD_TENSION: Double = 0.67
        const val AUGUST_2026_BASELINE_RETAIL_BUYING_POWER: Double = 0.54
        const val AUGUST_2026_BASELINE_INSTITUTIONAL_BUYING_POWER: Double = 0.57
        const val AUGUST_2026_BASELINE_MARKET_LIQUIDITY: Double = 0.58
        const val AUGUST_2026_BASELINE_ECONOMIC_MOMENTUM: Double = 0.48
    }
}
