package com.amond.kmpbook.persistence.result

data class GameSaveDeleteNotFound(
    override val path: String,
) : GameSaveDeleteResult
