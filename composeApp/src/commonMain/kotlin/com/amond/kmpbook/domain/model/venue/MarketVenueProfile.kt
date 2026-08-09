package com.amond.kmpbook.domain.model.venue

import com.amond.kmpbook.domain.model.market.Market
import kotlinx.datetime.LocalTime

/**
 * Stable game parameters for a primary listing venue.
 *
 * [spreadMultiplier] and [depthMultiplier] are explicit simulation assumptions, not
 * exchange-published constants. Security size, volatility, volume and stress remain the
 * primary inputs; these multipliers add a smaller venue-level tendency. Session times are
 * exchange-local (America/New_York for U.S. venues).
 */
data class MarketVenueProfile(
    val market: Market,
    val marketModel: MarketVenueModel,
    val spreadMultiplier: Double,
    val depthMultiplier: Double,
    val preMarketOpensAt: LocalTime?,
    val afterHoursClosesAt: LocalTime?,
    val auctionDescription: String,
    val liquidityDescription: String,
    val gameAssumption: String,
) {
    init {
        require(spreadMultiplier > 0.0 && spreadMultiplier.isFinite())
        require(depthMultiplier > 0.0 && depthMultiplier.isFinite())
        require(auctionDescription.isNotBlank())
        require(liquidityDescription.isNotBlank())
        require(gameAssumption.isNotBlank())
    }
}
