package com.amond.kmpbook.domain.model

import kotlin.math.abs
import kotlin.time.Instant

enum class OrderType(val displayName: String) {
    MARKET("시장가"),
    LIMIT("지정가"),
}
