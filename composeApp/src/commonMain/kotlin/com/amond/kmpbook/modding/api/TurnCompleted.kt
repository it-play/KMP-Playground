package com.amond.kmpbook.modding.api

/** 한 번의 진행 명령이 완료되어 게임 턴이 증가한 순간이다. */
data class TurnCompleted(
    override val state: ModGameStateSummary,
    val previousTurn: Long,
    val turnsAdvanced: Long,
) : ModGameEvent
