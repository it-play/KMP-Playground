package com.amond.kmpbook.presentation

import com.amond.kmpbook.domain.model.EventScope
import com.amond.kmpbook.domain.model.EventSeverity
import com.amond.kmpbook.domain.model.EventType
import com.amond.kmpbook.domain.model.GameEvent
import com.amond.kmpbook.domain.model.GameEventImpact
import com.amond.kmpbook.domain.model.ImpactDirection
import com.amond.kmpbook.domain.model.InstrumentTradingHalt
import com.amond.kmpbook.domain.model.KrxCircuitBreakerLevel
import com.amond.kmpbook.domain.model.KrxCircuitBreakerPhase
import com.amond.kmpbook.domain.model.KrxViDirection
import com.amond.kmpbook.domain.model.KrxViKind
import com.amond.kmpbook.domain.model.KrxViPhase
import com.amond.kmpbook.domain.model.KrxViSession
import com.amond.kmpbook.domain.model.ListingLifecycleEventKind
import com.amond.kmpbook.domain.model.ListingFinalDisposition
import com.amond.kmpbook.domain.model.ListingFinalDispositionType
import com.amond.kmpbook.domain.model.ListingLifecycleLedgerEvent
import com.amond.kmpbook.domain.model.ListingLifecycleReason
import com.amond.kmpbook.domain.model.ListingLifecycleStatus
import com.amond.kmpbook.domain.model.ListingNoticeLevel
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.MarketIndexId
import com.amond.kmpbook.domain.model.OrderSide
import com.amond.kmpbook.domain.model.OrderStatus
import com.amond.kmpbook.domain.model.OrderType
import com.amond.kmpbook.domain.model.TradingHaltOrderPolicy
import com.amond.kmpbook.domain.model.TradingHaltReason
import com.amond.kmpbook.domain.model.TradeSettlementKind
import com.amond.kmpbook.domain.model.TurnStep
import com.amond.kmpbook.domain.simulation.MarketMicrostructure
import com.amond.kmpbook.domain.simulation.TradingProtectionEngine
import com.amond.kmpbook.domain.time.GameCalendar
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.math.exp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class SimulatorProtectionIntegrationTest {
    @Test
    fun protectionAndListingStateRoundTripThroughSaveRestore() {
        val source = playingViewModel(seed = 61_001L)
        val original = source.currentState
        val stock = original.stocks.first { it.market == Market.KOSPI && it.hasCorporateEarnings }
        val date = original.currentDate
        val listing = original.listingLifecycleStates.getValue(stock.id).copy(
            status = ListingLifecycleStatus.DEFICIENCY_NOTICE,
            activeReason = ListingLifecycleReason.KRX_LISTING_MAINTENANCE,
            designatedOn = date,
            cureDeadline = date,
            designationCount = 1,
            ledgerSequence = 1L,
        )
        val ledgerEvent = ListingLifecycleLedgerEvent(
            id = "listing-round-trip:${stock.id}",
            sequence = 1L,
            stockId = stock.id,
            tradingDate = date,
            kind = ListingLifecycleEventKind.DEFICIENCY_DESIGNATED,
            fromStatus = ListingLifecycleStatus.LISTED,
            toStatus = ListingLifecycleStatus.DEFICIENCY_NOTICE,
            reason = ListingLifecycleReason.KRX_LISTING_MAINTENANCE,
            level = ListingNoticeLevel.CAUTION,
            title = "상장 유지 요건 안내",
            summary = "저장·복원 통합 테스트",
            deadline = date,
        )
        val halt = InstrumentTradingHalt(
            stockId = stock.id,
            reason = TradingHaltReason.MATERIAL_DISCLOSURE,
            detail = "중요 공시 확인",
            startedAt = original.currentTime,
            scheduledReleaseAt = original.currentTime + 30.minutes,
            policy = queuedOrderPolicy,
        )
        val listingStates = original.listingLifecycleStates + (stock.id to listing)
        val protection = original.tradingProtectionSnapshot.copy(
            instrumentTradingHalts = original.tradingProtectionSnapshot.instrumentTradingHalts +
                (stock.id to halt),
        )
        val pendingEventReturns = mapOf(stock.id to 0.075)
        val saved = original.copy(
            pendingClosedEventLogReturns = pendingEventReturns,
            listingLifecycleStates = listingStates,
            listingLifecycleLedger = listOf(ledgerEvent),
            tradingProtectionSnapshot = protection,
        )

        val restored = SimulatorViewModel()
        assertTrue(restored.restoreGame(saved))
        val actual = restored.currentState

        assertEquals(pendingEventReturns, actual.pendingClosedEventLogReturns)
        assertEquals(listingStates, actual.listingLifecycleStates)
        assertEquals(listOf(ledgerEvent), actual.listingLifecycleLedger)
        assertEquals(protection, actual.tradingProtectionSnapshot)
    }

    @Test
    fun suspendedListingRejectsOrdersButRemainsInIndicesUntilDelistingIsEffective() {
        val viewModel = playingViewModel(seed = 61_002L)
        viewModel.setTimeForTesting(Instant.parse("2026-08-07T14:00:00Z")) // 10:00 EDT.
        val original = viewModel.currentState
        val stock = original.stocks.first {
            it.market == Market.NASDAQ && it.hasCorporateEarnings
        }
        val suspended = original.listingLifecycleStates.getValue(stock.id).copy(
            status = ListingLifecycleStatus.TRADING_SUSPENDED,
            activeReason = ListingLifecycleReason.SERIOUS_COMPLIANCE_EVENT,
            designatedOn = original.currentDate,
            designationCount = 1,
        )
        assertTrue(
            viewModel.restoreGame(
                original.copy(
                    listingLifecycleStates = original.listingLifecycleStates +
                        (stock.id to suspended),
                ),
            ),
        )

        assertFalse(viewModel.placeOrder(stock.id, OrderSide.BUY, OrderType.MARKET, 1.0))
        assertTrue(viewModel.currentState.lastMessage.orEmpty().contains("거래가 멈춘"))
        assertTrue(viewModel.currentState.orders.isEmpty())

        viewModel.advance(TurnStep.ONE_HOUR)

        val after = viewModel.currentState
        val expectedSp500Constituents = after.stocks.count {
            it.market.isUnitedStates && it.hasCorporateEarnings
        }
        val expectedNasdaqConstituents = after.stocks.count {
            it.market == Market.NASDAQ && it.hasCorporateEarnings
        }
        assertEquals(
            expectedSp500Constituents,
            after.marketIndices.getValue(MarketIndexId.SP_500).constituentCount,
        )
        assertEquals(
            expectedNasdaqConstituents,
            after.marketIndices.getValue(MarketIndexId.NASDAQ_COMPOSITE).constituentCount,
        )
    }

    @Test
    fun marketAndInstrumentProtectionsBlockOrQueueAccordingToPolicy() {
        val marketBlocked = playingViewModel(seed = 61_003L)
        val blockedState = marketBlocked.currentState
        val stock = blockedState.stocks.first { it.market == Market.KOSPI && it.hasCorporateEarnings }
        val protection = blockedState.tradingProtectionSnapshot
        val initialCircuitBreaker = protection.krxCircuitBreakers.getValue(Market.KOSPI)
        val haltedCircuitBreaker = initialCircuitBreaker.copy(
            phase = KrxCircuitBreakerPhase.HALTED,
            triggeredLevels = setOf(KrxCircuitBreakerLevel.LEVEL_1),
            triggerIndexValues = mapOf(KrxCircuitBreakerLevel.LEVEL_1 to 2_480.0),
            activeLevel = KrxCircuitBreakerLevel.LEVEL_1,
            triggeredAt = blockedState.currentTime,
            haltEndsAt = blockedState.currentTime + 20.minutes,
            reopeningEndsAt = blockedState.currentTime + 30.minutes,
        )
        assertTrue(
            marketBlocked.restoreGame(
                blockedState.copy(
                    tradingProtectionSnapshot = protection.copy(
                        krxCircuitBreakers = protection.krxCircuitBreakers +
                            (Market.KOSPI to haltedCircuitBreaker),
                    ),
                ),
            ),
        )

        assertFalse(marketBlocked.placeOrder(stock.id, OrderSide.BUY, OrderType.MARKET, 1.0))
        assertTrue(marketBlocked.currentState.lastMessage.orEmpty().contains("서킷브레이커"))
        assertTrue(marketBlocked.currentState.orders.isEmpty())

        val instrumentQueued = playingViewModel(seed = 61_004L)
        val queuedState = instrumentQueued.currentState
        val queuedStock = queuedState.stocks.first { it.market == Market.KOSPI && it.hasCorporateEarnings }
        val queuedProtection = queuedState.tradingProtectionSnapshot
        val halt = InstrumentTradingHalt(
            stockId = queuedStock.id,
            reason = TradingHaltReason.MATERIAL_DISCLOSURE,
            detail = "중요 공시 확인",
            startedAt = queuedState.currentTime,
            scheduledReleaseAt = queuedState.currentTime + 30.minutes,
            policy = queuedOrderPolicy,
        )
        assertTrue(
            instrumentQueued.restoreGame(
                queuedState.copy(
                    tradingProtectionSnapshot = queuedProtection.copy(
                        instrumentTradingHalts = queuedProtection.instrumentTradingHalts +
                            (queuedStock.id to halt),
                    ),
                ),
            ),
        )

        assertTrue(instrumentQueued.placeOrder(queuedStock.id, OrderSide.BUY, OrderType.MARKET, 1.0))
        assertEquals(OrderStatus.ACCEPTED, instrumentQueued.currentState.orders.single().status)
        assertTrue(instrumentQueued.currentState.trades.isEmpty())

        instrumentQueued.advance(TurnStep.ONE_HOUR)

        assertEquals(OrderStatus.FILLED, instrumentQueued.currentState.orders.single().status)
        assertEquals(1, instrumentQueued.currentState.trades.size)

        val viQueued = playingViewModel(seed = 61_005L)
        val viState = viQueued.currentState
        val viStock = viState.stocks.first { it.market == Market.KOSDAQ && it.hasCorporateEarnings }
        val viProtection = viState.tradingProtectionSnapshot
        val quote = viState.quotes.getValue(viStock.id)
        val activeVi = viProtection.krxVolatilityInterruptions.getValue(viStock.id).copy(
            phase = KrxViPhase.CALL_AUCTION,
            kind = KrxViKind.DYNAMIC,
            session = KrxViSession.CONTINUOUS_AUCTION,
            referencePrice = quote.price,
            triggerRate = 0.06,
            direction = KrxViDirection.UPPER,
            triggeredAt = viState.currentTime,
            auctionEndsAt = viState.currentTime + 2.minutes,
            triggerCount = 1,
        )
        assertTrue(
            viQueued.restoreGame(
                viState.copy(
                    tradingProtectionSnapshot = viProtection.copy(
                        krxVolatilityInterruptions = viProtection.krxVolatilityInterruptions +
                            (viStock.id to activeVi),
                    ),
                ),
            ),
        )

        assertTrue(viQueued.placeOrder(viStock.id, OrderSide.BUY, OrderType.MARKET, 1.0))
        assertEquals(OrderStatus.ACCEPTED, viQueued.currentState.orders.single().status)
        assertTrue(viQueued.currentState.trades.isEmpty())
    }

    @Test
    fun closedMarketEventShockIsAppliedOnceAtTheNextOpeningPrice() {
        val viewModel = playingViewModel(seed = 61_006L)
        val closedAt = GameCalendar.fromGameLocalDateTime(LocalDateTime(2026, 8, 7, 17, 0))
        viewModel.setTimeForTesting(closedAt)
        val original = viewModel.currentState
        val stock = original.stocks.first { it.market == Market.KOSPI && it.hasCorporateEarnings }
        val event = GameEvent(
            id = "closed-event-carry:${stock.id}",
            title = "폐장 후 대형 수주",
            description = "폐장 중 발생한 종목 호재의 다음 개장 반영을 검증합니다.",
            scope = EventScope.STOCK,
            type = EventType.PRODUCT_TECHNOLOGY,
            severity = EventSeverity.MAJOR,
            impact = GameEventImpact(
                direction = ImpactDirection.POSITIVE,
                shockReturn = 0.25,
            ),
            startsAt = closedAt,
            durationHours = 120,
            affectedMarkets = setOf(stock.market),
            affectedStockIds = setOf(stock.id),
        )
        assertTrue(
            viewModel.restoreGame(
                original.copy(
                    activeEvents = listOf(event),
                    newsEvents = original.newsEvents + event,
                    eventEngineSnapshot = original.eventEngineSnapshot.copy(activeEvents = listOf(event)),
                    pendingClosedEventLogReturns = emptyMap(),
                ),
            ),
        )
        val priceBeforeClosedHour = viewModel.currentState.quotes.getValue(stock.id).price

        viewModel.advance(TurnStep.ONE_HOUR)

        val afterClosedHour = viewModel.currentState
        val carriedLogReturn = afterClosedHour.pendingClosedEventLogReturns.getValue(stock.id)
        assertTrue(carriedLogReturn > 0.0)
        assertEquals(priceBeforeClosedHour, afterClosedHour.quotes.getValue(stock.id).price)

        viewModel.setTimeForTesting(
            GameCalendar.fromGameLocalDateTime(LocalDateTime(2026, 8, 10, 9, 0)),
        )
        val priceBeforeOpening = viewModel.currentState.quotes.getValue(stock.id).price
        viewModel.advance(TurnStep.ONE_HOUR)

        val afterOpening = viewModel.currentState
        val openingBar = afterOpening.priceHistory.getValue(stock.id).last()
        val expectedOpeningPrice = MarketMicrostructure.roundNearest(
            stock,
            priceBeforeOpening * exp(carriedLogReturn),
        )
        assertEquals(expectedOpeningPrice, openingBar.open)
        assertFalse(stock.id in afterOpening.pendingClosedEventLogReturns)
    }

    @Test
    fun overlappingDisclosureHaltsBlockTheUnionOfBothIntervals() {
        val viewModel = playingViewModel(seed = 61_011L)
        val from = GameCalendar.fromGameLocalDateTime(LocalDateTime(2026, 8, 10, 8, 50))
        viewModel.setTimeForTesting(from)
        val original = viewModel.currentState
        val stock = original.stocks.first { it.market == Market.KOSPI && it.hasCorporateEarnings }
        fun disclosure(id: String, startsAt: Instant) = GameEvent(
            id = "$id:${stock.id}:${startsAt.epochSeconds}",
            title = "${stock.name} 중요정보 확인",
            description = "서로 겹치는 공시 거래정지 구간을 검증합니다.",
            scope = EventScope.STOCK,
            type = EventType.REGULATION_POLICY,
            severity = EventSeverity.MAJOR,
            impact = GameEventImpact(direction = ImpactDirection.NEUTRAL),
            startsAt = startsAt,
            durationHours = 1,
            affectedMarkets = setOf(stock.market),
            affectedStockIds = setOf(stock.id),
        )
        val first = disclosure("accounting_issue", from + 10.minutes) // 09:00-09:30
        val second = disclosure("major_lawsuit", from + 30.minutes) // 09:20-09:50
        val events = listOf(first, second)
        assertTrue(
            viewModel.restoreGame(
                original.copy(
                    activeEvents = events,
                    newsEvents = original.newsEvents + events,
                    eventEngineSnapshot = original.eventEngineSnapshot.copy(activeEvents = events),
                ),
            ),
        )
        assertTrue(viewModel.placeOrder(stock.id, OrderSide.BUY, OrderType.MARKET, 1.0))
        assertTrue(viewModel.currentState.trades.isEmpty())

        viewModel.advance(TurnStep.ONE_HOUR) // 08:50-09:50: union is fully blocked after open.

        assertEquals(OrderStatus.ACCEPTED, viewModel.currentState.orders.single().status)
        assertTrue(viewModel.currentState.trades.isEmpty())
        viewModel.advance(TurnStep.ONE_HOUR)
        assertEquals(OrderStatus.FILLED, viewModel.currentState.orders.single().status)
        assertEquals(1, viewModel.currentState.trades.size)
    }

    @Test
    fun zeroCashLiquidationUsesPaymentDateAndSurvivesTaxLedgerReplay() {
        val viewModel = playingViewModel(seed = 61_007L)
        val tenKst = GameCalendar.fromGameLocalDateTime(LocalDateTime(2026, 8, 7, 10, 0))
        viewModel.setTimeForTesting(tenKst)
        val stock = viewModel.currentState.stocks.first {
            it.market == Market.KOSPI && it.isFundLike
        }
        assertTrue(viewModel.placeOrder(stock.id, OrderSide.BUY, OrderType.MARKET, 1.0))
        val bought = viewModel.currentState
        val holding = assertNotNull(bought.holdings[stock.id])
        val date = LocalDate(2026, 8, 14)
        val pending = bought.listingLifecycleStates.getValue(stock.id).copy(
            status = ListingLifecycleStatus.LIQUIDATION_PENDING,
            activeReason = ListingLifecycleReason.ETF_VOLUNTARY_LIQUIDATION,
            designatedOn = date,
            scheduledDelistingOn = date,
            settlementDueOn = date,
            tradingAllowedUntilDelisting = false,
            finalDisposition = ListingFinalDisposition(
                type = ListingFinalDispositionType.CASH_LIQUIDATION,
                effectiveOn = date,
                settlementDueOn = date,
                cashPerUnit = 0.0,
                entitledQuantity = holding.quantity,
                entitledCostBasis = holding.costBasis,
            ),
        )
        assertTrue(
            viewModel.restoreGame(
                bought.copy(
                    listingLifecycleStates = bought.listingLifecycleStates +
                        (stock.id to pending),
                ),
            ),
        )
        viewModel.setTimeForTesting(
            GameCalendar.fromGameLocalDateTime(LocalDateTime(2026, 8, 14, 15, 0)),
        )
        val cashBefore = viewModel.currentState.cashByCurrency.getValue(stock.currency)

        viewModel.advance(TurnStep.ONE_HOUR)

        val paid = viewModel.currentState
        val dispositionTrade = paid.trades.last()
        assertEquals(0.0, dispositionTrade.price)
        assertEquals(TradeSettlementKind.CONTRACTUAL_CASH_SETTLEMENT, dispositionTrade.settlementKind)
        assertEquals(date, dispositionTrade.settlementDateOverride)
        assertEquals(date, paid.realizedGains.last().settlementDate)
        assertEquals(cashBefore, paid.cashByCurrency.getValue(stock.currency))
        assertFalse(stock.id in paid.holdings)
        assertFalse(dispositionTrade.id in paid.pendingTaxSettlementTradeIds)

        val restored = SimulatorViewModel()
        assertTrue(restored.restoreGame(paid))
        assertEquals(date, restored.currentState.realizedGains.last().settlementDate)
        assertEquals(0.0, restored.currentState.trades.last().price)
    }

    @Test
    fun nextSessionAlertHaltIsUpcomingUntilOpenThenBlocksWithoutOverwritingCurrentState() {
        val viewModel = playingViewModel(seed = 61_008L)
        val eightKst = GameCalendar.fromGameLocalDateTime(LocalDateTime(2026, 8, 10, 8, 0))
        viewModel.setTimeForTesting(eightKst)
        val original = viewModel.currentState
        val stock = original.stocks.first { it.market == Market.KOSPI && it.hasCorporateEarnings }
        val session = requireNotNull(
            GameCalendar.regularSessionWindow(stock.market, LocalDate(2026, 8, 10)),
        )
        val scheduled = TradingProtectionEngine.startInstrumentTradingHalt(
            stockId = stock.id,
            reason = TradingHaltReason.REGULATORY_ACTION,
            detail = "최초 투자위험 지정",
            startedAt = session.opensAt,
            scheduledReleaseAt = session.closesAt,
        )
        val protection = original.tradingProtectionSnapshot.copy(
            scheduledInstrumentTradingHalts = mapOf("danger-halt" to scheduled),
        )
        assertTrue(viewModel.restoreGame(original.copy(tradingProtectionSnapshot = protection)))

        val beforeOpenProjection = buildProtectionUiProjection(
            snapshot = viewModel.currentState.tradingProtectionSnapshot,
            listingStates = viewModel.currentState.listingLifecycleStates,
            selectedStockId = stock.id,
            selectedMarket = stock.market,
        )
        assertFalse(beforeOpenProjection.symbolBadges[stock.id]?.text == "거래정지")

        viewModel.advance(TurnStep.ONE_HOUR)

        val atOpen = viewModel.currentState
        assertTrue(atOpen.tradingProtectionSnapshot.scheduledInstrumentTradingHalts.isEmpty())
        assertTrue(
            TradingProtectionEngine.isInstrumentHaltActive(
                atOpen.tradingProtectionSnapshot.instrumentTradingHalts.getValue(stock.id),
                atOpen.currentTime,
            ),
        )
        assertFalse(viewModel.placeOrder(stock.id, OrderSide.BUY, OrderType.MARKET, 1.0))
    }

    @Test
    fun restoredScheduledHaltBlocksOrderAndAppearsInUiAtExactOpen() {
        val viewModel = playingViewModel(seed = 61_010L)
        val nineKst = GameCalendar.fromGameLocalDateTime(LocalDateTime(2026, 8, 10, 9, 0))
        viewModel.setTimeForTesting(nineKst)
        val original = viewModel.currentState
        val stock = original.stocks.first { it.market == Market.KOSPI && it.hasCorporateEarnings }
        val session = requireNotNull(
            GameCalendar.regularSessionWindow(stock.market, LocalDate(2026, 8, 10)),
        )
        val scheduled = TradingProtectionEngine.startInstrumentTradingHalt(
            stockId = stock.id,
            reason = TradingHaltReason.REGULATORY_ACTION,
            detail = "최초 투자위험 지정",
            startedAt = session.opensAt,
            scheduledReleaseAt = session.closesAt,
        )
        val protection = original.tradingProtectionSnapshot.copy(
            scheduledInstrumentTradingHalts = mapOf("danger-halt" to scheduled),
        )

        assertTrue(viewModel.restoreGame(original.copy(tradingProtectionSnapshot = protection)))
        assertFalse(viewModel.placeOrder(stock.id, OrderSide.BUY, OrderType.MARKET, 1.0))
        val projection = buildProtectionUiProjection(
            snapshot = viewModel.currentState.tradingProtectionSnapshot,
            listingStates = viewModel.currentState.listingLifecycleStates,
            selectedStockId = stock.id,
            selectedMarket = stock.market,
            at = viewModel.currentState.currentTime,
        )
        assertEquals("거래정지", projection.symbolBadges.getValue(stock.id).text)
    }

    @Test
    fun overlappingDisclosureAndFullDayAlertHaltsHaveNoIntrahourTradingGap() {
        val viewModel = playingViewModel(seed = 61_009L)
        val nineKst = GameCalendar.fromGameLocalDateTime(LocalDateTime(2026, 8, 10, 9, 0))
        viewModel.setTimeForTesting(nineKst)
        val original = viewModel.currentState
        val stock = original.stocks.first { it.market == Market.KOSPI && it.hasCorporateEarnings }
        val session = requireNotNull(
            GameCalendar.regularSessionWindow(stock.market, LocalDate(2026, 8, 10)),
        )
        val disclosure = TradingProtectionEngine.startInstrumentTradingHalt(
            stockId = stock.id,
            reason = TradingHaltReason.MATERIAL_DISCLOSURE,
            detail = "중요 공시 확인",
            startedAt = session.opensAt,
            scheduledReleaseAt = session.opensAt + 30.minutes,
        )
        val fullDay = TradingProtectionEngine.startInstrumentTradingHalt(
            stockId = stock.id,
            reason = TradingHaltReason.REGULATORY_ACTION,
            detail = "최초 투자위험 지정",
            startedAt = session.opensAt,
            scheduledReleaseAt = session.closesAt,
        )
        val protection = original.tradingProtectionSnapshot.copy(
            instrumentTradingHalts = original.tradingProtectionSnapshot.instrumentTradingHalts +
                (stock.id to disclosure),
            scheduledInstrumentTradingHalts = mapOf("full-day-alert" to fullDay),
        )
        assertTrue(viewModel.restoreGame(original.copy(tradingProtectionSnapshot = protection)))

        viewModel.advance(TurnStep.ONE_HOUR)

        val after = viewModel.currentState
        assertEquals(0L, after.priceHistory.getValue(stock.id).last().volume)
        val controlling = after.tradingProtectionSnapshot
            .instrumentTradingHalts.getValue(stock.id)
        assertEquals("최초 투자위험 지정", controlling.detail)
        assertTrue(TradingProtectionEngine.isInstrumentHaltActive(controlling, after.currentTime))
        assertFalse(after.newsEvents.any { event ->
            event.id.startsWith("halt-release:${stock.id}:") && event.startsAt == after.currentTime
        })
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
