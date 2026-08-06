package com.amond.kmpbook.domain.model

import kotlin.math.abs
import kotlin.math.round
import kotlin.time.Instant

/**
 * 시뮬레이션 종목의 변하지 않는 메타데이터다.
 *
 * 새 종목은 [StockCatalog][com.amond.kmpbook.domain.data.StockCatalog]의 목록에 이 데이터 한 건만
 * 추가하면 된다. 미국 소수점 거래를 활성화할 때는 [quantityStep]을 0.000001처럼 낮춘다.
 */
data class StockDefinition(
    val symbol: String,
    val name: String,
    val englishName: String,
    val market: Market,
    val sector: Sector,
    val initialPrice: Double,
    val volatility: Double,
    val dividendYield: Double,
    val marketCap: Double,
    val sharesOutstanding: Long,
    val description: String,
    val beta: Double = 1.0,
    val quantityStep: Double = 1.0,
    val lotSize: Double = 1.0,
) {
    init {
        require(symbol.isNotBlank()) { "종목 코드는 비어 있을 수 없습니다." }
        require(name.isNotBlank()) { "종목명은 비어 있을 수 없습니다." }
        require(initialPrice > 0.0) { "기준 가격은 0보다 커야 합니다." }
        require(volatility >= 0.0) { "변동성은 음수일 수 없습니다." }
        require(dividendYield >= 0.0) { "배당수익률은 음수일 수 없습니다." }
        require(marketCap > 0.0) { "시가총액은 0보다 커야 합니다." }
        require(sharesOutstanding > 0L) { "발행주식 수는 0보다 커야 합니다." }
        require(beta >= 0.0) { "베타는 음수일 수 없습니다." }
        require(quantityStep > 0.0) { "수량 단위는 0보다 커야 합니다." }
        require(lotSize > 0.0) { "매매 단위는 0보다 커야 합니다." }
    }

    /** 시장까지 포함하므로 같은 티커가 다른 시장에 있어도 충돌하지 않는다. */
    val id: String get() = "${market.name}:$symbol"
    val currency: Currency get() = market.currency
    val supportsFractional: Boolean get() = quantityStep < 1.0

    fun acceptsQuantity(quantity: Double): Boolean {
        if (quantity <= 0.0) return false
        val steps = quantity / quantityStep
        return abs(steps - round(steps)) < QUANTITY_EPSILON
    }

    private companion object {
        const val QUANTITY_EPSILON = 1e-7
    }
}

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

/** 차트의 OHLCV 봉 하나. [startTime, endTime) 구간을 나타낸다. */
data class PriceBar(
    val stockId: String,
    val startTime: Instant,
    val endTime: Instant,
    val step: TurnStep,
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

    val interval: TurnStep get() = step
    val change: Double get() = close - open
    val changeRate: Double get() = if (open == 0.0) 0.0 else change / open
    val isRising: Boolean get() = close > open
}
