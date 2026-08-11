package com.amond.kmpbook.modding.api

import com.amond.kmpbook.modding.model.ModCapability

/** 주문 자금 부족 시 자동 환전 여부를 설정한다. */
data class SetAutoExchangeCommand(
    val enabled: Boolean,
) : ModCommand {
    override val requiredCapability: ModCapability = ModCapability.PLAYER_COMMANDS
}
