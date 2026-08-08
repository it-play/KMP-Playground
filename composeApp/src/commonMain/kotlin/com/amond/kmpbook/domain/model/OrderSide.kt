package com.amond.kmpbook.domain.model

import kotlin.math.abs
import kotlin.time.Instant

enum class OrderSide(val displayName: String, val cashFlowSign: Int) {
    BUY("매수", -1),
    SELL("매도", 1),
}
