package com.amond.kmpbook.domain.model.protection.us

import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.protection.us.UsMwcbVenuePhase
import com.amond.kmpbook.domain.model.protection.us.UsMwcbVenueStatus
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
