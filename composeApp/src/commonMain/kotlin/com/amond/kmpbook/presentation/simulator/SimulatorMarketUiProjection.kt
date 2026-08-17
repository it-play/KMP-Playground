package com.amond.kmpbook.presentation.simulator

import com.amond.kmpbook.domain.model.event.GameEvent
import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingLifecycleState
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.pricing.Quote
import com.amond.kmpbook.domain.model.protection.core.TradingProtectionAction
import com.amond.kmpbook.domain.model.protection.core.TradingProtectionRequest
import com.amond.kmpbook.domain.model.protection.core.TradingProtectionSnapshot
import com.amond.kmpbook.domain.model.protection.krx.KrxCircuitBreakerPhase
import com.amond.kmpbook.domain.model.protection.us.UsMwcbPhase
import com.amond.kmpbook.domain.model.protection.us.UsMwcbVenuePhase
import com.amond.kmpbook.domain.model.venue.MarketSession
import com.amond.kmpbook.domain.simulation.event.EventShockCalculator
import com.amond.kmpbook.domain.simulation.market.MacroEnvironment
import com.amond.kmpbook.domain.simulation.order.OrderBookEngine
import com.amond.kmpbook.domain.simulation.order.OrderBookGenerationInput
import com.amond.kmpbook.domain.simulation.order.OrderBookSnapshot
import com.amond.kmpbook.domain.simulation.price.DeterministicRandom
import com.amond.kmpbook.domain.simulation.protection.TradingProtectionEngine
import com.amond.kmpbook.domain.time.GameCalendar
import kotlin.math.ln
import kotlin.time.Instant

/** Canonical, persistence-verifiable projection of the calendar sessions and selected quote book. */
internal data class SimulatorMarketUiProjection(
    val quotes: Map<String, Quote>,
    val marketSessions: Map<Market, MarketSession>,
    val selectedOrderBook: OrderBookSnapshot?,
)

internal fun canonicalSimulatorMarketSession(
    market: Market,
    at: Instant,
    protection: TradingProtectionSnapshot,
): MarketSession {
    val calendarSession = GameCalendar.marketSession(market, at)
    val protected = if (market.isKorean) {
        protection.krxCircuitBreakers[market]?.phase
            ?.let { phase -> phase != KrxCircuitBreakerPhase.NORMAL } == true
    } else {
        protection.usMarketWideCircuitBreaker?.let { state ->
            state.tradingDate == GameCalendar.marketLocalDateTime(market, at).date &&
                state.phase != UsMwcbPhase.NORMAL &&
                state.venueStatuses[market]?.phase != UsMwcbVenuePhase.REOPENED
        } == true
    }
    return if (protected) MarketSession.CLOSED else calendarSession
}

internal fun canonicalSimulatorOrderBook(
    campaignSeed: Long,
    stock: StockDefinition,
    quote: Quote,
    session: MarketSession,
    macro: MacroEnvironment,
    activeEvents: Collection<GameEvent>,
    at: Instant,
): OrderBookSnapshot {
    val liquidity = EventShockCalculator.liquidityMultiplierAt(
        events = activeEvents.sortedBy(GameEvent::id),
        stock = stock,
        time = at,
    )
    val eventLiquidityStress = (-ln(liquidity) / ln(5.0)).coerceIn(-1.0, 1.0)
    val combinedLiquidityStress = if (eventLiquidityStress >= 0.0) {
        1.0 - (1.0 - macro.liquidityStress) * (1.0 - eventLiquidityStress)
    } else {
        macro.liquidityStress * (1.0 + eventLiquidityStress)
    }.coerceIn(0.0, 1.0)
    return OrderBookEngine(
        DeterministicRandom.mixSeed(campaignSeed, SimulatorRuntime.BOOK_STREAM_ID),
    ).generate(
        OrderBookGenerationInput(
            stock = stock,
            timestamp = at,
            lastPrice = quote.price,
            dailyBasePrice = quote.previousClose,
            session = session,
            buyPressure = (
                macro.retailOrderFlow * 0.42 + macro.institutionalOrderFlow * 0.58
                ).coerceIn(-1.0, 1.0),
            marketStress = (
                combinedLiquidityStress * 0.68 +
                    ((macro.volatilityRegime - 1.0) / 3.0).coerceAtLeast(0.0) * 0.32
                ).coerceIn(0.0, 1.0),
        ),
    )
}

internal fun projectSimulatorMarketUi(
    campaignSeed: Long,
    currentTime: Instant,
    selectedStockId: String?,
    stocksById: Map<String, StockDefinition>,
    quotes: Map<String, Quote>,
    listingLifecycleStates: Map<String, ListingLifecycleState>,
    protection: TradingProtectionSnapshot,
    macro: MacroEnvironment,
    activeEvents: Collection<GameEvent>,
): SimulatorMarketUiProjection {
    val sessions = Market.entries.associateWith { market ->
        canonicalSimulatorMarketSession(market, currentTime, protection)
    }
    // Top-of-book fields are a derived view. Clearing them first prevents a restored selection's
    // former book from leaking into a different selected instrument on the next snapshot.
    val projectedQuotes = quotes.mapValuesTo(linkedMapOf()) { (stockId, quote) ->
        val stock = stocksById.getValue(stockId)
        quote.copy(
            timestamp = currentTime,
            bidPrice = null,
            askPrice = null,
            bidQuantity = 0.0,
            askQuantity = 0.0,
            session = if (listingLifecycleStates.getValue(stockId).isTradable) {
                sessions.getValue(stock.market)
            } else {
                MarketSession.CLOSED
            },
        )
    }
    val selectedBook = selectedStockId?.let { stockId ->
        val stock = stocksById.getValue(stockId)
        val listing = listingLifecycleStates.getValue(stockId)
        val permission = TradingProtectionEngine.permission(
            snapshot = protection,
            request = TradingProtectionRequest(
                market = stock.market,
                action = TradingProtectionAction.CONTINUOUS_TRADING,
                stockId = stockId,
            ),
            at = currentTime,
        )
        if (!listing.isTradable || !permission.allowed) {
            null
        } else {
            canonicalSimulatorOrderBook(
                campaignSeed = campaignSeed,
                stock = stock,
                quote = projectedQuotes.getValue(stockId),
                session = sessions.getValue(stock.market),
                macro = macro,
                activeEvents = activeEvents,
                at = currentTime,
            )
        }
    }
    if (selectedBook != null) {
        projectedQuotes[selectedBook.stockId] = selectedBook.applyTopOfBook(
            projectedQuotes.getValue(selectedBook.stockId),
        )
    }
    return SimulatorMarketUiProjection(
        quotes = projectedQuotes,
        marketSessions = sessions,
        selectedOrderBook = selectedBook,
    )
}
