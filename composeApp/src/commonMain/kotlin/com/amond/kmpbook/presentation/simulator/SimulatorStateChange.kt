package com.amond.kmpbook.presentation.simulator

import com.amond.kmpbook.domain.model.game.GamePhase
import kotlin.time.Instant

internal data class SimulatorStateChange(
    val sequence: Long,
    val kind: SimulatorStateChangeKind,
    val phase: GamePhase,
    val currentTime: Instant,
    val turn: Long,
    val totalAssetsKrw: Double,
    val selectedInstrumentId: String?,
    val isAdvancing: Boolean,
    val scenarioName: String,
    val previousTurn: Long? = null,
)
