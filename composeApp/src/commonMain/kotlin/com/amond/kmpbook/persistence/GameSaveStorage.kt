package com.amond.kmpbook.persistence

import com.amond.kmpbook.presentation.SimulatorUiState
import kotlin.time.Instant

const val CURRENT_GAME_SAVE_SCHEMA_VERSION: Int = 3
const val GAME_SAVE_FORMAT_ID: String = "market-ledger-2040.game-save"

data class GameSaveMetadata(
    val format: String,
    val schemaVersion: Int,
    val savedAt: Instant,
    val gameTime: Instant,
    val turn: Long,
)

enum class GameSaveErrorCode(val displayName: String) {
    NOT_FOUND("저장 파일 없음"),
    FILE_TOO_LARGE("저장 파일 크기 초과"),
    CORRUPTED_FILE("손상된 저장 파일"),
    UNSUPPORTED_SCHEMA("지원하지 않는 저장 스키마"),
    INVALID_STATE("유효하지 않은 게임 상태"),
    SERIALIZATION_FAILED("저장 데이터 직렬화 실패"),
    IO_ERROR("파일 입출력 실패"),
    SECURITY_ERROR("파일 접근 권한 오류"),
}

data class GameSaveError(
    val code: GameSaveErrorCode,
    val message: String,
    val causeType: String? = null,
) {
    init {
        require(message.isNotBlank()) { "A save error needs an explanatory message." }
    }
}

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
