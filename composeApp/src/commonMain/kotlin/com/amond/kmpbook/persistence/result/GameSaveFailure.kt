package com.amond.kmpbook.persistence.result

import com.amond.kmpbook.persistence.model.GameSaveError

data class GameSaveFailure(
    override val path: String,
    val error: GameSaveError,
) : GameSaveResult
