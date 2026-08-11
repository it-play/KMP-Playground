package com.amond.kmpbook.modding.api

import com.amond.kmpbook.modding.model.ModCapability

/** 현재 뉴스 피드의 모든 이벤트를 읽음 처리한다. */
data object MarkAllEventsReadCommand : ModCommand {
    override val requiredCapability: ModCapability = ModCapability.PLAYER_COMMANDS
}
