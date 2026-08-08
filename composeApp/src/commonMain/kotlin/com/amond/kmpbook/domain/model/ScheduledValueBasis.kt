package com.amond.kmpbook.domain.model

import kotlin.time.Instant

/** Calendar dates are deterministic; released figures remain explicitly fictional game data. */
enum class ScheduledValueBasis(val displayName: String) {
    GAME_GENERATED("게임 수치"),
}
