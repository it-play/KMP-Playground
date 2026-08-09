package com.amond.kmpbook.presentation.simulator

import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.presentation.trading.RuntimePriceBounds
import com.amond.kmpbook.presentation.trading.RuntimeTradingInterval
import kotlin.time.Instant
import kotlinx.datetime.minus
import kotlinx.datetime.plus

internal class TurnProtectionImpactBuilder {
    val marketBlocks = mutableMapOf<Market, MutableList<RuntimeTradingInterval>>()
    val instrumentBlocks = mutableMapOf<String, MutableList<RuntimeTradingInterval>>()
    val priceBounds = mutableMapOf<String, RuntimePriceBounds>()
    val newRestrictionStartedAt = mutableMapOf<String, Instant>()
    val newMarketRestrictionStartedAt = mutableMapOf<Market, Instant>()
    val temporaryProtectionMarkets = mutableSetOf<Market>()
    var usMwcbControlledTurn: Boolean = false

    fun addMarketBlock(market: Market, block: RuntimeTradingInterval, temporary: Boolean) {
        marketBlocks.getOrPut(market) { mutableListOf() } += block
        newMarketRestrictionStartedAt[market] = minOf(
            newMarketRestrictionStartedAt[market] ?: block.startsAt,
            block.startsAt,
        )
        if (temporary) temporaryProtectionMarkets += market
    }

    fun addInstrumentBlock(stockId: String, block: RuntimeTradingInterval) {
        instrumentBlocks.getOrPut(stockId) { mutableListOf() } += block
        newRestrictionStartedAt[stockId] = minOf(
            newRestrictionStartedAt[stockId] ?: block.startsAt,
            block.startsAt,
        )
    }

    fun mergePriceBounds(stockId: String, bounds: RuntimePriceBounds) {
        priceBounds[stockId] = priceBounds[stockId]?.merge(bounds) ?: bounds
    }

    fun build(): TurnProtectionImpact = TurnProtectionImpact(
        marketBlocks = marketBlocks.mapValues { it.value.toList() },
        instrumentBlocks = instrumentBlocks.mapValues { it.value.toList() },
        priceBounds = priceBounds.toMap(),
        newRestrictionStartedAt = newRestrictionStartedAt.toMap(),
        newMarketRestrictionStartedAt = newMarketRestrictionStartedAt.toMap(),
        temporaryProtectionMarkets = temporaryProtectionMarkets.toSet(),
        usMwcbControlledTurn = usMwcbControlledTurn,
    )
}
