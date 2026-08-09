package com.amond.kmpbook.persistence.result

data class GameSaveMissing(
    override val path: String,
) : GameSavePresenceResult
