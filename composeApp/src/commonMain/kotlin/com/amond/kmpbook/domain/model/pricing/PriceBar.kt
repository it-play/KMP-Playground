package com.amond.kmpbook.domain.model.pricing

import kotlin.time.Instant

/** 차트의 OHLCV 봉 하나. [startTime, endTime) 구간을 나타낸다. */
data class PriceBar(
    val stockId: String,
    val startTime: Instant,
    val endTime: Instant,
    val step: PriceBarInterval,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
) {
    init {
        require(stockId.isNotBlank()) { "종목 ID는 비어 있을 수 없습니다." }
        require(endTime > startTime) { "가격 봉 종료 시각은 시작 시각보다 뒤여야 합니다." }
        require(open >= 0.0 && high >= 0.0 && low >= 0.0 && close >= 0.0) {
            "OHLC 가격은 음수일 수 없습니다."
        }
        require(high >= maxOf(open, close, low)) { "고가는 시가·종가·저가 이상이어야 합니다." }
        require(low <= minOf(open, close, high)) { "저가는 시가·종가·고가 이하여야 합니다." }
        require(volume >= 0L) { "거래량은 음수일 수 없습니다." }
    }

    val interval: PriceBarInterval get() = step
    val change: Double get() = close - open
    val changeRate: Double get() = if (open == 0.0) 0.0 else change / open
    val isRising: Boolean get() = close > open
}
