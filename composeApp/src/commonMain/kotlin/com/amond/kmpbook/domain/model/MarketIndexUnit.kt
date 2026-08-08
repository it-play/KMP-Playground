package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

enum class MarketIndexUnit(val displayName: String) {
    INDEX_POINTS("지수 포인트"),
    ANNUALIZED_VOLATILITY_PERCENT("연환산 변동성 %"),
}
