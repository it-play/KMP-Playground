package com.amond.kmpbook.presentation.simulator

import com.amond.kmpbook.domain.simulation.market.ExternalMarketForces

data class NewGameOptions(
    val scenarioName: String = DEFAULT_SCENARIO_NAME,
    val difficultyName: String = DEFAULT_DIFFICULTY_NAME,
    val initialCapitalKrw: Double = 100_000_000.0,
    val seed: Long = DEFAULT_SEED,
    val usFractionalTrading: Boolean = false,
    val autoExchange: Boolean = true,
    val ironmanMode: Boolean = false,
    val initialUsdKrw: Double = 1_350.0,
    /** 2026년 8월 기본 시나리오이자 게임 시작 시 동역학 엔진에 주입할 목표 환경. */
    val initialExternalMarketForces: ExternalMarketForces = ExternalMarketForces(),
) {
    init {
        require(scenarioName.isNotBlank() && scenarioName.length <= MAX_GAME_LABEL_LENGTH) {
            "시나리오 이름은 1자 이상 ${MAX_GAME_LABEL_LENGTH}자 이하여야 합니다."
        }
        require(difficultyName.isNotBlank() && difficultyName.length <= MAX_GAME_LABEL_LENGTH) {
            "난이도 이름은 1자 이상 ${MAX_GAME_LABEL_LENGTH}자 이하여야 합니다."
        }
        require(initialCapitalKrw >= MIN_INITIAL_CAPITAL_KRW && initialCapitalKrw.isFinite()) {
            "초기 자금은 100만원 이상이어야 합니다."
        }
        require(initialUsdKrw > 0.0 && initialUsdKrw.isFinite()) {
            "초기 원·달러 환율은 0보다 커야 합니다."
        }
    }

    companion object {
        const val DEFAULT_SCENARIO_NAME: String = "2026년 8월"
        const val DEFAULT_DIFFICULTY_NAME: String = "혼조"
        const val MAX_GAME_LABEL_LENGTH: Int = 24
        const val MIN_INITIAL_CAPITAL_KRW: Double = 1_000_000.0
        const val DEFAULT_SEED: Long = 20_260_807L
    }
}
