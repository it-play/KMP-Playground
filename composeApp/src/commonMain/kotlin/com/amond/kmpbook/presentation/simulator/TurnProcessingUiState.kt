package com.amond.kmpbook.presentation.simulator

import com.amond.kmpbook.domain.model.game.TurnStep
import kotlin.time.Instant

/** Lightweight, throttled progress for the currently running market-clock command. */
data class TurnProcessingUiState(
    val step: TurnStep,
    val startedAt: Instant,
    val targetTime: Instant,
    val currentTime: Instant,
    val completedHours: Int,
    val totalHours: Int,
    val stage: String,
    val latestActivity: String,
    val recentEventTitle: String? = null,
    val marketSessionSummary: String,
    val cancellationRequested: Boolean = false,
) {
    init {
        require(totalHours > 0) { "The processing total must be positive" }
        require(completedHours in 0..totalHours) { "Completed hours must be within the processing range" }
    }

    val progress: Float
        get() = (completedHours.toFloat() / totalHours).coerceIn(0f, 1f)

    val remainingHours: Int
        get() = (totalHours - completedHours).coerceAtLeast(0)
}
