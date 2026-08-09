package com.amond.kmpbook.persistence

sealed interface GameSaveDeleteResult {
    val path: String
}
