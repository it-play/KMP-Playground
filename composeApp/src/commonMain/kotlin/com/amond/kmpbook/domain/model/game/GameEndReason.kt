package com.amond.kmpbook.domain.model.game

enum class GameEndReason(val displayName: String) {
    DATE_LIMIT("2040년 12월 31일 도달"),
    BANKRUPTCY("파산"),
    PLAYER_FINISHED("사용자 종료"),
}
