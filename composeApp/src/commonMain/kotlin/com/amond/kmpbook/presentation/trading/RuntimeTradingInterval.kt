package com.amond.kmpbook.presentation.trading

import kotlin.time.Instant
import kotlinx.datetime.minus
import kotlinx.datetime.plus

internal data class RuntimeTradingInterval(
    val startsAt: Instant,
    val endsAt: Instant,
) {
    init {
        require(endsAt > startsAt) { "거래 가능 구간의 종료 시각은 시작 시각보다 뒤여야 합니다." }
    }
}
