package com.amond.kmpbook.modding.api

import com.amond.kmpbook.modding.model.ModCapability

/** 상세 화면과 주문 화면에서 사용할 종목을 선택한다. */
data class SelectInstrumentCommand(
    val instrumentId: String,
) : ModCommand {
    override val requiredCapability: ModCapability = ModCapability.PLAYER_COMMANDS
}
