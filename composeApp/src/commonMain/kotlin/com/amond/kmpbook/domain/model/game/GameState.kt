package com.amond.kmpbook.domain.model.game

import kotlin.time.Instant

/** 엔진의 가변 상태를 UI에 전달할 때 사용하는 불변 게임 진행 스냅샷. */
data class GameState(
    val phase: GamePhase,
    val screen: Screen,
    val currentTime: Instant,
    val turn: Long,
    val selectedTurnStep: TurnStep,
    val selectedStockId: String? = null,
    val activeEventIds: List<String> = emptyList(),
    val endReason: GameEndReason? = null,
) {
    init {
        require(turn >= 0L) { "턴 번호는 음수일 수 없습니다." }
        require(phase == GamePhase.FINISHED || endReason == null) {
            "종료 사유는 종료된 게임에만 지정할 수 있습니다."
        }
    }
}
