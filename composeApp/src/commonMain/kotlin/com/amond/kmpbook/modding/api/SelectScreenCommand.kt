package com.amond.kmpbook.modding.api

import com.amond.kmpbook.domain.model.game.Screen
import com.amond.kmpbook.modding.model.ModCapability

/** 현재 로비·게임 화면을 선택한다. */
data class SelectScreenCommand(
    val screen: Screen,
) : ModCommand {
    override val requiredCapability: ModCapability = ModCapability.PLAYER_COMMANDS
}
