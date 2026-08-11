package com.amond.kmpbook.modding.api

/** 준비 상태에서 실제 플레이가 시작되거나 새 캠페인으로 되감긴 순간이다. */
data class GameStarted(
    override val state: ModGameStateSummary,
    val scenarioName: String,
) : ModGameEvent
