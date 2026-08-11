package com.amond.kmpbook.modding.api

import com.amond.kmpbook.modding.model.ModCapability

/** 일시 정지된 게임을 다시 진행 상태로 전환한다. */
data object ResumeGameCommand : ModCommand {
    override val requiredCapability: ModCapability = ModCapability.PLAYER_COMMANDS
}
