package com.amond.kmpbook.domain.simulation

import com.amond.kmpbook.domain.model.MarketSession
import com.amond.kmpbook.domain.model.MarketVenueProfiles
import com.amond.kmpbook.domain.model.OrderBook
import com.amond.kmpbook.domain.model.OrderBookLevel
import com.amond.kmpbook.domain.model.Quote
import com.amond.kmpbook.domain.model.StockDefinition
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.time.Instant

data class OrderBookDepthLevel(
    val price: Double,
    val quantity: Double,
    val cumulativeQuantity: Double,
    val orderCount: Int,
) {
    init {
        require(price > 0.0 && price.isFinite()) { "Order price must be positive and finite" }
        require(quantity > 0.0 && quantity.isFinite()) { "Order quantity must be positive and finite" }
        require(cumulativeQuantity >= quantity && cumulativeQuantity.isFinite())
        require(orderCount > 0) { "Order count must be positive" }
    }
}
