package com.amond.kmpbook.presentation

import com.amond.kmpbook.domain.data.StockCatalog
import com.amond.kmpbook.domain.model.CorporateActionKind
import com.amond.kmpbook.domain.model.CorporateActionSource
import com.amond.kmpbook.domain.model.DailyListingSurveillanceInput
import com.amond.kmpbook.domain.model.EventScope
import com.amond.kmpbook.domain.model.EventSeverity
import com.amond.kmpbook.domain.model.EventType
import com.amond.kmpbook.domain.model.GamePhase
import com.amond.kmpbook.domain.model.GameEvent
import com.amond.kmpbook.domain.model.GameEventImpact
import com.amond.kmpbook.domain.model.ImpactDirection
import com.amond.kmpbook.domain.model.ListingFinalDisposition
import com.amond.kmpbook.domain.model.ListingFinalDispositionType
import com.amond.kmpbook.domain.model.ListingLifecycleEventKind
import com.amond.kmpbook.domain.model.ListingLifecycleReason
import com.amond.kmpbook.domain.model.ListingLifecycleStatus
import com.amond.kmpbook.domain.model.ListingRiskTag
import com.amond.kmpbook.domain.model.OrderSide
import com.amond.kmpbook.domain.model.OrderType
import com.amond.kmpbook.domain.model.PendingCorporateAction
import com.amond.kmpbook.domain.model.TradeSettlementKind
import com.amond.kmpbook.domain.model.TurnStep
import com.amond.kmpbook.domain.simulation.ListingLifecycleEngine
import com.amond.kmpbook.domain.time.GameCalendar
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class ListingSettlementRuntimeInvariantTest {
    @Test
    fun frozenLiquidationEntitlementSurvivesDividendCorporateActionsAndRestoreThenPaysOnceAtNextClose() {
        val effectiveOn = LocalDate(2026, 8, 13)
        val weekendDueOn = LocalDate(2026, 8, 16)
        val nextTradingDate = LocalDate(2026, 8, 17)
        val viewModel = playingViewModel()
        val stock = viewModel.currentState.stocks.first { it.symbol == "WEEK" }
        assertTrue(stock.dividendYield > 0.0)

        val effectiveSession = assertNotNull(GameCalendar.regularSessionWindow(stock.market, effectiveOn))
        viewModel.setTimeForTesting(effectiveSession.opensAt + 30.minutes)
        assertTrue(viewModel.placeOrder(stock.id, OrderSide.BUY, OrderType.MARKET, 10.0))
        val purchasedHolding = assertNotNull(viewModel.currentState.holdings[stock.id])
        assertEquals(10.0, purchasedHolding.quantity)

        viewModel.setTimeForTesting(effectiveSession.closesAt - 1.hours)
        val beforeLiquidation = viewModel.currentState
        val scheduled = beforeLiquidation.listingLifecycleStates.getValue(stock.id).copy(
            status = ListingLifecycleStatus.DELISTING_SCHEDULED,
            activeReason = ListingLifecycleReason.ETF_VOLUNTARY_LIQUIDATION,
            designatedOn = LocalDate(2026, 8, 12),
            scheduledDelistingOn = effectiveOn,
            settlementDueOn = null,
            tradingAllowedUntilDelisting = true,
            finalDisposition = null,
            lastEvaluatedTradingDate = LocalDate(2026, 8, 12),
        )
        val liquidationNotice = GameEvent(
            id = "etf_liquidation_approved:runtime-invariant:${stock.id}",
            title = "ETF 청산 승인",
            description = "장 마감 기준가로 청산 권리를 확정합니다.",
            scope = EventScope.STOCK,
            type = EventType.FUND_OPERATION,
            severity = EventSeverity.MAJOR,
            impact = GameEventImpact(direction = ImpactDirection.NEUTRAL),
            startsAt = effectiveSession.closesAt - 24.hours,
            durationHours = 24,
            affectedMarkets = setOf(stock.market),
            affectedStockIds = setOf(stock.id),
            listingRiskTags = setOf(ListingRiskTag.ETF_LIQUIDATION_APPROVED),
            listingFinalDispositionHint = ListingFinalDispositionType.CASH_LIQUIDATION,
        )
        assertTrue(
            viewModel.restoreGame(
                beforeLiquidation.copy(
                    listingLifecycleStates = beforeLiquidation.listingLifecycleStates +
                        (stock.id to scheduled),
                    newsEvents = beforeLiquidation.newsEvents + liquidationNotice,
                ),
            ),
        )

        viewModel.advance(TurnStep.ONE_HOUR)

        val enteredPending = viewModel.currentState
        val pendingState = enteredPending.listingLifecycleStates.getValue(stock.id)
        assertEquals(ListingLifecycleStatus.LIQUIDATION_PENDING, pendingState.status)
        val frozen = assertNotNull(pendingState.finalDisposition)
        assertEquals(purchasedHolding.quantity, frozen.entitledQuantity)
        assertEquals(purchasedHolding.costBasis, frozen.entitledCostBasis)
        assertNotNull(frozen.cashPerUnit)

        val weekendDisposition = frozen.copy(settlementDueOn = weekendDueOn)
        val weekendPendingState = pendingState.copy(
            settlementDueOn = weekendDueOn,
            finalDisposition = weekendDisposition,
        )
        val FridaySession = assertNotNull(
            GameCalendar.regularSessionWindow(stock.market, LocalDate(2026, 8, 14)),
        )
        val actionIds = setOf("pending-forward-split", "pending-reverse-split")
        val actions = listOf(
            PendingCorporateAction(
                id = "pending-forward-split",
                stockId = stock.id,
                kind = CorporateActionKind.FORWARD_SPLIT,
                announcedAt = effectiveSession.closesAt,
                effectiveNotBefore = FridaySession.opensAt,
                quantityMultiplier = 2.0,
                source = CorporateActionSource.CAMPAIGN_RULE,
                rationale = "청산 권리 확정 뒤 분할 회귀 테스트",
            ),
            PendingCorporateAction(
                id = "pending-reverse-split",
                stockId = stock.id,
                kind = CorporateActionKind.REVERSE_SPLIT,
                announcedAt = effectiveSession.closesAt,
                effectiveNotBefore = FridaySession.opensAt + 1.hours,
                quantityMultiplier = 0.5,
                source = CorporateActionSource.CAMPAIGN_RULE,
                rationale = "청산 권리 확정 뒤 병합 회귀 테스트",
            ),
        )
        val adjustedLedger = enteredPending.listingLifecycleLedger.map { event ->
            if (event.stockId == stock.id && event.kind == ListingLifecycleEventKind.LIQUIDATION_STARTED) {
                event.copy(deadline = weekendDueOn, disposition = weekendDisposition)
            } else {
                event
            }
        }
        assertTrue(
            viewModel.restoreGame(
                enteredPending.copy(
                    listingLifecycleStates = enteredPending.listingLifecycleStates +
                        (stock.id to weekendPendingState),
                    listingLifecycleLedger = adjustedLedger,
                    pendingCorporateActions = enteredPending.pendingCorporateActions + actions,
                ),
            ),
        )

        val dividendCountBeforeFriday = viewModel.currentState.dividendLedger.count { it.stockId == stock.id }
        val fifoQuantityBeforeFriday = viewModel.currentState.fifoCostBasisBook.lots
            .filter { it.stockId == stock.id }
            .sumOf { it.remainingQuantity }
        viewModel.advance(TurnStep.ONE_DAY)

        val fridayClose = viewModel.currentState
        val holdingAfterFriday = assertNotNull(fridayClose.holdings[stock.id])
        val entitlementAfterFriday = assertNotNull(
            fridayClose.listingLifecycleStates.getValue(stock.id).finalDisposition,
        )
        assertEquals(purchasedHolding.quantity, holdingAfterFriday.quantity)
        assertEquals(purchasedHolding.averagePrice, holdingAfterFriday.averagePrice)
        assertEquals(purchasedHolding.costBasis, holdingAfterFriday.costBasis)
        assertEquals(frozen.entitledQuantity, entitlementAfterFriday.entitledQuantity)
        assertEquals(frozen.entitledCostBasis, entitlementAfterFriday.entitledCostBasis)
        assertEquals(dividendCountBeforeFriday, fridayClose.dividendLedger.count { it.stockId == stock.id })
        assertEquals(
            fifoQuantityBeforeFriday,
            fridayClose.fifoCostBasisBook.lots.filter { it.stockId == stock.id }.sumOf { it.remainingQuantity },
        )
        assertTrue(fridayClose.pendingCorporateActions.map { it.id }.containsAll(actionIds))
        assertFalse(fridayClose.corporateActionLedger.any { it.id in actionIds })

        val restored = SimulatorViewModel()
        assertTrue(restored.restoreGame(fridayClose))
        val restoredPending = restored.currentState
        assertEquals(holdingAfterFriday, restoredPending.holdings[stock.id])
        assertEquals(
            entitlementAfterFriday,
            restoredPending.listingLifecycleStates.getValue(stock.id).finalDisposition,
        )
        assertEquals(
            fridayClose.fifoCostBasisBook.lots.filter { it.stockId == stock.id },
            restoredPending.fifoCostBasisBook.lots.filter { it.stockId == stock.id },
        )

        restored.advance(TurnStep.ONE_DAY)
        restored.advance(TurnStep.ONE_DAY)
        assertEquals(
            ListingLifecycleStatus.LIQUIDATION_PENDING,
            restored.currentState.listingLifecycleStates.getValue(stock.id).status,
        )
        assertTrue(restored.currentState.trades.none { trade ->
            trade.stockId == stock.id && trade.settlementKind == TradeSettlementKind.CONTRACTUAL_CASH_SETTLEMENT
        })

        val mondaySession = assertNotNull(GameCalendar.regularSessionWindow(stock.market, nextTradingDate))
        while (restored.currentState.currentTime < mondaySession.closesAt - 1.hours) {
            restored.advance(TurnStep.ONE_HOUR)
        }
        assertEquals(mondaySession.closesAt - 1.hours, restored.currentState.currentTime)
        assertEquals(
            ListingLifecycleStatus.LIQUIDATION_PENDING,
            restored.currentState.listingLifecycleStates.getValue(stock.id).status,
        )
        val cashBeforePayment = restored.currentState.cashByCurrency.getValue(stock.currency)

        restored.advance(TurnStep.ONE_HOUR)

        val paid = restored.currentState
        assertEquals(mondaySession.closesAt, paid.currentTime)
        assertEquals(
            ListingLifecycleStatus.TERMINATED,
            paid.listingLifecycleStates.getValue(stock.id).status,
        )
        assertTrue(stock.id !in paid.holdings)
        val settlements = paid.trades.filter { trade ->
            trade.stockId == stock.id && trade.settlementKind == TradeSettlementKind.CONTRACTUAL_CASH_SETTLEMENT
        }
        assertEquals(1, settlements.size)
        assertEquals(frozen.entitledQuantity, settlements.single().quantity)
        assertEquals(frozen.cashPerUnit, settlements.single().price)
        assertEquals(nextTradingDate, settlements.single().settlementDateOverride)
        assertEquals(
            cashBeforePayment + requireNotNull(frozen.cashPerUnit) * requireNotNull(frozen.entitledQuantity),
            paid.cashByCurrency.getValue(stock.currency),
            0.01,
        )
        assertFalse(paid.corporateActionLedger.any { it.id in actionIds })
        assertTrue(paid.pendingCorporateActions.none { it.id in actionIds })

        val cashAfterFirstPayment = paid.cashByCurrency.getValue(stock.currency)
        restored.advance(TurnStep.ONE_DAY)
        val afterRetryWindow = restored.currentState
        assertEquals(cashAfterFirstPayment, afterRetryWindow.cashByCurrency.getValue(stock.currency))
        assertEquals(
            1,
            afterRetryWindow.trades.count { trade ->
                trade.stockId == stock.id &&
                    trade.settlementKind == TradeSettlementKind.CONTRACTUAL_CASH_SETTLEMENT
            },
        )
    }

    @Test
    fun gameEndKeepsPost2040LiquidationReceivableAndValuesItAtTheContractualUnitPrice() {
        val viewModel = playingViewModel()
        val stock = viewModel.currentState.stocks.first { it.symbol == "WEEK" }
        val purchaseSession = assertNotNull(
            GameCalendar.regularSessionWindow(stock.market, LocalDate(2026, 8, 13)),
        )
        viewModel.setTimeForTesting(purchaseSession.opensAt + 30.minutes)
        assertTrue(viewModel.placeOrder(stock.id, OrderSide.BUY, OrderType.MARKET, 10.0))
        val purchased = assertNotNull(viewModel.currentState.holdings[stock.id])

        viewModel.setTimeForTesting(GameCalendar.endInstant - 1.hours)
        val beforeInjection = viewModel.currentState
        val effectiveOn = LocalDate(2040, 12, 30)
        val settlementDueOn = LocalDate(2041, 1, 7)
        val cashPerUnit = 123.45
        val stalePrice = 17.89
        assertTrue(stalePrice != cashPerUnit)
        val disposition = ListingFinalDisposition(
            type = ListingFinalDispositionType.CASH_LIQUIDATION,
            effectiveOn = effectiveOn,
            settlementDueOn = settlementDueOn,
            cashPerUnit = cashPerUnit,
            entitledQuantity = purchased.quantity,
            entitledCostBasis = purchased.costBasis,
        )
        val pending = beforeInjection.listingLifecycleStates.getValue(stock.id).copy(
            status = ListingLifecycleStatus.LIQUIDATION_PENDING,
            activeReason = ListingLifecycleReason.ETF_VOLUNTARY_LIQUIDATION,
            designatedOn = effectiveOn,
            scheduledDelistingOn = effectiveOn,
            settlementDueOn = settlementDueOn,
            tradingAllowedUntilDelisting = false,
            finalDisposition = disposition,
            lastEvaluatedTradingDate = effectiveOn,
        )
        val staleHolding = purchased.copy(currentPrice = stalePrice)
        assertTrue(
            viewModel.restoreGame(
                beforeInjection.copy(
                    holdings = beforeInjection.holdings + (stock.id to staleHolding),
                    listingLifecycleStates = beforeInjection.listingLifecycleStates +
                        (stock.id to pending),
                ),
            ),
        )
        assertEquals(cashPerUnit, viewModel.currentState.holdings.getValue(stock.id).currentPrice)
        val sellTradesBeforeEnd = viewModel.currentState.trades.count { it.side == OrderSide.SELL }
        val realizedBeforeEnd = viewModel.currentState.realizedGains.size

        viewModel.advance(TurnStep.ONE_HOUR)

        val ended = viewModel.currentState
        assertEquals(GameCalendar.endInstant, ended.currentTime)
        assertEquals(GamePhase.SETTLEMENT, ended.phase)
        assertEquals(
            ListingLifecycleStatus.LIQUIDATION_PENDING,
            ended.listingLifecycleStates.getValue(stock.id).status,
        )
        val holdingAtEnd = assertNotNull(ended.holdings[stock.id])
        val dispositionAtEnd = assertNotNull(
            ended.listingLifecycleStates.getValue(stock.id).finalDisposition,
        )
        assertEquals(purchased.quantity, holdingAtEnd.quantity)
        assertEquals(cashPerUnit, holdingAtEnd.currentPrice)
        assertEquals(purchased.quantity, dispositionAtEnd.entitledQuantity)
        assertEquals(purchased.costBasis, dispositionAtEnd.entitledCostBasis)
        assertEquals(settlementDueOn, dispositionAtEnd.settlementDueOn)
        assertFalse(ended.listingLifecycleStates.getValue(stock.id).isTerminal)
        assertEquals(
            sellTradesBeforeEnd,
            ended.trades.count { it.side == OrderSide.SELL },
        )
        assertEquals(realizedBeforeEnd, ended.realizedGains.size)
        assertTrue(ended.realizedGains.none { it.stockId == stock.id && it.settlementDate.year == 2040 })
        val tax2040 = assertNotNull(ended.annualTaxLedgers[2040])
        assertEquals(0L, tax2040.foreignGainKrw)
        assertEquals(0L, tax2040.stockTaxableBaseKrw)
        assertEquals(0L, tax2040.totalPayableKrw)
        assertTrue(ended.taxPaymentNotices.none { it.taxYear == 2040 && it.amountKrw > 0L })

        val expectedReceivableValueKrw = purchased.quantity * cashPerUnit * ended.macro.usdKrw
        assertEquals(expectedReceivableValueKrw, ended.currentPortfolio.stockValueKrw, 0.01)
        val finalSnapshot = ended.portfolioSnapshots.last()
        assertEquals(GameCalendar.endInstant, finalSnapshot.timestamp)
        assertEquals(expectedReceivableValueKrw, finalSnapshot.stockValueKrw, 0.01)
        assertEquals(cashPerUnit, finalSnapshot.holdings.single { it.stockId == stock.id }.currentPrice)

        val staleFinalSave = ended.copy(
            holdings = ended.holdings + (stock.id to holdingAtEnd.copy(currentPrice = stalePrice)),
        )
        val reloaded = SimulatorViewModel()
        assertTrue(reloaded.restoreGame(staleFinalSave))
        val reloadedState = reloaded.currentState
        assertEquals(cashPerUnit, reloadedState.holdings.getValue(stock.id).currentPrice)
        assertEquals(
            ListingLifecycleStatus.LIQUIDATION_PENDING,
            reloadedState.listingLifecycleStates.getValue(stock.id).status,
        )
        assertEquals(expectedReceivableValueKrw, reloadedState.currentPortfolio.stockValueKrw, 0.01)
        assertEquals(sellTradesBeforeEnd, reloadedState.trades.count { it.side == OrderSide.SELL })
        assertEquals(realizedBeforeEnd, reloadedState.realizedGains.size)
    }

    @Test
    fun scheduledCashLiquidationDoesNotEnterPendingWithoutAConfirmedUnitPrice() {
        val stock = StockCatalog.all.first { it.symbol == "WEEK" }
        val effectiveOn = LocalDate(2026, 8, 14)
        val engine = ListingLifecycleEngine()
        val scheduled = engine.initialState(stock).copy(
            status = ListingLifecycleStatus.DELISTING_SCHEDULED,
            activeReason = ListingLifecycleReason.ETF_VOLUNTARY_LIQUIDATION,
            designatedOn = LocalDate(2026, 8, 13),
            scheduledDelistingOn = effectiveOn,
            tradingAllowedUntilDelisting = false,
        )

        val result = engine.evaluate(
            scheduled,
            DailyListingSurveillanceInput(
                stockId = stock.id,
                tradingDate = effectiveOn,
                riskTags = setOf(ListingRiskTag.ETF_LIQUIDATION_APPROVED),
                finalDispositionHint = ListingFinalDispositionType.CASH_LIQUIDATION,
                liquidationCashPerUnit = null,
            ),
        )

        assertEquals(ListingLifecycleStatus.DELISTING_SCHEDULED, result.state.status)
        assertNull(result.state.finalDisposition)
        assertNull(result.state.settlementDueOn)
        assertTrue(result.ledgerEvents.isEmpty())
    }

    private fun playingViewModel(): SimulatorViewModel = SimulatorViewModel().apply {
        newGame(NewGameOptions(seed = 81_600L))
    }
}
