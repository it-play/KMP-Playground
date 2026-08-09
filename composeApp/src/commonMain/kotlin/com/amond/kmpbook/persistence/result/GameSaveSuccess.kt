package com.amond.kmpbook.persistence.result

import com.amond.kmpbook.persistence.model.GameSaveMetadata

data class GameSaveSuccess(
    override val path: String,
    val metadata: GameSaveMetadata,
    val bytesWritten: Long,
    /** False means the filesystem did not support ATOMIC_MOVE and replace fallback succeeded. */
    val usedAtomicMove: Boolean,
) : GameSaveResult
