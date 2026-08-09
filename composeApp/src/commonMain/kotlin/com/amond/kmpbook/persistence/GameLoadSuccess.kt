package com.amond.kmpbook.persistence

import com.amond.kmpbook.presentation.simulator.SimulatorUiState

data class GameLoadSuccess(
    override val path: String,
    val state: SimulatorUiState,
    val metadata: GameSaveMetadata,
    val bytesRead: Long,
) : GameLoadResult
