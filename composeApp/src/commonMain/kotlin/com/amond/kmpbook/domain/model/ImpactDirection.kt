package com.amond.kmpbook.domain.model

import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

enum class ImpactDirection(val displayName: String) {
    POSITIVE("호재"),
    NEGATIVE("악재"),
    MIXED("혼조"),
    NEUTRAL("중립"),
}
