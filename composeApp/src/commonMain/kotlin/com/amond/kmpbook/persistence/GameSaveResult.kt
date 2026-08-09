package com.amond.kmpbook.persistence

sealed interface GameSaveResult {
    val path: String
}
