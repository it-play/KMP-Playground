package com.amond.kmpbook.presentation

import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.GamePhase
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.OrderSide
import com.amond.kmpbook.domain.model.OrderStatus
import com.amond.kmpbook.domain.model.OrderType
import com.amond.kmpbook.domain.model.Screen
import com.amond.kmpbook.domain.model.TimeInForce
import com.amond.kmpbook.domain.model.TurnStep
import com.amond.kmpbook.domain.tax.TaxLiability
import com.amond.kmpbook.domain.tax.TaxLiabilityStatus
import com.amond.kmpbook.domain.time.GameCalendar
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.math.round
import kotlin.time.Duration.Companion.hours
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
        assertEquals(32, state.stocks.size)
        assertEquals(32, state.quotes.size)
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
        assertEquals(expected.rngState, actual.rngState)
        assertEquals(expected.eventEngineSnapshot, actual.eventEngineSnapshot)
        assertEquals(expected.nextSequence, actual.nextSequence)
    }
}
