package com.amond.kmpbook.presentation.trading

import com.amond.kmpbook.domain.model.trading.OrderSide
import com.amond.kmpbook.domain.model.trading.OrderType
import com.amond.kmpbook.domain.model.trading.TimeInForce

data class OrderRequest(
    val stockId: String,
    val side: OrderSide,
    val type: OrderType,
    val quantity: Double,
    val limitPrice: Double? = null,
    val timeInForce: TimeInForce = TimeInForce.DAY,
)
