package com.amond.kmpbook.domain.model

import kotlin.math.abs
import kotlin.time.Instant

enum class TimeInForce(val displayName: String) {
    DAY("당일 주문"),
    GOOD_TILL_CANCELLED("취소 전까지"),
    IMMEDIATE_OR_CANCEL("즉시 체결 후 잔량 취소"),
    FILL_OR_KILL("전량 즉시 체결"),
}
