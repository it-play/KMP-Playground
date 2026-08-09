package com.amond.kmpbook.persistence.model

data class GameSaveEntry(
    val name: String,
    val fileName: String,
    val path: String,
    val sizeBytes: Long,
    val metadata: GameSaveMetadata,
)
