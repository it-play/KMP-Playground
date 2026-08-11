package com.amond.kmpbook.modding.api

import com.amond.kmpbook.domain.model.game.GamePhase
import kotlin.time.Instant

/** 이벤트에 첨부되는 작고 불변인 게임 상태 요약이다. */
data class ModGameStateSummary(
    val phase: GamePhase,
    val currentTime: Instant,
    val turn: Long,
    val totalAssetsKrw: Double,
    val selectedInstrumentId: String?,
    val isAdvancing: Boolean,
)
