package com.amond.kmpbook.modding.api

import com.amond.kmpbook.modding.model.ModCapability

/** 진행 중인 게임을 일시 정지한다. */
data object PauseGameCommand : ModCommand {
    override val requiredCapability: ModCapability = ModCapability.PLAYER_COMMANDS
}
