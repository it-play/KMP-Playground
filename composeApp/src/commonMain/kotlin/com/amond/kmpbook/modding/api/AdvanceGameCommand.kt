package com.amond.kmpbook.modding.api

import com.amond.kmpbook.domain.model.game.TurnStep
import com.amond.kmpbook.modding.model.ModCapability

/** 지정한 단위 또는 현재 선택 단위만큼 백그라운드 진행을 시작한다. */
data class AdvanceGameCommand(
    val step: TurnStep? = null,
) : ModCommand {
    override val requiredCapability: ModCapability = ModCapability.PLAYER_COMMANDS
}
