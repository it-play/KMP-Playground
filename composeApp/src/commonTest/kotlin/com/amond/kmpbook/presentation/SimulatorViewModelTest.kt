package com.amond.kmpbook.presentation

import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.data.StockCatalog
import com.amond.kmpbook.domain.model.GamePhase
import com.amond.kmpbook.domain.model.EventScope
import com.amond.kmpbook.domain.model.EventSeverity
import com.amond.kmpbook.domain.model.EventType
import com.amond.kmpbook.domain.model.GameEvent
import com.amond.kmpbook.domain.model.GameEventImpact
import com.amond.kmpbook.domain.model.ImpactDirection
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.MarketIndexId
import com.amond.kmpbook.domain.model.EtfExposureRegion
import com.amond.kmpbook.domain.model.OrderSide
import com.amond.kmpbook.domain.model.OrderStatus
import com.amond.kmpbook.domain.model.OrderType
import com.amond.kmpbook.domain.model.PendingCorporateAction
import com.amond.kmpbook.domain.model.CorporateActionKind
import com.amond.kmpbook.domain.model.CorporateActionSource
import com.amond.kmpbook.domain.model.Screen
import com.amond.kmpbook.domain.model.TimeInForce
import com.amond.kmpbook.domain.model.TurnStep
import com.amond.kmpbook.domain.simulation.DeterministicRandom
import com.amond.kmpbook.domain.simulation.PriceEngine
import com.amond.kmpbook.domain.simulation.MarketMicrostructure
import com.amond.kmpbook.domain.simulation.ScheduledEventEngine
import com.amond.kmpbook.domain.tax.TaxLiability
import com.amond.kmpbook.domain.tax.TaxLiabilityStatus
import com.amond.kmpbook.domain.time.GameCalendar
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.math.round
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SimulatorViewModelTest {
    @Test
    fun setupStartsNewGameWithFullCatalogAndInitialHistory() {
        val viewModel = SimulatorViewModel()

        assertEquals(GamePhase.SETUP, viewModel.currentState.phase)
        viewModel.newGame(NewGameOptions(seed = 77L))
        val state = viewModel.currentState

        assertEquals(GamePhase.PLAYING, state.phase)
        assertEquals(100_000_000.0, state.cashByCurrency[Currency.KRW])
        assertEquals(GameCalendar.startInstant, state.currentTime)
        assertEquals(StockCatalog.all.size, state.stocks.size)
        assertEquals(StockCatalog.all.size, state.quotes.size)
        assertTrue(state.priceHistory.values.all { it.size == 1 })
        assertNotNull(state.selectedStock)

        viewModel.resetGame()
        assertEquals(GamePhase.SETUP, viewModel.currentState.phase)
    }

    @Test
    fun everyAdvanceStepRunsAsOneHourTurns() {
        val viewModel = playingViewModel(seed = 101L)
        viewModel.selectTurnStep(TurnStep.FOUR_HOURS)

        viewModel.advance()

        val state = viewModel.currentState
        assertEquals(GameCalendar.advance(GameCalendar.startInstant, TurnStep.FOUR_HOURS), state.currentTime)
        assertEquals(4L, state.turn)
        assertTrue(state.priceHistory.values.all { it.size == 5 })
        assertEquals(TurnStep.FOUR_HOURS, state.selectedTurnStep)
        assertEquals(Market.KOSPI.currency, state.selectedStock?.currency)
    }

    @Test
    fun marketBuyAndSellCreateHoldingsTradesFeesAndDomesticSaleTax() {
        val viewModel = playingViewModel(seed = 202L)
        val stock = viewModel.currentState.stocks.first { it.market == Market.KOSPI }

        assertTrue(viewModel.placeOrder(stock.id, OrderSide.BUY, OrderType.MARKET, 1.0))
        val bought = viewModel.currentState
        assertEquals(OrderStatus.FILLED, bought.orders.last().status)
        assertEquals(1.0, bought.holdings.getValue(stock.id).quantity)
        assertEquals(1, bought.trades.size)
        assertTrue(bought.transactionCosts.last().commission > 0.0)
        assertEquals(0.0, bought.transactionCosts.last().saleTax)

        assertTrue(viewModel.placeOrder(stock.id, OrderSide.SELL, OrderType.MARKET, 1.0))
        val sold = viewModel.currentState
        assertEquals(OrderStatus.FILLED, sold.orders.last().status)
        assertFalse(stock.id in sold.holdings)
        assertEquals(2, sold.trades.size)
        assertTrue(sold.transactionCosts.last().saleTax > 0.0)
        assertNotNull(sold.transactionCosts.last().taxBreakdown)
        assertEquals(1, sold.realizedGains.size)
        assertNotNull(sold.annualTaxLedgers[sold.currentDate.year])
    }

    @Test
    fun balanceQuantityLimitAndCancellationAreValidated() {
        val lowCash = SimulatorViewModel().apply {
            newGame(NewGameOptions(initialCapitalKrw = 10_000.0, seed = 3L))
        }
        val expensive = lowCash.currentState.stocks.first { it.market == Market.KOSPI }
        assertFalse(lowCash.placeOrder(expensive.id, OrderSide.BUY, OrderType.MARKET, 1.0))
        assertTrue(lowCash.currentState.lastMessage.orEmpty().contains("현금"))

        val viewModel = playingViewModel(seed = 4L)
        val stock = viewModel.currentState.stocks.first { it.market == Market.KOSPI }
        assertTrue(
            viewModel.placeOrder(
                stockId = stock.id,
                side = OrderSide.BUY,
                type = OrderType.LIMIT,
                quantity = 1.0,
                limitPrice = 50_000.0,
                timeInForce = TimeInForce.GOOD_TILL_CANCELLED,
            ),
        )
        assertEquals(OrderStatus.ACCEPTED, viewModel.currentState.orders.last().status)
        assertTrue(viewModel.cancelOrder(viewModel.currentState.orders.last().id))
        assertEquals(OrderStatus.CANCELLED, viewModel.currentState.orders.last().status)

        val us = viewModel.currentState.stocks.first { it.market == Market.NASDAQ }
        assertFalse(viewModel.placeOrder(us.id, OrderSide.BUY, OrderType.MARKET, 0.5))
    }

    @Test
    fun iocCancelsAndFokRejectsWhenImmediateFillIsUnavailable() {
        val viewModel = playingViewModel(seed = 5L)
        val us = viewModel.currentState.stocks.first { it.market == Market.NASDAQ }
        assertFalse(viewModel.currentState.marketSessions.getValue(Market.NASDAQ).isTradable)

        assertTrue(
            viewModel.placeOrder(
                us.id,
                OrderSide.BUY,
                OrderType.MARKET,
                1.0,
                timeInForce = TimeInForce.IMMEDIATE_OR_CANCEL,
            ),
        )
        assertEquals(OrderStatus.CANCELLED, viewModel.currentState.orders.last().status)

        assertFalse(
            viewModel.placeOrder(
                us.id,
                OrderSide.BUY,
                OrderType.MARKET,
                1.0,
                timeInForce = TimeInForce.FILL_OR_KILL,
            ),
        )
        assertEquals(OrderStatus.REJECTED, viewModel.currentState.orders.last().status)
    }

    @Test
    fun dayOrderPlacedAfterCloseWaitsUntilNextTradingDayClose() {
        val viewModel = playingViewModel(seed = 55L)
        viewModel.setTimeForTesting(
            GameCalendar.fromGameLocalDateTime(LocalDateTime(2026, 8, 7, 17, 0)),
        )
        val stock = viewModel.currentState.stocks.first { it.market == Market.KOSPI }

        assertTrue(
            viewModel.placeOrder(
                stock.id,
                OrderSide.BUY,
                OrderType.LIMIT,
                1.0,
                limitPrice = 50_000.0,
                timeInForce = TimeInForce.DAY,
            ),
        )
        viewModel.advance(TurnStep.ONE_HOUR)

        assertEquals(OrderStatus.ACCEPTED, viewModel.currentState.orders.last().status)
    }

    @Test
    fun explicitAndAutomaticForeignExchangeUseSeparateCashBalances() {
        val viewModel = playingViewModel(seed = 6L)
        val before = viewModel.currentState

        assertTrue(viewModel.exchange(Currency.KRW, Currency.USD, 1_000_000.0))
        val exchanged = viewModel.currentState
        assertTrue(exchanged.cashByCurrency.getValue(Currency.KRW) < before.cashByCurrency.getValue(Currency.KRW))
        assertTrue(exchanged.cashByCurrency.getValue(Currency.USD) > 0.0)
        assertEquals(1, exchanged.foreignExchangeLedger.size)
        assertFalse(exchanged.foreignExchangeLedger.single().automatic)
    }

    @Test
    fun endDateClampsAndEntersSettlement() {
        val viewModel = playingViewModel(seed = 7L)
        viewModel.setTimeForTesting(GameCalendar.endInstant - 1.hours)

        viewModel.advance(TurnStep.ONE_WEEK)

        val state = viewModel.currentState
        assertEquals(GameCalendar.endInstant, state.currentTime)
        assertEquals(GamePhase.SETTLEMENT, state.phase)
        assertEquals(Screen.ENDING, state.screen)
        assertTrue(state.isAtEnd)
        assertNotNull(state.annualTaxLedgers[2040])
    }

    @Test
    fun fifoTaxGainUsesAcquisitionAndSaleExchangeRatesSeparately() {
        val viewModel = playingViewModel(seed = 700L)
        viewModel.setTimeForTesting(
            GameCalendar.fromGameLocalDateTime(LocalDateTime(2026, 8, 7, 23, 0)),
        )
        val stock = viewModel.currentState.stocks.first { it.market == Market.NASDAQ }

        assertTrue(viewModel.placeOrder(stock.id, OrderSide.BUY, OrderType.MARKET, 100.0))
        val bought = viewModel.currentState
        val buyTrade = bought.trades.last()
        val buyCost = bought.transactionCosts.last()
        viewModel.advance(TurnStep.ONE_HOUR)
        assertTrue(viewModel.placeOrder(stock.id, OrderSide.SELL, OrderType.MARKET, 100.0))

        val sold = viewModel.currentState
        val sellTrade = sold.trades.last()
        val sellCost = sold.transactionCosts.last()
        val gain = sold.realizedGains.single()
        val expectedAcquisition = round(buyTrade.grossAmount * buyCost.exchangeRateToKrw).toLong() +
            round(buyTrade.commission * buyCost.exchangeRateToKrw).toLong()
        val expectedProceeds = round(sellTrade.grossAmount * sellCost.exchangeRateToKrw).toLong()
        val expectedSellingCosts = round(
            (sellTrade.commission + sellTrade.tax) * sellCost.exchangeRateToKrw,
        ).toLong()

        assertEquals(expectedAcquisition, gain.taxCostBasisKrw)
        assertEquals(expectedProceeds, gain.taxGrossProceedsKrw)
        assertEquals(expectedSellingCosts, gain.taxDirectSellingCostsKrw)
        assertEquals(expectedProceeds - expectedAcquisition - expectedSellingCosts, gain.taxGainKrw)
        assertEquals(gain.taxGainKrw, sold.annualTaxLedgers.getValue(gain.settlementDate.year).foreignGainKrw)
        assertTrue(sold.fifoCostBasisBook.lots.isEmpty())
    }

    @Test
    fun usBuyAndSellTaxRatesLockTogetherOnTheirTPlusOneSettlementDate() {
        val viewModel = playingViewModel(seed = 701L)
        viewModel.setTimeForTesting(Instant.parse("2026-08-07T15:00:00Z"))
        val stock = viewModel.currentState.stocks.first { it.market == Market.NASDAQ }

        assertTrue(viewModel.placeOrder(stock.id, OrderSide.BUY, OrderType.MARKET, 10.0))
        assertTrue(viewModel.placeOrder(stock.id, OrderSide.SELL, OrderType.MARKET, 10.0))
        val pending = viewModel.currentState
        val buyTrade = pending.trades.first()
        val sellTrade = pending.trades.last()
        assertEquals(setOf(buyTrade.id, sellTrade.id), pending.pendingTaxSettlementTradeIds)
        assertEquals(
            pending.transactionCosts.first().exchangeRateToKrw,
            pending.taxExchangeRatesByTradeId?.getValue(buyTrade.id),
        )

        // Midnight in New York starts the T+1 settlement date (Monday after a Friday trade).
        viewModel.setTimeForTesting(Instant.parse("2026-08-10T03:00:00Z"))
        viewModel.advance(TurnStep.ONE_HOUR)

        val settled = viewModel.currentState
        val lockedBuyRate = settled.taxExchangeRatesByTradeId?.getValue(buyTrade.id)
        val lockedSellRate = settled.taxExchangeRatesByTradeId?.getValue(sellTrade.id)
        assertTrue(settled.pendingTaxSettlementTradeIds.isNullOrEmpty())
        assertEquals(settled.macro.usdKrw, lockedBuyRate)
        assertEquals(lockedBuyRate, lockedSellRate)
        val gain = settled.realizedGains.single()
        val rate = requireNotNull(lockedBuyRate)
        val expectedAcquisition = round(buyTrade.grossAmount * rate).toLong() +
            round(buyTrade.commission * rate).toLong()
        val expectedProceeds = round(sellTrade.grossAmount * rate).toLong()
        val expectedSellingCosts = round((sellTrade.commission + sellTrade.tax) * rate).toLong()
        assertEquals(expectedAcquisition, gain.taxCostBasisKrw)
        assertEquals(expectedProceeds, gain.taxGrossProceedsKrw)
        assertEquals(expectedSellingCosts, gain.taxDirectSellingCostsKrw)
        assertEquals(expectedProceeds - expectedAcquisition - expectedSellingCosts, gain.taxGainKrw)
    }

    @Test
    fun foreignSaleTaxYearAndRateFollowSettlementAcrossYearEnd() {
        val viewModel = playingViewModel(seed = 705L)
        viewModel.setTimeForTesting(Instant.parse("2026-12-31T15:00:00Z"))
        val stock = viewModel.currentState.stocks.first { it.market == Market.NYSE }

        assertTrue(viewModel.placeOrder(stock.id, OrderSide.BUY, OrderType.MARKET, 1.0))
        assertTrue(viewModel.placeOrder(stock.id, OrderSide.SELL, OrderType.MARKET, 1.0))
        assertEquals(LocalDate(2027, 1, 4), viewModel.currentState.realizedGains.single().settlementDate)

        // 2027-01-04 00:00 America/New_York.
        viewModel.setTimeForTesting(Instant.parse("2027-01-04T04:00:00Z"))
        viewModel.advance(TurnStep.ONE_HOUR)

        val settled = viewModel.currentState
        val gain = settled.realizedGains.single()
        assertTrue(settled.pendingTaxSettlementTradeIds.isNullOrEmpty())
        assertEquals(settled.macro.usdKrw, gain.exchangeRateToKrw)
        assertEquals(gain.taxGainKrw, settled.annualTaxLedgers.getValue(2027).foreignGainKrw)
        assertEquals(0L, settled.annualTaxLedgers.getValue(2026).foreignGainKrw)
    }

    @Test
    fun pendingTaxFxLedgerSurvivesRestoreAndLegacyNullMigratesAsFinal() {
        val source = playingViewModel(seed = 706L)
        source.setTimeForTesting(Instant.parse("2026-08-07T15:00:00Z"))
        val stock = source.currentState.stocks.first { it.market == Market.NASDAQ }
        assertTrue(source.placeOrder(stock.id, OrderSide.BUY, OrderType.MARKET, 1.0))
        assertTrue(source.placeOrder(stock.id, OrderSide.SELL, OrderType.MARKET, 1.0))
        val saved = source.currentState

        val restored = SimulatorViewModel()
        assertTrue(restored.restoreGame(saved))
        assertEquals(saved.taxExchangeRatesByTradeId, restored.currentState.taxExchangeRatesByTradeId)
        assertEquals(saved.pendingTaxSettlementTradeIds, restored.currentState.pendingTaxSettlementTradeIds)

        source.setTimeForTesting(Instant.parse("2026-08-10T03:00:00Z"))
        restored.setTimeForTesting(Instant.parse("2026-08-10T03:00:00Z"))
        source.advance(TurnStep.ONE_HOUR)
        restored.advance(TurnStep.ONE_HOUR)
        assertEquals(source.currentState.taxExchangeRatesByTradeId, restored.currentState.taxExchangeRatesByTradeId)
        assertEquals(source.currentState.pendingTaxSettlementTradeIds, restored.currentState.pendingTaxSettlementTradeIds)
        assertEquals(source.currentState.fifoCostBasisBook, restored.currentState.fifoCostBasisBook)
        assertEquals(source.currentState.realizedGains, restored.currentState.realizedGains)
        assertEquals(source.currentState.annualTaxLedgers, restored.currentState.annualTaxLedgers)

        val legacy = SimulatorViewModel()
        assertTrue(
            legacy.restoreGame(
                saved.copy(
                    taxExchangeRatesByTradeId = null,
                    pendingTaxSettlementTradeIds = null,
                ),
            ),
        )
        assertTrue(legacy.currentState.pendingTaxSettlementTradeIds.isNullOrEmpty())
        assertEquals(
            saved.transactionCosts.associate { it.tradeId to it.exchangeRateToKrw },
            legacy.currentState.taxExchangeRatesByTradeId,
        )
    }

    @Test
    fun restoreRejectsDomesticTradeInPendingTaxFxLedger() {
        val source = playingViewModel(seed = 707L)
        val stock = source.currentState.stocks.first { it.market == Market.KOSPI }
        assertTrue(source.placeOrder(stock.id, OrderSide.BUY, OrderType.MARKET, 1.0))
        val saved = source.currentState
        val tradeId = saved.trades.single().id

        val restored = SimulatorViewModel()
        assertFalse(restored.restoreGame(saved.copy(pendingTaxSettlementTradeIds = setOf(tradeId))))
    }

    @Test
    fun usTradesUseTPlusOneSettlementWhileKrxKeepsTPlusTwo() {
        val us = playingViewModel(seed = 702L)
        us.setTimeForTesting(Instant.parse("2026-12-28T15:00:00Z"))
        val usStock = us.currentState.stocks.first { it.market == Market.NASDAQ }
        assertTrue(us.placeOrder(usStock.id, OrderSide.BUY, OrderType.MARKET, 1.0))
        assertTrue(us.placeOrder(usStock.id, OrderSide.SELL, OrderType.MARKET, 1.0))
        assertEquals(LocalDate(2026, 12, 29), us.currentState.realizedGains.single().settlementDate)

        val kr = playingViewModel(seed = 703L)
        kr.setTimeForTesting(Instant.parse("2026-12-28T01:00:00Z"))
        val krStock = kr.currentState.stocks.first { it.market == Market.KOSPI }
        assertTrue(kr.placeOrder(krStock.id, OrderSide.BUY, OrderType.MARKET, 1.0))
        assertTrue(kr.placeOrder(krStock.id, OrderSide.SELL, OrderType.MARKET, 1.0))
        assertEquals(LocalDate(2026, 12, 30), kr.currentState.realizedGains.single().settlementDate)
    }

    @Test
    fun levelThreeCircuitBreakerBlocksQuotesAndImmediateExecution() {
        val viewModel = playingViewModel(seed = 704L)
        viewModel.setTimeForTesting(Instant.parse("2026-08-07T15:00:00Z"))
        val beforeHalt = viewModel.currentState
        val halted = beforeHalt.copy(
            macro = beforeHalt.macro.copy(usCircuitBreakerLevel = 3),
            usCircuitBreakerState = UsCircuitBreakerState(
                tradingDate = LocalDate(2026, 8, 7),
                triggeredLevels = setOf(3),
                haltedForDay = true,
            ),
        )
        assertTrue(viewModel.restoreGame(halted))
        val stock = viewModel.currentState.stocks.first { it.market == Market.NASDAQ }

        assertEquals(com.amond.kmpbook.domain.model.MarketSession.CLOSED, viewModel.currentState.marketSessions.getValue(Market.NASDAQ))
        assertTrue(viewModel.placeOrder(stock.id, OrderSide.BUY, OrderType.MARKET, 1.0))
        assertEquals(OrderStatus.ACCEPTED, viewModel.currentState.orders.last().status)
        assertTrue(viewModel.currentState.trades.isEmpty())
    }

    @Test
    fun queuedMarketOrderFillsAtTheCarriedOpeningGap() {
        val viewModel = playingViewModel(seed = 706L)
        viewModel.setTimeForTesting(
            GameCalendar.fromGameLocalDateTime(LocalDateTime(2026, 8, 10, 8, 0)),
        )
        val stock = viewModel.currentState.stocks.first {
            it.market == Market.KOSPI && it.isEtf &&
                it.etfProfile?.exposureRegion == EtfExposureRegion.UNITED_STATES
        }
        val restored = viewModel.currentState.copy(
            pendingEtfReferenceReturns = mapOf(stock.id to kotlin.math.ln(1.10)),
        )
        assertTrue(viewModel.restoreGame(restored))
        val preGapPrice = viewModel.currentState.quotes.getValue(stock.id).price

        assertTrue(viewModel.placeOrder(stock.id, OrderSide.BUY, OrderType.MARKET, 1.0))
        assertEquals(OrderStatus.ACCEPTED, viewModel.currentState.orders.last().status)
        viewModel.advance(TurnStep.ONE_HOUR) // 08:00-09:00 KST remains closed.
        // The clock now says 09:00, but the gap auction has not been generated yet.
        assertTrue(viewModel.placeOrder(stock.id, OrderSide.BUY, OrderType.MARKET, 1.0))
        assertTrue(viewModel.currentState.trades.isEmpty())
        viewModel.advance(TurnStep.ONE_HOUR) // 09:00 opening auction consumes the carry.

        val state = viewModel.currentState
        val openingBar = state.priceHistory.getValue(stock.id).last()
        val trades = state.trades.filter { it.stockId == stock.id }
        assertEquals(2, trades.size)
        assertTrue(trades.all { it.price == openingBar.open })
        assertEquals(openingBar.open, state.quotes.getValue(stock.id).open)
        assertTrue(openingBar.open > preGapPrice)
    }

    @Test
    fun krxFinalHalfHourPreservesTheClosedFxComplementForNextOpen() {
        val viewModel = playingViewModel(seed = 707L)
        viewModel.setTimeForTesting(
            GameCalendar.fromGameLocalDateTime(LocalDateTime(2026, 8, 10, 15, 0)),
        )
        val stock = viewModel.currentState.stocks.first {
            it.market == Market.KOSPI && it.isEtf &&
                it.etfProfile?.exposureRegion == EtfExposureRegion.UNITED_STATES &&
                it.etfProfile.fxProfile?.isFullyHedged == false
        }
        assertTrue(viewModel.restoreGame(viewModel.currentState.copy(pendingEtfReferenceReturns = emptyMap())))

        viewModel.advance(TurnStep.ONE_HOUR)

        val state = viewModel.currentState
        val expectedClosedHalf = PriceEngine(1L).referenceLogReturn(
            stock = stock,
            macro = state.macro,
            referenceTradingFraction = 0.0,
            fxTradingFraction = 0.5,
        )
        assertTrue(kotlin.math.abs(expectedClosedHalf) > 1e-12)
        assertEquals(
            expectedClosedHalf,
            state.pendingEtfReferenceReturns.orEmpty().getValue(stock.id),
            1e-12,
        )
    }

    @Test
    fun usLeadingClosedHalfIsAppliedToTheSameDayOpeningGap() {
        val viewModel = playingViewModel(seed = 709L)
        // 09:00 EDT: the following bar opens at 09:30 after a leading closed half-hour.
        viewModel.setTimeForTesting(Instant.parse("2026-08-07T13:00:00Z"))
        val stock = viewModel.currentState.stocks.first {
            it.market.isUnitedStates && it.isEtf &&
                it.etfProfile?.exposureRegion == EtfExposureRegion.DEVELOPED_EX_US
        }
        assertTrue(viewModel.restoreGame(viewModel.currentState.copy(pendingEtfReferenceReturns = emptyMap())))
        val previousPrice = viewModel.currentState.quotes.getValue(stock.id).price

        viewModel.advance(TurnStep.ONE_HOUR)

        val state = viewModel.currentState
        val openingBar = state.priceHistory.getValue(stock.id).last()
        assertTrue(openingBar.open != previousPrice)
        assertEquals(openingBar.open, state.quotes.getValue(stock.id).open)
        assertFalse(stock.id in state.pendingEtfReferenceReturns.orEmpty())
    }

    @Test
    fun levelOneIntrahourReopenDoesNotCreateAnOvernightFxPendingReturn() {
        val viewModel = playingViewModel(seed = 710L)
        viewModel.setTimeForTesting(Instant.parse("2026-08-07T14:00:00Z")) // 10:00 EDT
        val state = viewModel.currentState
        val tradingDate = LocalDate(2026, 8, 7)
        val alignedIndices = state.marketIndices.orEmpty().mapValues { (id, snapshot) ->
            if (id == MarketIndexId.SP_500) {
                val crashedValue = snapshot.previousClose * 0.92
                snapshot.copy(
                    sessionDate = tradingDate,
                    value = crashedValue,
                    open = snapshot.previousClose,
                    high = snapshot.previousClose,
                    low = crashedValue,
                )
            } else {
                snapshot
            }
        }
        val stock = state.stocks.first {
            it.market.isUnitedStates && it.isEtf &&
                it.etfProfile?.exposureRegion == EtfExposureRegion.DEVELOPED_EX_US
        }
        assertTrue(
            viewModel.restoreGame(
                state.copy(
                    pendingEtfReferenceReturns = emptyMap(),
                    marketIndices = alignedIndices,
                    usCircuitBreakerState = UsCircuitBreakerState(tradingDate = tradingDate),
                ),
            ),
        )

        viewModel.advance(TurnStep.ONE_HOUR)

        val after = viewModel.currentState
        assertEquals(1, after.macro.usCircuitBreakerLevel)
        assertFalse(stock.id in after.pendingEtfReferenceReturns.orEmpty())
    }

    @Test
    fun levelThreeBlocksUsUnderlyingCarryButKeepsFxCarry() {
        val viewModel = playingViewModel(seed = 708L)
        viewModel.setTimeForTesting(Instant.parse("2026-08-07T15:00:00Z"))
        val state = viewModel.currentState
        val tradingDate = LocalDate(2026, 8, 7)
        val alignedIndices = state.marketIndices.orEmpty().mapValues { (id, snapshot) ->
            if (id == MarketIndexId.SP_500) snapshot.copy(sessionDate = tradingDate) else snapshot
        }
        val stock = state.stocks.first {
            it.market == Market.KOSPI && it.isEtf &&
                it.etfProfile?.exposureRegion == EtfExposureRegion.UNITED_STATES &&
                it.etfProfile.fxProfile?.isFullyHedged == false
        }
        assertTrue(
            viewModel.restoreGame(
                state.copy(
                    pendingEtfReferenceReturns = emptyMap(),
                    marketIndices = alignedIndices,
                    usCircuitBreakerState = UsCircuitBreakerState(
                        tradingDate = tradingDate,
                        triggeredLevels = setOf(3),
                        haltedForDay = true,
                    ),
                ),
            ),
        )

        viewModel.advance(TurnStep.ONE_HOUR)

        val after = viewModel.currentState
        val fxOnly = PriceEngine(1L).referenceLogReturn(
            stock = stock,
            macro = after.macro,
            referenceTradingFraction = 0.0,
            fxTradingFraction = 1.0,
        )
        val phantomUnderlying = PriceEngine(1L).referenceLogReturn(
            stock = stock,
            macro = after.macro,
            referenceTradingFraction = 1.0,
            fxTradingFraction = 1.0,
        )
        assertEquals(3, after.macro.usCircuitBreakerLevel)
        assertTrue(kotlin.math.abs(fxOnly - phantomUnderlying) > 1e-12)
        assertEquals(
            fxOnly,
            after.pendingEtfReferenceReturns.orEmpty().getValue(stock.id),
            1e-12,
        )
    }

    @Test
    fun scheduledDistributionDropsQuoteAndDoesNotCreateFreeGrossReturn() {
        val viewModel = playingViewModel(seed = 705L)
        val stock = viewModel.currentState.stocks.first {
            it.market == Market.KOSPI && !it.isEtf && it.dividendYield > 0.0
        }
        assertTrue(viewModel.placeOrder(stock.id, OrderSide.BUY, OrderType.MARKET, 1.0))
        viewModel.advance(TurnStep.ONE_HOUR) // Align the holding mark with the synthetic quote.
        viewModel.setTimeForTesting(Instant.parse("2026-09-14T14:00:00Z")) // 23:00 KST
        val before = viewModel.currentState
        val priceBefore = before.quotes.getValue(stock.id).price
        val assetsBefore = before.totalAssetsKrw

        viewModel.advance(TurnStep.ONE_HOUR)

        val after = viewModel.currentState
        val dividend = after.dividendLedger.single { it.stockId == stock.id }
        assertTrue(after.quotes.getValue(stock.id).price < priceBefore)
        assertTrue(dividend.grossAmount > dividend.netAmount)
        assertEquals(
            assetsBefore - dividend.withholdingTaxKrw,
            after.totalAssetsKrw,
            com.amond.kmpbook.domain.simulation.MarketMicrostructure.tickSize(stock.market, priceBefore) + 1.0,
        )
    }

    @Test
    fun dueAnnualTaxIsPaidFromKrwCashAndMarkedPaid() {
        val source = playingViewModel(seed = 701L)
        val original = source.currentState
        val dueDate = LocalDate(2027, 5, 31)
        val liability = TaxLiability(
            id = "test-tax-2026",
            label = "테스트 양도소득세",
            taxYear = 2026,
            assessedTaxKrw = 1_000_000L,
            dueDate = dueDate,
            status = TaxLiabilityStatus.DUE,
        )
        val ledger = original.annualTaxLedgers.getValue(2026).copy(liabilities = listOf(liability))
        val notice = TaxPaymentNotice(
            id = liability.id,
            taxYear = 2026,
            dueDate = dueDate,
            amountKrw = liability.payableKrw,
            status = TaxLiabilityStatus.DUE,
            message = "납부 예정",
        )
        assertTrue(
            source.restoreGame(
                original.copy(
                    annualTaxLedgers = mapOf(2026 to ledger),
                    taxPaymentNotices = listOf(notice),
                ),
            ),
        )
        source.setTimeForTesting(
            GameCalendar.fromGameLocalDateTime(LocalDateTime(2027, 5, 30, 23, 0)),
        )
        val cashBefore = source.currentState.cashByCurrency.getValue(Currency.KRW)

        source.advance(TurnStep.ONE_HOUR)

        val paid = source.currentState
        assertEquals(cashBefore - 1_000_000.0, paid.cashByCurrency.getValue(Currency.KRW))
        assertEquals(TaxLiabilityStatus.PAID, paid.taxPaymentNotices.single().status)
        assertEquals(TaxLiabilityStatus.PAID, paid.annualTaxLedgers.getValue(2026).liabilities.single().status)
        assertEquals(1_000_000.0, paid.paidAnnualTaxKrw)
        assertTrue(paid.currentPortfolio.cumulativeTaxKrw >= 1_000_000.0)
    }

    @Test
    fun sameSeedAndRestoreProduceExactContinuation() {
        val first = playingViewModel(seed = 8_888L)
        val second = playingViewModel(seed = 8_888L)

        first.advance(TurnStep.TWELVE_HOURS)
        second.advance(TurnStep.TWELVE_HOURS)
        assertEquivalent(first.currentState, second.currentState)

        val restored = SimulatorViewModel()
        assertTrue(restored.restoreGame(first.currentState))
        first.advance(TurnStep.ONE_DAY)
        restored.advance(TurnStep.ONE_DAY)

        assertEquivalent(first.currentState, restored.currentState)
    }

    @Test
    fun officialEmploymentReleaseFlowsThroughRuntimeExactlyOnce() {
        val seed = 9_107L
        val from = GameCalendar.fromGameLocalDateTime(LocalDateTime(2026, 8, 7, 21, 0))
        val to = from + 1.hours
        val viewModel = playingViewModel(seed)
        viewModel.setTimeForTesting(from)
        val expected = ScheduledEventEngine(
            DeterministicRandom.mixSeed(seed, SimulatorRuntime.SCHEDULED_EVENT_STREAM_ID),
        ).generate(from, to, viewModel.currentState.stocks).emissions.single {
            it.occurrence.seriesId == "us-employment"
        }

        viewModel.advance(TurnStep.ONE_HOUR)

        val state = viewModel.currentState
        val releases = state.newsEvents.filter { it.id == expected.newsEvent.id }
        assertEquals(1, releases.size)
        assertEquals("공식 일정 · 게임 수치", releases.single().sourceLabel)
        assertEquals(expected.outcome.surpriseScore, state.macro.growthSurprise)
    }

    @Test
    fun callableEtnEventTerminatesTradingAndCreatesAutomaticDisposal() {
        val viewModel = playingViewModel(seed = 9_108L)
        viewModel.setTimeForTesting(Instant.parse("2026-08-07T15:00:00Z"))
        val etn = viewModel.currentState.stocks.single { it.symbol == "SLVO" }
        assertTrue(viewModel.placeOrder(etn.id, OrderSide.BUY, OrderType.MARKET, 1.0))
        val saved = viewModel.currentState
        val callEvent = GameEvent(
            id = "${SimulatorRuntime.ETN_CALL_EVENT_PREFIX}test",
            title = "테스트 조기상환 결정",
            description = "저장·복원 뒤 조기상환 수명주기를 검증합니다.",
            scope = EventScope.STOCK,
            type = EventType.FUND_OPERATION,
            severity = EventSeverity.MAJOR,
            impact = GameEventImpact(direction = ImpactDirection.MIXED),
            startsAt = saved.currentTime - 1.hours,
            durationHours = 1,
            affectedMarkets = setOf(etn.market),
            affectedSectors = setOf(etn.sector),
            affectedStockIds = setOf(etn.id),
        )
        assertTrue(viewModel.restoreGame(saved.copy(newsEvents = saved.newsEvents + callEvent)))

        viewModel.advance(TurnStep.ONE_HOUR)

        val after = viewModel.currentState
        assertFalse(etn.id in after.holdings)
        assertTrue(etn.id in after.terminatedInstrumentIds.orEmpty())
        assertEquals(OrderSide.SELL, after.trades.last().side)
        assertTrue(after.newsEvents.any { it.id.startsWith("instrument-early-redemption:${etn.id}:") })
        assertFalse(viewModel.placeOrder(etn.id, OrderSide.BUY, OrderType.MARKET, 1.0))
    }

    @Test
    fun reverseSplitAppliesAtOpeningBoundaryAndCashSettlesWholeShareRemainder() {
        val viewModel = playingViewModel(seed = 9_109L)
        viewModel.setTimeForTesting(Instant.parse("2026-08-07T03:00:00Z"))
        val stock = viewModel.currentState.stocks.first {
            it.market == Market.KOSPI && !it.supportsFractional
        }
        assertTrue(viewModel.placeOrder(stock.id, OrderSide.BUY, OrderType.MARKET, 3.0))
        viewModel.setTimeForTesting(Instant.parse("2026-08-09T23:00:00Z")) // Monday 08:00 KST.
        val before = viewModel.currentState
        val action = PendingCorporateAction(
            id = "corporate-action:test-opening-boundary",
            stockId = stock.id,
            kind = CorporateActionKind.REVERSE_SPLIT,
            announcedAt = Instant.parse("2026-08-07T00:00:00Z"),
            effectiveNotBefore = Instant.parse("2026-08-10T00:00:00Z"), // Monday 09:00 KST.
            quantityMultiplier = 0.1,
            source = CorporateActionSource.CAMPAIGN_RULE,
            rationale = "정규장 시작 경계 및 단주 현금정산 회귀 테스트",
        )
        assertTrue(viewModel.restoreGame(before.copy(pendingCorporateActions = listOf(action))))

        viewModel.advance(TurnStep.ONE_HOUR)

        val after = viewModel.currentState
        assertTrue(after.pendingCorporateActions.isNullOrEmpty())
        assertEquals(1, after.corporateActionLedger.orEmpty().size)
        assertEquals(
            round(stock.sharesOutstanding * action.quantityMultiplier).toLong(),
            after.stocks.single { it.id == stock.id }.sharesOutstanding,
        )
        assertEquals(
            MarketMicrostructure.roundNearest(
                stock.market,
                before.priceHistory.getValue(stock.id).first().close / action.quantityMultiplier,
            ),
            after.priceHistory.getValue(stock.id).first().close,
        )
        assertFalse(stock.id in after.holdings)
        assertTrue(after.trades.last().quantity in 0.299999..0.300001)
        assertEquals("주식병합 단주 현금정산", after.orders.last().rejectionReason)
        assertTrue(after.fifoCostBasisBook.lots.none { it.stockId == stock.id })

        val restored = SimulatorViewModel()
        assertTrue(restored.restoreGame(after))
        assertEquals(after.stocks, restored.currentState.stocks)
        assertEquals(after.trades, restored.currentState.trades)
        assertEquals(after.fifoCostBasisBook, restored.currentState.fifoCostBasisBook)
        assertEquals(after.realizedGains, restored.currentState.realizedGains)
    }

    private fun playingViewModel(seed: Long): SimulatorViewModel = SimulatorViewModel().apply {
        newGame(NewGameOptions(seed = seed))
    }

    private fun assertEquivalent(expected: SimulatorUiState, actual: SimulatorUiState) {
        assertEquals(expected.currentTime, actual.currentTime)
        assertEquals(expected.turn, actual.turn)
        assertEquals(expected.quotes, actual.quotes)
        assertEquals(expected.priceHistory, actual.priceHistory)
        assertEquals(expected.macro, actual.macro)
        assertEquals(expected.activeEvents, actual.activeEvents)
        assertEquals(expected.newsEvents, actual.newsEvents)
        assertEquals(expected.cashByCurrency, actual.cashByCurrency)
        assertEquals(expected.holdings, actual.holdings)
        assertEquals(expected.orders, actual.orders)
        assertEquals(expected.trades, actual.trades)
        assertEquals(expected.transactionCosts, actual.transactionCosts)
        assertEquals(expected.realizedGains, actual.realizedGains)
        assertEquals(expected.fifoCostBasisBook, actual.fifoCostBasisBook)
        assertEquals(expected.taxExchangeRatesByTradeId, actual.taxExchangeRatesByTradeId)
        assertEquals(expected.pendingTaxSettlementTradeIds, actual.pendingTaxSettlementTradeIds)
        assertEquals(expected.rngState, actual.rngState)
        assertEquals(expected.eventEngineSnapshot, actual.eventEngineSnapshot)
        assertEquals(expected.nextSequence, actual.nextSequence)
    }
}
