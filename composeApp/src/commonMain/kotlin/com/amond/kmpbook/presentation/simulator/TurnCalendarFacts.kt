package com.amond.kmpbook.presentation.simulator

import com.amond.kmpbook.domain.model.market.Market
import kotlinx.datetime.LocalDate

/** Calendar observations that are invariant across both pricing passes of one simulated hour. */
internal data class TurnCalendarFacts(
    val marketDatesAtStart: Map<Market, LocalDate>,
    val marketDatesAtEnd: Map<Market, LocalDate>,
    val marketCloseReached: Map<Market, Boolean>,
)
