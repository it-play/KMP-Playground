package com.amond.kmpbook.persistence.result

import com.amond.kmpbook.persistence.model.GameSaveMetadata
import com.amond.kmpbook.presentation.simulator.SimulatorUiState

data class GameLoadSuccess(
    override val path: String,
    val state: SimulatorUiState,
    val metadata: GameSaveMetadata,
    val bytesRead: Long,
) : GameLoadResult
