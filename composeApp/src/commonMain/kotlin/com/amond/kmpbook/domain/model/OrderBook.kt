package com.amond.kmpbook.domain.model

import kotlin.math.abs
import kotlin.time.Instant

/** 시가창의 매수·매도 잔량. bids는 고가 우선, asks는 저가 우선으로 공급한다. */
data class OrderBook(
    val stockId: String,
    val timestamp: Instant,
    val bids: List<OrderBookLevel>,
    val asks: List<OrderBookLevel>,
) {
    init {
        require(stockId.isNotBlank()) { "종목 ID는 비어 있을 수 없습니다." }
        require(bids.zipWithNext().all { (left, right) -> left.price >= right.price }) {
            "매수 호가는 높은 가격부터 정렬되어야 합니다."
        }
        require(asks.zipWithNext().all { (left, right) -> left.price <= right.price }) {
            "매도 호가는 낮은 가격부터 정렬되어야 합니다."
        }
    }

    val bestBid: OrderBookLevel? get() = bids.firstOrNull()
    val bestAsk: OrderBookLevel? get() = asks.firstOrNull()
    val spread: Double? get() = bestBid?.let { bid -> bestAsk?.price?.minus(bid.price) }
    val totalBidQuantity: Double get() = bids.sumOf { it.quantity }
    val totalAskQuantity: Double get() = asks.sumOf { it.quantity }
}
