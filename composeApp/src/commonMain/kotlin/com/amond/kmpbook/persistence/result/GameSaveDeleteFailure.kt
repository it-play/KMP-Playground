package com.amond.kmpbook.persistence.result

import com.amond.kmpbook.persistence.model.GameSaveError

data class GameSaveDeleteFailure(
    override val path: String,
    val error: GameSaveError,
) : GameSaveDeleteResult
