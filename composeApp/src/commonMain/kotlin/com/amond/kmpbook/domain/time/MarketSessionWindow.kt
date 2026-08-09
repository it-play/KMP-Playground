package com.amond.kmpbook.domain.time

import com.amond.kmpbook.domain.model.market.Market
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

data class MarketSessionWindow(
    val market: Market,
    val localDate: LocalDate,
    val opensAt: Instant,
    val closesAt: Instant,
) {
    init {
        require(closesAt > opensAt) { "시장 종료 시각은 개장 시각보다 뒤여야 합니다." }
    }
}
