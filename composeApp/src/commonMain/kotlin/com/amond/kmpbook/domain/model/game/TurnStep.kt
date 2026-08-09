package com.amond.kmpbook.domain.model.game

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/** 한 번의 진행 명령이 소비하는 기본 1시간 턴 수. */
enum class TurnStep(
    val hours: Int,
    val displayName: String,
) {
    ONE_HOUR(1, "1시간"),
    FOUR_HOURS(4, "4시간"),
    TWELVE_HOURS(12, "12시간"),
    ONE_DAY(24, "1일"),
    ONE_WEEK(168, "1주일"),
    ;

    val baseTurns: Int get() = hours
    val duration: Duration get() = hours.hours
}
