package com.amond.kmpbook.persistence.storage

import com.amond.kmpbook.persistence.result.GameLoadResult
import com.amond.kmpbook.persistence.result.GameSaveDeleteResult
import com.amond.kmpbook.persistence.result.GameSaveResult
import com.amond.kmpbook.persistence.model.GameSaveCatalog
import com.amond.kmpbook.presentation.simulator.SimulatorUiState

const val CURRENT_GAME_SAVE_SCHEMA_VERSION: Int = 22
const val GAME_SAVE_FORMAT_ID: String = "market-ledger-2040.game-save"

/**
 * Named manual save-game storage. Construction, [list], and [load] never create a directory or file;
 * only [save] writes, while [delete] mutates only the selected save path.
 *
 * The platform implementation owns the canonical Market Ledger 2040 save location and size limit.
 */
expect class GameSaveStorage() {
    val saveDirectory: String

    /** Opens the save directory in the platform file browser. Returns an error message on failure. */
    suspend fun openSaveDirectory(): String?

    suspend fun save(state: SimulatorUiState, name: String): GameSaveResult

    suspend fun load(fileName: String): GameLoadResult

    suspend fun list(): GameSaveCatalog

    suspend fun delete(fileName: String): GameSaveDeleteResult
}
