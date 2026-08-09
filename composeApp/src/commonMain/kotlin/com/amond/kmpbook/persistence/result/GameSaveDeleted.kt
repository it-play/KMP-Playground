package com.amond.kmpbook.persistence.result

data class GameSaveDeleted(
    override val path: String,
) : GameSaveDeleteResult
