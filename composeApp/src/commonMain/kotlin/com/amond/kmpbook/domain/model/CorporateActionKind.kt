package com.amond.kmpbook.domain.model

import kotlin.math.abs
import kotlin.time.Instant

enum class CorporateActionKind(val displayName: String) {
    FORWARD_SPLIT("주식분할"),
    REVERSE_SPLIT("주식병합"),
}
