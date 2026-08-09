package com.amond.kmpbook.domain.model.protection.us

import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.protection.us.UsMwcbLevel
import com.amond.kmpbook.domain.model.protection.us.UsMwcbPhase
import com.amond.kmpbook.domain.model.protection.us.UsMwcbState
import com.amond.kmpbook.domain.model.protection.us.UsMwcbVenueStatus
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

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
