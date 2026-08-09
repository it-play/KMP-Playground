package com.amond.kmpbook.persistence

data class GameSaveFailure(
    override val path: String,
    val error: GameSaveError,
) : GameSaveResult
