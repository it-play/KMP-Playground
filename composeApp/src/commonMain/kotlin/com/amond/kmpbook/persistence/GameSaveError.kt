package com.amond.kmpbook.persistence

import com.amond.kmpbook.presentation.SimulatorUiState
import kotlin.time.Instant

data class GameSaveError(
    val code: GameSaveErrorCode,
    val message: String,
    val causeType: String? = null,
) {
    init {
        require(message.isNotBlank()) { "A save error needs an explanatory message." }
    }
}
