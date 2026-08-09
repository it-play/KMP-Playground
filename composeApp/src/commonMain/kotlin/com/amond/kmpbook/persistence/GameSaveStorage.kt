package com.amond.kmpbook.persistence

import com.amond.kmpbook.presentation.SimulatorUiState
import kotlin.time.Instant

const val CURRENT_GAME_SAVE_SCHEMA_VERSION: Int = 17
const val GAME_SAVE_FORMAT_ID: String = "market-ledger-2040.game-save"

// These result variants stay with the expect storage declaration because together they form one
// multiplatform save protocol; splitting them would scatter a single public boundary across files.
sealed interface GameSaveResult {
    val path: String

    data class Success(
        override val path: String,
        val metadata: GameSaveMetadata,
        val bytesWritten: Long,
        /** False means the filesystem did not support ATOMIC_MOVE and replace fallback succeeded. */
        val usedAtomicMove: Boolean,
    ) : GameSaveResult

    data class Failure(
        override val path: String,
        val error: GameSaveError,
    ) : GameSaveResult
}

sealed interface GameLoadResult {
    val path: String

    data class Success(
        override val path: String,
        val state: SimulatorUiState,
        val metadata: GameSaveMetadata,
        val bytesRead: Long,
    ) : GameLoadResult

    data class NotFound(
        override val path: String,
        val message: String = "저장된 게임이 없습니다.",
    ) : GameLoadResult

    data class Failure(
        override val path: String,
        val error: GameSaveError,
    ) : GameLoadResult
}

sealed interface GameSavePresenceResult {
    val path: String

    data class Present(
        override val path: String,
        val sizeBytes: Long,
        val lastModifiedAt: Instant,
    ) : GameSavePresenceResult

    data class Missing(
        override val path: String,
    ) : GameSavePresenceResult

    data class Failure(
        override val path: String,
        val error: GameSaveError,
    ) : GameSavePresenceResult
}

sealed interface GameSaveDeleteResult {
    val path: String

    data class Deleted(
        override val path: String,
    ) : GameSaveDeleteResult

    data class NotFound(
        override val path: String,
    ) : GameSaveDeleteResult

    data class Failure(
        override val path: String,
        val error: GameSaveError,
    ) : GameSaveDeleteResult
}

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
