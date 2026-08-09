package com.amond.kmpbook.persistence

data class GameLoadFailure(
    override val path: String,
    val error: GameSaveError,
) : GameLoadResult
