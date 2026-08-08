package com.amond.kmpbook.presentation

import com.amond.kmpbook.domain.model.EventScope
import com.amond.kmpbook.domain.model.EventSeverity
import com.amond.kmpbook.domain.model.EventType
import com.amond.kmpbook.domain.model.GameEvent
import com.amond.kmpbook.domain.model.GameEventImpact
import com.amond.kmpbook.domain.model.ImpactDirection
import com.amond.kmpbook.domain.model.InstrumentTradingHalt
import com.amond.kmpbook.domain.model.ListingLifecycleEventKind
import com.amond.kmpbook.domain.model.ListingLifecycleLedgerEvent
import com.amond.kmpbook.domain.model.ListingLifecycleStatus
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.MarketIndexId
import com.amond.kmpbook.domain.model.OrderSide
import com.amond.kmpbook.domain.model.OrderStatus
import com.amond.kmpbook.domain.model.OrderType
import com.amond.kmpbook.domain.model.PriceBar
import com.amond.kmpbook.domain.model.TradingHaltOrderPolicy
import com.amond.kmpbook.domain.model.TradingHaltReason
import com.amond.kmpbook.domain.model.TurnStep
import com.amond.kmpbook.domain.model.UsMwcbPhase
import com.amond.kmpbook.domain.simulation.TradingProtectionRules
import com.amond.kmpbook.domain.time.GameCalendar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class SimulatorRuntimeTimingBoundaryTest {
    @Test
    fun persistenceCrossingInsideLastMinuteRemainsPendingAtTurnEnd() {
        val turnEnd = Instant.parse("2026-08-10T01:00:00Z")
        val crossing = turnEnd - 30.minutes / 60

        val observation = runtimePersistenceObservationAt(
            conditionSince = crossing,
            turnEnd = turnEnd,
            persistence = TradingProtectionRules.KRX_CB_PERSISTENCE,
        )

        assertEquals(turnEnd, observation)
        assertTrue(observation - crossing < TradingProtectionRules.KRX_CB_PERSISTENCE)
    }

    @Test
    fun futureHaltPreservesTradingBeforeStartAndNeverCountsAfterKrxClose() {
        val morningStart = Instant.parse("2026-08-10T00:00:00Z") // 09:00 KST.
        val morningEnd = Instant.parse("2026-08-10T01:00:00Z")
        val morning = runtimeTradableIntervals(
            market = Market.KOSPI,
            from = morningStart,
            to = morningEnd,
            blocked = listOf(
                RuntimeTradingInterval(
                    startsAt = Instant.parse("2026-08-10T00:30:00Z"),
                    endsAt = morningEnd,
                ),
            ),
        )
        assertEquals(
            listOf(RuntimeTradingInterval(morningStart, Instant.parse("2026-08-10T00:30:00Z"))),
            morning,
        )
        assertEquals(0.5, runtimeTradingFraction(morningStart, morningEnd, morning))

        val closingStart = Instant.parse("2026-08-10T06:00:00Z") // 15:00 KST.
        val closingEnd = Instant.parse("2026-08-10T07:00:00Z")
        val closing = runtimeTradableIntervals(
            market = Market.KOSPI,
            from = closingStart,
            to = closingEnd,
            blocked = listOf(
                RuntimeTradingInterval(
                    startsAt = Instant.parse("2026-08-10T06:00:00Z"),
                    endsAt = Instant.parse("2026-08-10T06:20:00Z"),
                ),
            ),
        )
        assertEquals(
            listOf(
                RuntimeTradingInterval(
                    Instant.parse("2026-08-10T06:20:00Z"),
                    Instant.parse("2026-08-10T06:30:00Z"),
                ),
            ),
            closing,
        )
        assertEquals(1.0 / 6.0, runtimeTradingFraction(closingStart, closingEnd, closing), 1e-12)
    }

    @Test
    fun orderDoesNotFillFromZeroVolumeBarExactlyWhenHaltReleases() {
        val viewModel = playingViewModel(72_001L)
        val eightKst = Instant.parse("2026-08-10T23:00:00Z") // 2026-08-11 08:00 KST.
        viewModel.setTimeForTesting(eightKst)
        val original = viewModel.currentState
        val stock = original.stocks.first { it.market == Market.KOSPI && it.hasCorporateEarnings }
        val halt = InstrumentTradingHalt(
            stockId = stock.id,
            reason = TradingHaltReason.MATERIAL_DISCLOSURE,
            detail = "09:00부터 한 시간 정지",
            startedAt = eightKst + 60.minutes,
            scheduledReleaseAt = eightKst + 120.minutes,
            policy = queuedOrderPolicy,
        )
        val protection = requireNotNull(original.tradingProtectionSnapshot)
        assertTrue(
            viewModel.restoreGame(
                original.copy(
                    tradingProtectionSnapshot = protection.copy(
                        instrumentTradingHalts = protection.instrumentTradingHalts + (stock.id to halt),
                    ),
                ),
            ),
        )
        assertTrue(viewModel.placeOrder(stock.id, OrderSide.BUY, OrderType.MARKET, 1.0))

        viewModel.advance(TurnStep.ONE_HOUR) // 08:00-09:00, closed.
        viewModel.advance(TurnStep.ONE_HOUR) // 09:00-10:00, fully halted.

        val atRelease = viewModel.currentState
        assertEquals(0L, atRelease.priceHistory.getValue(stock.id).last().volume)
        assertEquals(OrderStatus.ACCEPTED, atRelease.orders.single().status)
        assertTrue(atRelease.trades.isEmpty())
    }

    @Test
    fun orderQueuedBeforeOpenFillsInPreHaltPartOfSameHour() {
        val viewModel = playingViewModel(72_004L)
        val eightKst = Instant.parse("2026-08-10T23:00:00Z")
        viewModel.setTimeForTesting(eightKst)
        val original = viewModel.currentState
        val stock = original.stocks.first { it.market == Market.KOSPI && it.hasCorporateEarnings }
        val halt = InstrumentTradingHalt(
            stockId = stock.id,
            reason = TradingHaltReason.MATERIAL_DISCLOSURE,
            detail = "09:30부터 거래정지",
            startedAt = eightKst + 90.minutes,
            scheduledReleaseAt = eightKst + 180.minutes,
            policy = queuedOrderPolicy,
        )
        val protection = requireNotNull(original.tradingProtectionSnapshot)
        assertTrue(
            viewModel.restoreGame(
                original.copy(
                    tradingProtectionSnapshot = protection.copy(
                        instrumentTradingHalts = protection.instrumentTradingHalts + (stock.id to halt),
                    ),
                ),
            ),
        )
        assertTrue(viewModel.placeOrder(stock.id, OrderSide.BUY, OrderType.MARKET, 1.0))

        viewModel.advance(TurnStep.ONE_HOUR)
        viewModel.advance(TurnStep.ONE_HOUR)

        val state = viewModel.currentState
        assertTrue(state.priceHistory.getValue(stock.id).last().volume > 0L)
        assertEquals(OrderStatus.FILLED, state.orders.single().status)
        assertTrue(state.trades.single().executedAt < halt.startedAt)
    }

    @Test
    fun accumulatedSp500DailyLowIsNotReusedAsCurrentHourMwcbLow() {
        val viewModel = playingViewModel(72_005L)
        viewModel.setTimeForTesting(Instant.parse("2026-08-07T14:00:00Z")) // 10:00 EDT.
        val original = viewModel.currentState
        val indices = original.marketIndices.orEmpty()
        val sp500 = indices.getValue(MarketIndexId.SP_500)
        val poisonedDailyLow = sp500.copy(
            timestamp = original.currentTime,
            value = sp500.previousClose,
            open = sp500.previousClose,
            high = sp500.previousClose,
            low = sp500.previousClose * 0.79,
        )
        assertTrue(
            viewModel.restoreGame(
                original.copy(
                    marketIndices = indices + (MarketIndexId.SP_500 to poisonedDailyLow),
                    marketIndexHistory = original.marketIndexHistory.orEmpty() +
                        (MarketIndexId.SP_500 to listOf(poisonedDailyLow)),
                ),
            ),
        )

        viewModel.advance(TurnStep.ONE_HOUR)

        val mwcb = requireNotNull(viewModel.currentState.tradingProtectionSnapshot)
            .usMarketWideCircuitBreaker
        assertEquals(UsMwcbPhase.NORMAL, requireNotNull(mwcb).phase)
        assertTrue(mwcb.triggeredLevels.isEmpty())
    }

    @Test
    fun eventStartingInsideClosingHourIsObservedAtExactVenueClose() {
        val viewModel = playingViewModel(72_002L)
        val from = Instant.parse("2026-08-10T06:00:00Z") // 15:00 KST.
        viewModel.setTimeForTesting(from)
        val original = viewModel.currentState
        val stock = original.stocks.first { it.market == Market.KOSPI && it.hasCorporateEarnings }
        val event = GameEvent(
            id = "delisting_warning:closing-hour:${stock.id}",
            title = "상장 유지 요건 확인",
            description = "장 마감 전에 시작된 위험 공시",
            scope = EventScope.STOCK,
            type = EventType.REGULATION_POLICY,
            severity = EventSeverity.MAJOR,
            impact = GameEventImpact(direction = ImpactDirection.NEGATIVE),
            startsAt = from + 15.minutes,
            durationHours = 24,
            affectedMarkets = setOf(stock.market),
            affectedStockIds = setOf(stock.id),
        )
        assertTrue(viewModel.restoreGame(original.copy(newsEvents = original.newsEvents + event)))

        viewModel.advance(TurnStep.ONE_HOUR)

        assertTrue(
            viewModel.currentState.listingLifecycleStates.orEmpty().getValue(stock.id).status !=
                ListingLifecycleStatus.LISTED,
        )
    }

    @Test
    fun volatilityBoundsClipEveryExecutableOhlcEndpoint() {
        val bar = PriceBar(
            stockId = "bounded",
            startTime = Instant.parse("2026-08-10T14:00:00Z"),
            endTime = Instant.parse("2026-08-10T15:00:00Z"),
            step = TurnStep.ONE_HOUR,
            open = 100.0,
            high = 118.0,
            low = 87.0,
            close = 113.0,
            volume = 10_000L,
        )

        val bounded = runtimeClampBarToBounds(bar, RuntimePriceBounds(lower = 95.0, upper = 105.0))

        assertEquals(100.0, bounded.open)
        assertEquals(105.0, bounded.high)
        assertEquals(95.0, bounded.low)
        assertEquals(105.0, bounded.close)
        assertTrue(listOf(bounded.open, bounded.high, bounded.low, bounded.close).all { it in 95.0..105.0 })
    }

    @Test
    fun etnMaturityKeepsFinalSessionThenUsesListingLiquidationAndPaymentFlow() {
        val viewModel = playingViewModel(72_003L)
        val original = viewModel.currentState
        val etn = original.stocks.first { it.identityProfile?.maturityDate != null }
        val maturity = requireNotNull(etn.identityProfile?.maturityDate)
        val maturityOpen = requireNotNull(
            GameCalendar.regularSessionWindow(
                etn.market,
                kotlinx.datetime.LocalDate.parse(maturity),
            ),
        ).opensAt
        viewModel.setTimeForTesting(maturityOpen)

        viewModel.advance(TurnStep.ONE_HOUR)

        val duringFinalSession = viewModel.currentState
        assertTrue(etn.id !in duringFinalSession.terminatedInstrumentIds.orEmpty())
        assertEquals(
            ListingLifecycleStatus.LISTED,
            duringFinalSession.listingLifecycleStates.orEmpty().getValue(etn.id).status,
        )

        repeat(15) {
            if (viewModel.currentState.listingLifecycleStates.orEmpty().getValue(etn.id).isTerminal) {
                return@repeat
            }
            viewModel.advance(TurnStep.ONE_DAY)
        }

        val state = viewModel.currentState
        assertTrue(etn.id in state.terminatedInstrumentIds.orEmpty())
        assertEquals(
            ListingLifecycleStatus.TERMINATED,
            state.listingLifecycleStates.orEmpty().getValue(etn.id).status,
        )
        assertEquals(
            ListingLifecycleEventKind.TERMINATED,
            state.listingLifecycleLedger.orEmpty().last { it.stockId == etn.id }.kind,
        )
        val kinds = state.listingLifecycleLedger.orEmpty()
            .filter { it.stockId == etn.id }
            .map(ListingLifecycleLedgerEvent::kind)
        assertTrue(ListingLifecycleEventKind.DELISTING_SCHEDULED in kinds)
        assertTrue(ListingLifecycleEventKind.LIQUIDATION_STARTED in kinds)
    }

    private fun playingViewModel(seed: Long): SimulatorViewModel = SimulatorViewModel().apply {
        newGame(NewGameOptions(seed = seed))
    }

    private companion object {
        val queuedOrderPolicy = TradingHaltOrderPolicy(
            acceptsNewOrders = true,
            allowsCancellation = true,
            allowsExecution = false,
            allowsContinuousTrading = false,
        )
    }
}
