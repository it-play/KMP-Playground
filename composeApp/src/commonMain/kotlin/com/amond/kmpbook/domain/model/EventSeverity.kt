package com.amond.kmpbook.domain.model

import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

enum class EventSeverity(
    val displayName: String,
    val level: Int,
) {
    MINOR("경미", 1),
    MODERATE("보통", 2),
    MAJOR("중대", 3),
    CRITICAL("심각", 4),
}
