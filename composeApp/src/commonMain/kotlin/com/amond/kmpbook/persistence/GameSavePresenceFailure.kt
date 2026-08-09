package com.amond.kmpbook.persistence

data class GameSavePresenceFailure(
    override val path: String,
    val error: GameSaveError,
) : GameSavePresenceResult
