package com.amond.kmpbook.modding.api

/** 최종 정산이 끝나 게임이 종료 상태로 바뀐 순간이다. */
data class GameEnded(
    override val state: ModGameStateSummary,
    val finalAssetsKrw: Double,
) : ModGameEvent
