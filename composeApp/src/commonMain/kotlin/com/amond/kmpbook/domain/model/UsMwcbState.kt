package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

data class UsMwcbState(
    val tradingDate: LocalDate,
    val phase: UsMwcbPhase = UsMwcbPhase.NORMAL,
    val triggeredLevels: Set<UsMwcbLevel> = emptySet(),
    val activeLevel: UsMwcbLevel? = null,
    val triggeredAt: Instant? = null,
    val haltEndsAt: Instant? = null,
    /** Per-primary-listing-market reopening metadata; auctions do not all finish simultaneously. */
    val venueStatuses: Map<Market, UsMwcbVenueStatus> = emptyMap(),
) {
    init {
        require(venueStatuses.keys.all { it.isUnitedStates })
        require(venueStatuses.all { (market, status) -> market == status.market })
        when (phase) {
            UsMwcbPhase.NORMAL -> Unit
            UsMwcbPhase.HALTED -> require(activeLevel != null && triggeredAt != null && haltEndsAt != null)
            UsMwcbPhase.REOPENING_AUCTIONS -> require(activeLevel == UsMwcbLevel.LEVEL_1 || activeLevel == UsMwcbLevel.LEVEL_2)
            UsMwcbPhase.CLOSED_FOR_DAY -> require(activeLevel == UsMwcbLevel.LEVEL_3)
        }
    }
}
