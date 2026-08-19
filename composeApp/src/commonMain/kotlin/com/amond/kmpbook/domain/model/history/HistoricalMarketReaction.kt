package com.amond.kmpbook.domain.model.history

import com.amond.kmpbook.domain.model.market.Market
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** 휴장·시차를 보존한 시장별 최초 가격발견 시각이다. */
data class HistoricalMarketReaction(
    val market: Market,
    val priceDiscoveryAt: Instant,
    val observedTradingDate: LocalDate,
)
