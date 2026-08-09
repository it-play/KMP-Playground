package com.amond.kmpbook.domain.model.protection.core

import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.protection.core.ProgramOrderSide
import com.amond.kmpbook.domain.model.protection.core.TradingProtectionAction
import com.amond.kmpbook.domain.model.protection.core.TradingProtectionRequest

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
