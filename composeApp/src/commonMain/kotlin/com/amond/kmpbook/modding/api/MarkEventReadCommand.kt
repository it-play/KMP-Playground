package com.amond.kmpbook.modding.api

import com.amond.kmpbook.modding.model.ModCapability

/** 뉴스·이벤트 한 건을 읽음 처리한다. */
data class MarkEventReadCommand(
    val eventId: String,
) : ModCommand {
    override val requiredCapability: ModCapability = ModCapability.PLAYER_COMMANDS
}
