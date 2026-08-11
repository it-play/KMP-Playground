package com.amond.kmpbook.modding.api

import com.amond.kmpbook.modding.model.ModCapability

/** 취소 가능한 미체결 주문을 취소한다. */
data class CancelOrderCommand(
    val orderId: String,
) : ModCommand {
    override val requiredCapability: ModCapability = ModCapability.PLAYER_COMMANDS
}
