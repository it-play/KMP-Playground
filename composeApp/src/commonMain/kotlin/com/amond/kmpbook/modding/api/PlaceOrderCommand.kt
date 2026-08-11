package com.amond.kmpbook.modding.api

import com.amond.kmpbook.domain.model.trading.OrderSide
import com.amond.kmpbook.domain.model.trading.OrderType
import com.amond.kmpbook.domain.model.trading.TimeInForce
import com.amond.kmpbook.modding.model.ModCapability

/** 플레이어 계좌로 신규 주문을 제출한다. */
data class PlaceOrderCommand(
    val instrumentId: String,
    val side: OrderSide,
    val type: OrderType,
    val quantity: Double,
    val limitPrice: Double? = null,
    val timeInForce: TimeInForce = TimeInForce.DAY,
) : ModCommand {
    override val requiredCapability: ModCapability = ModCapability.PLAYER_COMMANDS
}
