package com.amond.kmpbook.persistence.storage

import com.amond.kmpbook.persistence.result.GameLoadResult
import com.amond.kmpbook.persistence.result.GameSaveDeleteResult
import com.amond.kmpbook.persistence.result.GameSaveResult
import com.amond.kmpbook.persistence.model.GameSaveCatalog
import com.amond.kmpbook.presentation.simulator.SimulatorUiState

const val CURRENT_GAME_SAVE_SCHEMA_VERSION: Int = 50
const val GAME_SAVE_FORMAT_ID: String = "market-ledger-2040.game-save"

/**
 * Named manual save-game storage. Only [save] and [delete] mutate persistent save data; loading may
 * use short-lived private temporary files while validating an untrusted payload.
 *
 * The platform implementation owns the canonical Market Ledger 2040 save location and size limit.
 */
expect class GameSaveStorage() {
    val saveDirectory: String

    /** Opens the save directory in the platform file browser. Returns an error message on failure. */
    suspend fun openSaveDirectory(): String?

    /** Opens the game data root containing saves, mods, and resources. */
    suspend fun openGameDirectory(): String?

    suspend fun save(state: SimulatorUiState, name: String): GameSaveResult

    suspend fun load(fileName: String): GameLoadResult

    suspend fun list(): GameSaveCatalog

    suspend fun delete(fileName: String): GameSaveDeleteResult
}
