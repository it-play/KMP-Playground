package com.amond.kmpbook.modding.api

import com.amond.kmpbook.modding.model.ModCapability

/** 최종 정산 화면을 완료하고 게임을 종료한다. */
data object FinishSettlementCommand : ModCommand {
    override val requiredCapability: ModCapability = ModCapability.PLAYER_COMMANDS
}
