package com.amond.kmpbook.persistence.model

import kotlin.time.Instant

data class GameSaveMetadata(
    val format: String,
    val schemaVersion: Int,
    val savedAt: Instant,
    val gameTime: Instant,
    val turn: Long,
)
