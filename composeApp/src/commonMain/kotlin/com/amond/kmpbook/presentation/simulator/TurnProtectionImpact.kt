package com.amond.kmpbook.presentation.simulator

import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.presentation.trading.RuntimePriceBounds
import com.amond.kmpbook.presentation.trading.RuntimeTradingInterval
import kotlin.time.Instant
import kotlinx.datetime.minus
import kotlinx.datetime.plus

internal data class TurnProtectionImpact(
    val marketBlocks: Map<Market, List<RuntimeTradingInterval>> = emptyMap(),
    val instrumentBlocks: Map<String, List<RuntimeTradingInterval>> = emptyMap(),
    val priceBounds: Map<String, RuntimePriceBounds> = emptyMap(),
    val newRestrictionStartedAt: Map<String, Instant> = emptyMap(),
    val newMarketRestrictionStartedAt: Map<Market, Instant> = emptyMap(),
    val temporaryProtectionMarkets: Set<Market> = emptySet(),
    val usMwcbControlledTurn: Boolean = false,
) {
    fun firstNewRestrictionAt(stock: StockDefinition): Instant? = listOfNotNull(
        newRestrictionStartedAt[stock.id],
        newMarketRestrictionStartedAt[stock.market],
    ).minOrNull()
}
