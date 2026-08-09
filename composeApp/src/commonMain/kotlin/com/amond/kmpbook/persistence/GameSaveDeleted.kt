package com.amond.kmpbook.persistence

data class GameSaveDeleted(
    override val path: String,
) : GameSaveDeleteResult
