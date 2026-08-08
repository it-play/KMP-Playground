package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

data class TradingProtectionRequest(
    val market: Market,
    val action: TradingProtectionAction,
    val stockId: String? = null,
    val isProgramOrder: Boolean = false,
    val programOrderSide: ProgramOrderSide? = null,
    val proposedExecutionPrice: Double? = null,
    /** Auction-eligible orders may be accepted while continuous matching is paused. */
    val isAuctionEligibleOrder: Boolean = false,
) {
    init {
        require(stockId == null || stockId.isNotBlank())
        require(!isProgramOrder || programOrderSide != null)
        require(proposedExecutionPrice == null || proposedExecutionPrice > 0.0)
    }
}
