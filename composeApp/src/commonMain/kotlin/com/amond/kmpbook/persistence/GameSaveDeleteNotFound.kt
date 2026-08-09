package com.amond.kmpbook.persistence

data class GameSaveDeleteNotFound(
    override val path: String,
) : GameSaveDeleteResult
