package com.amond.kmpbook.domain.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

enum class GameEndReason(val displayName: String) {
    DATE_LIMIT("2040년 12월 31일 도달"),
    BANKRUPTCY("파산"),
    PLAYER_FINISHED("사용자 종료"),
}
