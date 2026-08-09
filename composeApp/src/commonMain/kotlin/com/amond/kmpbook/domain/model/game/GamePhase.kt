package com.amond.kmpbook.domain.model.game

enum class GamePhase(val displayName: String) {
    SETUP("게임 준비"),
    PLAYING("진행 중"),
    PAUSED("일시 정지"),
    SETTLEMENT("최종 정산"),
    FINISHED("게임 종료"),
}
