package com.amond.kmpbook.domain.simulation.order

import com.amond.kmpbook.domain.model.pricing.Quote
import com.amond.kmpbook.domain.model.trading.Order
import com.amond.kmpbook.domain.model.trading.OrderBook
import com.amond.kmpbook.domain.model.trading.OrderBookLevel
import com.amond.kmpbook.domain.model.venue.MarketSession
import kotlin.time.Instant

data class OrderBookSnapshot(
    val stockId: String,
    val timestamp: Instant,
    val session: MarketSession,
    val lastPrice: Double,
    val bids: List<OrderBookDepthLevel>,
    val asks: List<OrderBookDepthLevel>,
) {
    init {
        require(stockId.isNotBlank())
        require(lastPrice > 0.0 && lastPrice.isFinite())
        require(bids.size == asks.size) { "Bid and ask depth must match" }
        require(bids.size == 10 || bids.isEmpty()) { "An executable book must contain 10 levels" }
        require(bids.zipWithNext().all { (near, far) -> near.price > far.price }) {
            "Bid prices must strictly decrease away from the market"
        }
        require(asks.zipWithNext().all { (near, far) -> near.price < far.price }) {
            "Ask prices must strictly increase away from the market"
        }
        if (bids.isNotEmpty()) {
            require(bids.first().price < asks.first().price) { "Order book cannot be crossed" }
        }
    }

    val bestBid: OrderBookDepthLevel? get() = bids.firstOrNull()
    val bestAsk: OrderBookDepthLevel? get() = asks.firstOrNull()
    val spread: Double? get() = if (bestBid != null && bestAsk != null) bestAsk!!.price - bestBid!!.price else null
    val midpoint: Double? get() = if (bestBid != null && bestAsk != null) (bestBid!!.price + bestAsk!!.price) / 2.0 else null
    val totalBidQuantity: Double get() = bids.lastOrNull()?.cumulativeQuantity ?: 0.0
    val totalAskQuantity: Double get() = asks.lastOrNull()?.cumulativeQuantity ?: 0.0
    val imbalance: Double
        get() {
            val total = totalBidQuantity + totalAskQuantity
            return if (total == 0.0) 0.0 else (totalBidQuantity - totalAskQuantity) / total
        }
    val microPrice: Double?
        get() {
            val bid = bestBid ?: return null
            val ask = bestAsk ?: return null
            val topQuantity = bid.quantity + ask.quantity
            return if (topQuantity == 0.0) midpoint else {
                (ask.price * bid.quantity + bid.price * ask.quantity) / topQuantity
            }
        }

    fun applyTopOfBook(quote: Quote): Quote {
        require(quote.stockId == stockId) { "Quote and order book stock ids must match" }
        return quote.copy(
            bidPrice = bestBid?.price,
            askPrice = bestAsk?.price,
            bidQuantity = bestBid?.quantity ?: 0.0,
            askQuantity = bestAsk?.quantity ?: 0.0,
        )
    }

    /** Lossless executable-depth view for the shared domain order model. */
    fun toOrderBook(): OrderBook = OrderBook(
        stockId = stockId,
        timestamp = timestamp,
        bids = bids.map { OrderBookLevel(it.price, it.quantity, it.orderCount) },
        asks = asks.map { OrderBookLevel(it.price, it.quantity, it.orderCount) },
    )
}
