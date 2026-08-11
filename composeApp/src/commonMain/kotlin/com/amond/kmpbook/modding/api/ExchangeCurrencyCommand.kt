package com.amond.kmpbook.modding.api

import com.amond.kmpbook.domain.model.market.Currency
import com.amond.kmpbook.modding.model.ModCapability

/** 플레이어 현금을 한 통화에서 다른 통화로 환전한다. */
data class ExchangeCurrencyCommand(
    val from: Currency,
    val to: Currency,
    val amount: Double,
) : ModCommand {
    override val requiredCapability: ModCapability = ModCapability.PLAYER_COMMANDS
}
