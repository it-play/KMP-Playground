package com.amond.kmpbook.modding.api

/** 더 구체적인 수명주기 이벤트가 아닌 공개 게임 상태 변경이다. */
data class StateChanged(
    override val state: ModGameStateSummary,
) : ModGameEvent
