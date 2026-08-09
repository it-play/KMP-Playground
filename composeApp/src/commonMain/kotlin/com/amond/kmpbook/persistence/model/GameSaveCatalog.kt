package com.amond.kmpbook.persistence.model

data class GameSaveCatalog(
    val entries: List<GameSaveEntry>,
    val error: GameSaveError? = null,
)
