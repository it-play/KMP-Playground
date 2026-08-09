package com.amond.kmpbook.domain.simulation.order

import com.amond.kmpbook.domain.model.trading.Order

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
