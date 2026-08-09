package com.amond.kmpbook.persistence

data class GameSaveDeleteFailure(
    override val path: String,
    val error: GameSaveError,
) : GameSaveDeleteResult
