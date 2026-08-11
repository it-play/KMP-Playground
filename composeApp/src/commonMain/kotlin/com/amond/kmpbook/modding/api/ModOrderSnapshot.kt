package com.amond.kmpbook.modding.api

import com.amond.kmpbook.domain.model.trading.OrderSide
import com.amond.kmpbook.domain.model.trading.OrderStatus
import com.amond.kmpbook.domain.model.trading.OrderType
import com.amond.kmpbook.domain.model.trading.TimeInForce
import kotlin.time.Instant

/** 주문 접수부터 체결·취소까지의 현재 상태다. */
data class ModOrderSnapshot(
    val id: String,
    val instrumentId: String,
    val side: OrderSide,
    val type: OrderType,
    val quantity: Double,
    val limitPrice: Double?,
    val status: OrderStatus,
    val filledQuantity: Double,
    val remainingQuantity: Double,
    val averageFilledPrice: Double?,
    val timeInForce: TimeInForce,
    val createdAt: Instant,
    val updatedAt: Instant,
    val rejectionReason: String?,
)
