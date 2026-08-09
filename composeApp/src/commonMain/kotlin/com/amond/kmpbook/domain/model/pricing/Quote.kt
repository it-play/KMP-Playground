package com.amond.kmpbook.domain.model.pricing

import com.amond.kmpbook.domain.model.venue.MarketSession
import kotlin.time.Instant

/** 호가창과 종목 목록에 표시하는 한 시점의 시세. */
data class Quote(
    val stockId: String,
    val timestamp: Instant,
    val price: Double,
    val previousClose: Double,
    val open: Double = price,
    val high: Double = price,
    val low: Double = price,
    val volume: Long = 0L,
    val bidPrice: Double? = null,
    val askPrice: Double? = null,
    val bidQuantity: Double = 0.0,
    val askQuantity: Double = 0.0,
    val session: MarketSession = MarketSession.CLOSED,
) {
    init {
        require(stockId.isNotBlank()) { "종목 ID는 비어 있을 수 없습니다." }
        require(price >= 0.0 && previousClose >= 0.0) { "가격은 음수일 수 없습니다." }
        require(open >= 0.0 && high >= 0.0 && low >= 0.0) { "OHLC 가격은 음수일 수 없습니다." }
        require(high >= low) { "고가는 저가 이상이어야 합니다." }
        require(volume >= 0L) { "거래량은 음수일 수 없습니다." }
        require(bidQuantity >= 0.0 && askQuantity >= 0.0) { "호가 수량은 음수일 수 없습니다." }
    }

    val change: Double get() = price - previousClose
    val changeRate: Double get() = if (previousClose == 0.0) 0.0 else change / previousClose
    val spread: Double? get() = if (bidPrice != null && askPrice != null) askPrice - bidPrice else null
}
