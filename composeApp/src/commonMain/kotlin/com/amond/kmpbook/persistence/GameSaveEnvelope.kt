package com.amond.kmpbook.persistence

import com.amond.kmpbook.presentation.simulator.SimulatorUiState
import kotlin.time.Instant

/** Versioned on-disk envelope. Only the exact current schema is accepted. */
internal data class GameSaveEnvelope(
    val format: String,
    val schemaVersion: Int,
    val savedAt: Instant,
    val state: SimulatorUiState,
) {
    fun metadata(): GameSaveMetadata = GameSaveMetadata(
        format = format,
        schemaVersion = schemaVersion,
        savedAt = savedAt,
        gameTime = state.currentTime,
        turn = state.turn,
    )
}
