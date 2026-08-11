package com.amond.kmpbook.modding.api

/** ViewModel이 명시적으로 발행하는 모드용 게임 수명주기·상태 이벤트다. */
sealed interface ModGameEvent {
    val state: ModGameStateSummary
}
