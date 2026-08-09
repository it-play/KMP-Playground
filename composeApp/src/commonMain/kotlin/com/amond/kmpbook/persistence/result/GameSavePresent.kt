package com.amond.kmpbook.persistence.result

import kotlin.time.Instant

data class GameSavePresent(
    override val path: String,
    val sizeBytes: Long,
    val lastModifiedAt: Instant,
) : GameSavePresenceResult
