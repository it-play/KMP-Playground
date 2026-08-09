package com.amond.kmpbook.presentation.simulator

import com.amond.kmpbook.domain.simulation.market.ExternalMarketForces

data class NewGameOptions(
    val initialCapitalKrw: Double = 100_000_000.0,
    val seed: Long = DEFAULT_SEED,
    val usFractionalTrading: Boolean = false,
    val autoExchange: Boolean = true,
    val initialUsdKrw: Double = 1_350.0,
    /** 2026년 8월 기본 시나리오이자 게임 시작 시 동역학 엔진에 주입할 목표 환경. */
    val initialExternalMarketForces: ExternalMarketForces = ExternalMarketForces(),
) {
    init {
        require(initialCapitalKrw >= MIN_INITIAL_CAPITAL_KRW && initialCapitalKrw.isFinite()) {
            "초기 자금은 100만원 이상이어야 합니다."
        }
        require(initialUsdKrw > 0.0 && initialUsdKrw.isFinite()) {
            "초기 원·달러 환율은 0보다 커야 합니다."
        }
    }

    companion object {
        const val MIN_INITIAL_CAPITAL_KRW: Double = 1_000_000.0
        const val DEFAULT_SEED: Long = 20_260_807L
    }
}
