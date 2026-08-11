package com.amond.kmpbook.modding.api

import com.amond.kmpbook.modding.model.ModCapability

/** 특정 종목의 뉴스 목록에서 확인한 이벤트들을 읽음 처리한다. */
data class MarkInstrumentEventsReadCommand(
    val instrumentId: String,
    val eventIds: Set<String>,
) : ModCommand {
    override val requiredCapability: ModCapability = ModCapability.PLAYER_COMMANDS
}
