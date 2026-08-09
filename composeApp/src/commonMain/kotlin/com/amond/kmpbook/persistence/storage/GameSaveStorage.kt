package com.amond.kmpbook.persistence.storage

import com.amond.kmpbook.persistence.result.GameLoadResult
import com.amond.kmpbook.persistence.result.GameSaveDeleteResult
import com.amond.kmpbook.persistence.result.GameSavePresenceResult
import com.amond.kmpbook.persistence.result.GameSaveResult
import com.amond.kmpbook.presentation.simulator.SimulatorUiState

const val CURRENT_GAME_SAVE_SCHEMA_VERSION: Int = 19
const val GAME_SAVE_FORMAT_ID: String = "market-ledger-2040.game-save"

/**
 * Manual save-game storage. Construction, [exists], and [load] never create a directory or file;
 * only [save] writes, while [delete] mutates only the resolved single save path.
 *
 * The platform implementation owns the canonical Market Ledger 2040 save location and size limit.
 */
expect class GameSaveStorage() {
    val savePath: String

    suspend fun save(state: SimulatorUiState): GameSaveResult

    suspend fun load(): GameLoadResult

    suspend fun exists(): GameSavePresenceResult

    suspend fun delete(): GameSaveDeleteResult
}
