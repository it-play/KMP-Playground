package com.amond.kmpbook.modding.api

import com.amond.kmpbook.domain.model.game.TurnStep
import com.amond.kmpbook.modding.model.ModCapability

/** 이후 기본 진행 명령이 사용할 턴 단위를 선택한다. */
data class SelectTurnStepCommand(
    val step: TurnStep,
) : ModCommand {
    override val requiredCapability: ModCapability = ModCapability.PLAYER_COMMANDS
}
