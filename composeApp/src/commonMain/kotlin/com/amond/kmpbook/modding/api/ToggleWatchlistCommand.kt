package com.amond.kmpbook.modding.api

import com.amond.kmpbook.modding.model.ModCapability

/** 종목의 관심 목록 포함 여부를 반전한다. */
data class ToggleWatchlistCommand(
    val instrumentId: String,
) : ModCommand {
    override val requiredCapability: ModCapability = ModCapability.PLAYER_COMMANDS
}
