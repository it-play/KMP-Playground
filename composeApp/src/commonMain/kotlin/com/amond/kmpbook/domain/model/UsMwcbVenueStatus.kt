package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

data class UsMwcbVenueStatus(
    val market: Market,
    val phase: UsMwcbVenuePhase,
    val phaseStartedAt: Instant,
) {
    init {
        require(market.isUnitedStates)
    }
}
