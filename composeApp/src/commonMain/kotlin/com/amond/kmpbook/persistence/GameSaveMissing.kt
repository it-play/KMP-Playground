package com.amond.kmpbook.persistence

data class GameSaveMissing(
    override val path: String,
) : GameSavePresenceResult
