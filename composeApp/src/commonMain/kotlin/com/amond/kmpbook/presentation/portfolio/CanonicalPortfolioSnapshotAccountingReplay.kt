package com.amond.kmpbook.presentation.portfolio

import com.amond.kmpbook.domain.model.corporateaction.CorporateActionRecord
import com.amond.kmpbook.domain.model.instrument.DistributionEntitlementOrigin
import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.instrument.grossReceivableAmount
import com.amond.kmpbook.domain.model.market.Currency
import com.amond.kmpbook.domain.model.portfolio.PortfolioSnapshot
import com.amond.kmpbook.domain.model.trading.Order
import com.amond.kmpbook.domain.model.trading.OrderSide
import com.amond.kmpbook.domain.model.trading.Trade
import com.amond.kmpbook.domain.model.trading.TradeSettlementKind
import com.amond.kmpbook.domain.tax.liability.AccountingObservationBoundary
import com.amond.kmpbook.domain.tax.liability.TaxLiabilityStatus
import com.amond.kmpbook.domain.tax.dividend.DistributionReturnOfCapitalPolicy
import com.amond.kmpbook.domain.time.GameCalendar
import com.amond.kmpbook.domain.time.SecuritiesSettlementCalendar
import kotlin.math.round
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant

/**
 * Replays every persisted portfolio observation with one chronological sweep. Historical price
 * marks and USD/KRW marks remain bounded observed valuation facts; cash, native holdings, FIFO,
 * receivables, and cumulative accounting values are derived from durable accounting events.
 *
 * U.S. exchange trades use execution FX until their T+1 settlement midnight. The settlement is a
 * temporal event without a global accounting sequence, so it is applied before same-instant ex/pay
 * accounting, matching the runtime boundary order.
 */
object CanonicalPortfolioSnapshotAccountingReplay {
    data class Fact(
        val cashByCurrency: Map<Currency, Double>,
        val nativeHoldingsByStockId: Map<String, CanonicalTaxAccountingReplay.NativeHoldingFact>,
        val holdingCostBasisKrw: Map<String, Double>,
        val distributionReceivableByCurrency: Map<Currency, Double>,
        val realizedProfitKrw: Double,
        val cumulativeCommissionKrw: Double,
        val cumulativeTaxKrw: Double,
    )

    fun replay(
        stocksById: Map<String, StockDefinition>,
        initialCapitalKrw: Double,
        campaignSeed: Long,
        currentTime: Instant,
        orders: List<Order>,
        trades: List<Trade>,
        transactionCosts: List<TransactionCostRecord>,
        taxExchangeRatesByTradeId: Map<String, Double>,
        corporateActions: List<CorporateActionRecord>,
        distributionOrigins: List<DistributionEntitlementOrigin>,
        dividendEntries: List<DividendLedgerEntry>,
        taxPaymentNotices: List<TaxPaymentNotice>,
        foreignExchanges: List<ForeignExchangeRecord>,
        cashAdjustments: List<CashAdjustmentRecord>,
        portfolioSnapshots: List<PortfolioSnapshot>,
    ): Map<AccountingObservationBoundary, Fact> {
        require(orders.map(Order::id).distinct().size == orders.size)
        val tradesById = trades.associateBy(Trade::id)
        val costsByTradeId = transactionCosts.associateBy(TransactionCostRecord::tradeId)
        val actionsById = corporateActions.associateBy(CorporateActionRecord::id)
        val originsById = distributionOrigins.associateBy(DistributionEntitlementOrigin::id)
        val dividendsById = dividendEntries.associateBy(DividendLedgerEntry::id)
        val exchangesById = foreignExchanges.associateBy(ForeignExchangeRecord::id)
        val paidNoticesById = taxPaymentNotices.asSequence()
            .filter { notice -> notice.status == TaxLiabilityStatus.PAID }
            .associateBy(TaxPaymentNotice::id)
        val adjustmentsById = cashAdjustments.associateBy(CashAdjustmentRecord::id)
        require(tradesById.size == trades.size && costsByTradeId.size == transactionCosts.size &&
            costsByTradeId.keys == tradesById.keys && actionsById.size == corporateActions.size &&
            originsById.size == distributionOrigins.size && dividendsById.size == dividendEntries.size &&
            exchangesById.size == foreignExchanges.size &&
            adjustmentsById.size == cashAdjustments.size
        )
        require(transactionCosts.map(TransactionCostRecord::tradeId) == trades.map(Trade::id) &&
            trades.zipWithNext().all { (left, right) ->
                left.accountingSequence < right.accountingSequence
            } && dividendEntries.zipWithNext().all { (left, right) ->
                left.accountingSequence < right.accountingSequence
            } && paidNoticesById.values.zipWithNext().all { (left, right) ->
                requireNotNull(left.accountingSequence) < requireNotNull(right.accountingSequence)
            }
        ) { "Persisted accounting categories must retain their runtime append order." }
        require(taxExchangeRatesByTradeId.keys == tradesById.keys)
        val pendingSettlementIds = canonicalPendingTaxSettlementTradeIds(
            trades = trades,
            stocksById = stocksById,
            currentTime = currentTime,
        )
        require(
            pendingTaxSettlementRatesMatchExecutionFacts(
                pendingTradeIds = pendingSettlementIds,
                transactionCosts = transactionCosts,
                taxExchangeRatesByTradeId = taxExchangeRatesByTradeId,
            ),
        )

        val originByDistributionKey = distributionOrigins.associateBy { origin ->
            origin.stockId to origin.exDate
        }
        require(originByDistributionKey.size == distributionOrigins.size)
        val accountingEvents = buildList {
            trades.forEach { trade ->
                add(Event(trade.accountingSequence, trade.executedAt, Kind.TRADE, trade.id))
            }
            corporateActions.forEach { action ->
                add(Event(action.accountingSequence, action.effectiveAt, Kind.CORPORATE_ACTION, action.id))
            }
            distributionOrigins.forEach { origin ->
                add(Event(origin.accountingSequence, origin.establishedAt, Kind.DISTRIBUTION_ORIGIN, origin.id))
            }
            dividendEntries.forEach { dividend ->
                add(Event(dividend.accountingSequence, dividend.paidAt, Kind.DIVIDEND, dividend.id))
            }
            foreignExchanges.forEach { exchange ->
                add(Event(exchange.accountingSequence, exchange.executedAt, Kind.FX, exchange.id))
            }
            paidNoticesById.values.forEach { notice ->
                add(
                    Event(
                        requireNotNull(notice.accountingSequence),
                        requireNotNull(notice.paidAt),
                        Kind.TAX_PAYMENT,
                        notice.id,
                    ),
                )
            }
            cashAdjustments.forEach { adjustment ->
                add(Event(adjustment.accountingSequence, adjustment.adjustedAt, Kind.DEBUG_ADJUSTMENT, adjustment.id))
            }
        }.sortedBy(Event::accountingSequence)
        require(accountingEvents.map(Event::accountingSequence).distinct().size == accountingEvents.size)
        require(accountingEvents.all { event -> event.occurredAt in GameCalendar.startInstant..currentTime } &&
            accountingEvents.zipWithNext().all { (left, right) -> left.occurredAt <= right.occurredAt }
        )

        val settlementEvents = trades.asSequence()
            .filter { trade ->
                trade.settlementKind == TradeSettlementKind.EXCHANGE_TRADE &&
                    stocksById.getValue(trade.stockId).market.isUnitedStates
            }
            .map { trade ->
                val stock = stocksById.getValue(trade.stockId)
                val settlementDate = SecuritiesSettlementCalendar.settlementDate(
                    stock.market,
                    GameCalendar.marketLocalDateTime(stock.market, trade.executedAt).date,
                )
                SettlementEvent(
                    tradeId = trade.id,
                    settledAt = LocalDateTime(settlementDate, LocalTime(0, 0))
                        .toInstant(GameCalendar.timeZoneFor(stock.market)),
                )
            }
            .sortedWith(
                compareBy(SettlementEvent::settledAt)
                    .thenBy { event -> tradesById.getValue(event.tradeId).accountingSequence },
            )
            .toList()
        val boundaries = portfolioSnapshots.map { snapshot ->
            AccountingObservationBoundary(
                snapshot.timestamp,
                snapshot.accountingSequenceExclusiveUpperBound,
            )
        }
        require(boundaries.distinct().size == boundaries.size)
        require(boundaries.zipWithNext().all { (left, right) -> compareBoundaries(left, right) < 0 })

        val accumulator = Accumulator(
            stocksById = stocksById,
            initialCapitalKrw = initialCapitalKrw,
            campaignSeed = campaignSeed,
            tradesById = tradesById,
            costsByTradeId = costsByTradeId,
            taxExchangeRatesByTradeId = taxExchangeRatesByTradeId,
            actionsById = actionsById,
            originsById = originsById,
            originByDistributionKey = originByDistributionKey,
            dividendsById = dividendsById,
            exchangesById = exchangesById,
            paidNoticesById = paidNoticesById,
            adjustmentsById = adjustmentsById,
        )
        var accountingIndex = 0
        var settlementIndex = 0
        return buildMap {
            boundaries.forEach { boundary ->
                while (true) {
                    val accounting = accountingEvents.getOrNull(accountingIndex)
                        ?.takeIf { event ->
                            boundary.includes(event.occurredAt, event.accountingSequence)
                        }
                    val settlement = settlementEvents.getOrNull(settlementIndex)
                        ?.takeIf { event -> event.settledAt <= boundary.timestamp }
                    if (accounting == null && settlement == null) break
                    if (settlement != null &&
                        (accounting == null || settlement.settledAt <= accounting.occurredAt)
                    ) {
                        accumulator.applySettlement(settlement)
                        settlementIndex += 1
                    } else {
                        accumulator.applyAccounting(requireNotNull(accounting))
                        accountingIndex += 1
                    }
                }
                put(boundary, accumulator.fact())
            }
        }
    }

    private class Accumulator(
        private val stocksById: Map<String, StockDefinition>,
        initialCapitalKrw: Double,
        private val campaignSeed: Long,
        private val tradesById: Map<String, Trade>,
        private val costsByTradeId: Map<String, TransactionCostRecord>,
        private val taxExchangeRatesByTradeId: Map<String, Double>,
        private val actionsById: Map<String, CorporateActionRecord>,
        private val originsById: Map<String, DistributionEntitlementOrigin>,
        private val originByDistributionKey: Map<Pair<String, LocalDate>, DistributionEntitlementOrigin>,
        private val dividendsById: Map<String, DividendLedgerEntry>,
        private val exchangesById: Map<String, ForeignExchangeRecord>,
        private val paidNoticesById: Map<String, TaxPaymentNotice>,
        private val adjustmentsById: Map<String, CashAdjustmentRecord>,
    ) {
        private val nativePositions = linkedMapOf<String, NativePosition>()
        private val lotsByStockId = linkedMapOf<String, ArrayDeque<MutableTaxLot>>()
        /**
         * Only U.S. BUY lots awaiting their one T+1 FX rebase need mutation provenance. The entry
         * is removed at settlement so long-held settled lots do not accumulate an unbounded log.
         */
        private val unsettledPurchaseLotsByTradeId = linkedMapOf<String, MutableTaxLot>()
        private val holdingCostBasisKrw = linkedMapOf<String, Long>()
        private val cash = Currency.entries.associateWithTo(linkedMapOf()) { currency ->
            if (currency == Currency.KRW) roundCurrencyForAccounting(initialCapitalKrw, currency)
            else 0.0
        }
        private val pendingReceivablesByOriginId = linkedMapOf<String, Pair<Currency, Double>>()
        private val realizedTaxGainByTradeId = linkedMapOf<String, Long>()
        private val realizedTaxCostBasisByTradeId = linkedMapOf<String, Long>()
        private var realizedTaxGainTotalKrw = 0L
        private var cumulativeCommissionKrw = 0.0
        private var cumulativeSaleTaxKrw = 0.0
        private var cumulativeDividendWithholdingTaxKrw = 0.0
        private var cumulativePaidAnnualTaxKrw = 0.0

        fun applyAccounting(event: Event) {
            when (event.kind) {
                Kind.TRADE -> applyTrade(tradesById.getValue(event.id))
                Kind.CORPORATE_ACTION -> applyCorporateAction(actionsById.getValue(event.id))
                Kind.DISTRIBUTION_ORIGIN -> applyDistributionOrigin(originsById.getValue(event.id))
                Kind.DIVIDEND -> applyDividend(dividendsById.getValue(event.id))
                Kind.FX -> applyForeignExchange(exchangesById.getValue(event.id))
                Kind.TAX_PAYMENT -> applyTaxPayment(paidNoticesById.getValue(event.id))
                Kind.DEBUG_ADJUSTMENT -> applyCashAdjustment(adjustmentsById.getValue(event.id))
            }
        }

        fun applySettlement(event: SettlementEvent) {
            val trade = tradesById.getValue(event.tradeId)
            val stock = stocksById.getValue(trade.stockId)
            val executionRate = costsByTradeId.getValue(trade.id).exchangeRateToKrw
            val settlementRate = taxExchangeRatesByTradeId.getValue(trade.id)
            val gross = canonicalTradeGrossCash(trade)
            if (trade.side == OrderSide.BUY) {
                val lot = unsettledPurchaseLotsByTradeId.remove(trade.id)
                    ?: error("미결제 미국 매수 lot provenance가 없습니다: ${trade.id}")
                if (executionRate.toBits() == settlementRate.toBits()) {
                    lot.basisMutations.clear()
                    return
                }
                val newBasis = round(gross * settlementRate).toLong() +
                    round(trade.commission * settlementRate).toLong()
                var remainingQuantity = lot.purchaseQuantity
                var remainingBasis = newBasis
                lot.basisMutations.forEach { mutation ->
                    when (mutation) {
                        is LotBasisMutation.QuantityMultiplier -> {
                            remainingQuantity *= mutation.multiplier
                        }
                        is LotBasisMutation.ReturnOfCapital -> {
                            val nextReduction = minOf(remainingBasis, mutation.allocatedRocKrw)
                            mutation.appliedReductionKrw = nextReduction
                            remainingBasis -= nextReduction
                        }
                        is LotBasisMutation.Sale -> {
                            val consumesEntireLot = kotlin.math.abs(
                                mutation.quantity - remainingQuantity,
                            ) <= QUANTITY_EPSILON
                            val nextAllocation = if (consumesEntireLot) {
                                remainingBasis
                            } else {
                                kotlin.math.floor(
                                    remainingBasis.toDouble() * mutation.quantity /
                                        remainingQuantity,
                                ).toLong()
                            }
                            val allocationDelta = nextAllocation - mutation.allocatedBasisKrw
                            if (allocationDelta != 0L) {
                                val sellTradeId = mutation.sellTradeId
                                realizedTaxCostBasisByTradeId[sellTradeId] =
                                    realizedTaxCostBasisByTradeId.getValue(sellTradeId) +
                                    allocationDelta
                                val oldGain = realizedTaxGainByTradeId.getValue(sellTradeId)
                                val nextGain = CanonicalPortfolioAccountingTotals
                                    .checkedTaxGainKrwSubtract(oldGain, allocationDelta)
                                replaceRealizedTaxGain(sellTradeId, oldGain, nextGain)
                                mutation.allocatedBasisKrw = nextAllocation
                            }
                            remainingQuantity =
                                (remainingQuantity - mutation.quantity).coerceAtLeast(0.0)
                            remainingBasis -= nextAllocation
                        }
                    }
                }
                require(kotlin.math.abs(remainingQuantity - lot.remainingQuantity) <= QUANTITY_EPSILON)
                require(remainingBasis >= 0L)
                val remainingDelta = remainingBasis - lot.remainingCostBasisKrw
                lot.remainingCostBasisKrw = remainingBasis
                lot.initialCostBasisKrw = newBasis
                if (lot.remainingQuantity > QUANTITY_EPSILON) {
                    holdingCostBasisKrw[stock.id] =
                        holdingCostBasisKrw.getValue(stock.id) + remainingDelta
                }
                lot.basisMutations.clear()
            } else {
                if (executionRate.toBits() == settlementRate.toBits()) return
                val oldGain = realizedTaxGainByTradeId.getValue(trade.id)
                val nextGain = round(gross * settlementRate).toLong() -
                    realizedTaxCostBasisByTradeId.getValue(trade.id) -
                    round((trade.commission + trade.tax) * settlementRate).toLong()
                replaceRealizedTaxGain(trade.id, oldGain, nextGain)
            }
        }

        fun fact(): Fact {
            val receivables = pendingReceivablesByOriginId.values
                .groupBy(Pair<Currency, Double>::first)
                .mapValues { (_, values) -> values.sumOf(Pair<Currency, Double>::second) }
            return Fact(
                cashByCurrency = cash.toMap(),
                nativeHoldingsByStockId = nativePositions.mapValuesTo(linkedMapOf()) { (stockId, position) ->
                    CanonicalTaxAccountingReplay.NativeHoldingFact(
                        stockId = stockId,
                        quantity = position.quantity,
                        averagePrice = position.averagePrice,
                        currency = stocksById.getValue(stockId).currency,
                        realizedProfit = position.realizedProfit,
                    )
                },
                holdingCostBasisKrw = holdingCostBasisKrw.mapValues { (_, value) -> value.toDouble() },
                distributionReceivableByCurrency = receivables,
                realizedProfitKrw = realizedTaxGainTotalKrw.toDouble(),
                cumulativeCommissionKrw = cumulativeCommissionKrw,
                cumulativeTaxKrw = CanonicalPortfolioAccountingTotals
                    .combineCumulativeTaxCategorySums(
                        saleTaxKrw = cumulativeSaleTaxKrw,
                        dividendWithholdingTaxKrw = cumulativeDividendWithholdingTaxKrw,
                        paidAnnualTaxKrw = cumulativePaidAnnualTaxKrw,
                    ),
            )
        }

        private fun applyTrade(trade: Trade) {
            val stock = stocksById.getValue(trade.stockId)
            val cost = costsByTradeId.getValue(trade.id)
            val gross = canonicalTradeGrossCash(trade)
            val taxRateAtExecution = if (
                trade.settlementKind == TradeSettlementKind.EXCHANGE_TRADE &&
                stock.market.isUnitedStates
            ) cost.exchangeRateToKrw else taxExchangeRatesByTradeId.getValue(trade.id)
            cash[trade.currency] = tradeCashBalanceAfter(
                currentBalance = cash.getValue(trade.currency),
                side = trade.side,
                grossCash = gross,
                commission = trade.commission,
                saleTax = trade.tax,
                currency = trade.currency,
            )
            cumulativeCommissionKrw += cost.commissionKrw
            cumulativeSaleTaxKrw += cost.saleTaxKrw
            val previous = nativePositions[stock.id]
            val settledOn = when (trade.settlementKind) {
                TradeSettlementKind.EXCHANGE_TRADE -> SecuritiesSettlementCalendar.settlementDate(
                    stock.market,
                    GameCalendar.marketLocalDateTime(stock.market, trade.executedAt).date,
                )
                TradeSettlementKind.CONTRACTUAL_CASH_SETTLEMENT ->
                    requireNotNull(trade.settlementDateOverride)
            }
            if (trade.side == OrderSide.BUY) {
                val nextQuantity = (previous?.quantity ?: 0.0) + trade.quantity
                val nextCost = (previous?.costBasis ?: 0.0) + gross + trade.commission
                nativePositions[stock.id] = NativePosition(
                    quantity = nextQuantity,
                    averagePrice = nextCost / nextQuantity,
                    realizedProfit = previous?.realizedProfit ?: 0.0,
                )
                val purchase = round(gross * taxRateAtExecution).toLong()
                val directCosts = round(trade.commission * taxRateAtExecution).toLong()
                val lot = MutableTaxLot(
                    lotId = trade.id,
                    stockId = stock.id,
                    acquiredOn = settledOn,
                    purchaseQuantity = trade.quantity,
                    remainingQuantity = trade.quantity,
                    initialCostBasisKrw = purchase + directCosts,
                    remainingCostBasisKrw = purchase + directCosts,
                )
                lotsByStockId.getOrPut(stock.id) { ArrayDeque() }.addLast(lot)
                if (
                    trade.settlementKind == TradeSettlementKind.EXCHANGE_TRADE &&
                    stock.market.isUnitedStates
                ) {
                    check(unsettledPurchaseLotsByTradeId.put(trade.id, lot) == null)
                }
                holdingCostBasisKrw[stock.id] =
                    holdingCostBasisKrw.getOrElse(stock.id) { 0L } + purchase + directCosts
            } else {
                val requiredPrevious = requireNotNull(previous)
                require(requiredPrevious.quantity + QUANTITY_EPSILON >= trade.quantity)
                val nativeCostBasis = requiredPrevious.averagePrice * trade.quantity
                var remainingToSell = trade.quantity
                var allocatedCostBasisKrw = 0L
                val lots = lotsByStockId.getOrPut(stock.id) { ArrayDeque() }
                while (remainingToSell > QUANTITY_EPSILON) {
                    val lot = lots.firstOrNull()
                        ?: error("매도 전에 재생 가능한 FIFO lot이 없습니다: ${trade.id}")
                    require(lot.acquiredOn <= settledOn)
                    val usedQuantity = minOf(remainingToSell, lot.remainingQuantity)
                    val consumesEntireLot =
                        kotlin.math.abs(usedQuantity - lot.remainingQuantity) <= QUANTITY_EPSILON
                    val usedBasis = if (consumesEntireLot) {
                        lot.remainingCostBasisKrw
                    } else {
                        kotlin.math.floor(
                            lot.remainingCostBasisKrw.toDouble() * usedQuantity /
                                lot.remainingQuantity,
                        ).toLong()
                    }
                    if (unsettledPurchaseLotsByTradeId[lot.lotId] === lot) {
                        lot.basisMutations += LotBasisMutation.Sale(
                            sellTradeId = trade.id,
                            quantity = usedQuantity,
                            allocatedBasisKrw = usedBasis,
                        )
                    }
                    lot.remainingQuantity =
                        (lot.remainingQuantity - usedQuantity).coerceAtLeast(0.0)
                    lot.remainingCostBasisKrw -= usedBasis
                    allocatedCostBasisKrw += usedBasis
                    remainingToSell -= usedQuantity
                    if (consumesEntireLot) lots.removeFirst()
                }
                require(remainingToSell <= QUANTITY_EPSILON)
                val taxGainKrw = round(gross * taxRateAtExecution).toLong() -
                    allocatedCostBasisKrw -
                    round((trade.commission + trade.tax) * taxRateAtExecution).toLong()
                realizedTaxCostBasisByTradeId[trade.id] = allocatedCostBasisKrw
                realizedTaxGainByTradeId[trade.id] = taxGainKrw
                realizedTaxGainTotalKrw = CanonicalPortfolioAccountingTotals
                    .checkedTaxGainKrwAdd(realizedTaxGainTotalKrw, taxGainKrw)
                val remaining = (requiredPrevious.quantity - trade.quantity).coerceAtLeast(0.0)
                if (remaining < stock.quantityStep / 2.0) {
                    nativePositions.remove(stock.id)
                    holdingCostBasisKrw.remove(stock.id)
                } else {
                    nativePositions[stock.id] = requiredPrevious.copy(
                        quantity = remaining,
                        realizedProfit = requiredPrevious.realizedProfit +
                            gross - nativeCostBasis - trade.commission - trade.tax,
                    )
                    holdingCostBasisKrw[stock.id] =
                        holdingCostBasisKrw.getValue(stock.id) - allocatedCostBasisKrw
                }
            }
        }

        private fun applyCorporateAction(action: CorporateActionRecord) {
            lotsByStockId[action.stockId].orEmpty().forEach { lot ->
                if (unsettledPurchaseLotsByTradeId[lot.lotId] === lot) {
                    lot.basisMutations += LotBasisMutation.QuantityMultiplier(
                        action.quantityMultiplier,
                    )
                }
                lot.remainingQuantity *= action.quantityMultiplier
            }
            nativePositions[action.stockId]?.let { previous ->
                nativePositions[action.stockId] = previous.copy(
                    quantity = previous.quantity * action.quantityMultiplier,
                    averagePrice = previous.averagePrice / action.quantityMultiplier,
                )
            }
        }

        private fun applyDistributionOrigin(origin: DistributionEntitlementOrigin) {
            val stock = stocksById.getValue(origin.stockId)
            require(
                origin.taxableCoverageRatio.toBits() ==
                    DistributionReturnOfCapitalPolicy.modeledTaxableCoverageRatio(stock).toBits(),
            )
            require(
                DistributionReturnOfCapitalPolicy.isEligible(stock) ||
                    origin.returnOfCapitalAmount.toBits() == 0.0.toBits(),
            )
            pendingReceivablesByOriginId[origin.id] = stock.currency to grossReceivableAmount(
                currency = stock.currency,
                grossPerUnit = origin.grossPerUnit,
                entitledQuantity = origin.entitledQuantity,
            )
            val amountKrw = round(
                origin.returnOfCapitalAmount * origin.taxBasisExchangeRateToKrw,
            ).toLong()
            applyReturnOfCapital(origin.stockId, amountKrw, origin.id)
        }

        private fun applyDividend(dividend: DividendLedgerEntry) {
            applyCashDelta(dividend.currency, dividend.netAmount)
            cumulativeDividendWithholdingTaxKrw += dividend.withholdingTaxKrw
            originByDistributionKey[dividend.stockId to dividend.exDate]?.let { origin ->
                pendingReceivablesByOriginId.remove(origin.id)
            } ?: run {
                if (dividend.returnOfCapitalAmount > 0.0) {
                    require(
                        DistributionReturnOfCapitalPolicy.isEligible(
                            stocksById.getValue(dividend.stockId),
                        ),
                    )
                    val amountKrw = round(
                        dividend.returnOfCapitalAmount * dividend.exchangeRateToKrw,
                    ).toLong()
                    applyReturnOfCapital(dividend.stockId, amountKrw, dividend.id)
                }
            }
        }

        private fun applyReturnOfCapital(
            stockId: String,
            amountKrw: Long,
            sourceId: String,
        ) {
            if (amountKrw == 0L) return
            val lots = lotsByStockId[stockId].orEmpty()
            if (lots.isEmpty()) return
            val totalQuantity = lots.sumOf(MutableTaxLot::remainingQuantity)
            require(totalQuantity > 0.0)
            var allocated = 0L
            var reduction = 0L
            lots.forEachIndexed { index, lot ->
                val lotRoc = if (index == lots.lastIndex) {
                    amountKrw - allocated
                } else {
                    kotlin.math.floor(
                        amountKrw.toDouble() * lot.remainingQuantity / totalQuantity,
                    ).toLong()
                }
                allocated += lotRoc
                val lotReduction = minOf(lot.remainingCostBasisKrw, lotRoc)
                lot.remainingCostBasisKrw -= lotReduction
                reduction += lotReduction
                if (unsettledPurchaseLotsByTradeId[lot.lotId] === lot) {
                    lot.basisMutations += LotBasisMutation.ReturnOfCapital(
                        sourceId = sourceId,
                        allocatedRocKrw = lotRoc,
                        appliedReductionKrw = lotReduction,
                    )
                }
            }
            check(allocated == amountKrw)
            holdingCostBasisKrw[stockId]?.let { previous ->
                holdingCostBasisKrw[stockId] = previous - reduction
            }
        }

        private fun applyForeignExchange(exchange: ForeignExchangeRecord) {
            require(exchange.id == "fx-$campaignSeed-${exchange.accountingSequence}")
            applyCashDelta(exchange.fromCurrency, -exchange.sourceAmount)
            applyCashDelta(exchange.toCurrency, exchange.receivedAmount)
        }

        private fun applyTaxPayment(notice: TaxPaymentNotice) {
            applyCashDelta(Currency.KRW, -notice.amountKrw.toDouble())
            cumulativePaidAnnualTaxKrw += notice.amountKrw.toDouble()
        }

        private fun applyCashAdjustment(adjustment: CashAdjustmentRecord) {
            require(cash.getValue(adjustment.currency).toBits() == adjustment.balanceBefore.toBits())
            cash[adjustment.currency] = roundCurrencyForAccounting(
                adjustment.balanceAfter,
                adjustment.currency,
            )
        }

        private fun applyCashDelta(currency: Currency, delta: Double) {
            require(delta.isFinite())
            val next = roundCurrencyForAccounting(cash.getValue(currency) + delta, currency)
            require(next >= 0.0)
            cash[currency] = if (next == -0.0) 0.0 else next
        }

        private fun replaceRealizedTaxGain(
            tradeId: String,
            oldGainKrw: Long,
            nextGainKrw: Long,
        ) {
            check(realizedTaxGainByTradeId.getValue(tradeId) == oldGainKrw)
            realizedTaxGainByTradeId[tradeId] = nextGainKrw
            try {
                realizedTaxGainTotalKrw = CanonicalPortfolioAccountingTotals
                    .replaceCheckedTaxGainKrwTotal(
                        currentTotalKrw = realizedTaxGainTotalKrw,
                        priorGainKrw = oldGainKrw,
                        nextGainKrw = nextGainKrw,
                        updatedOrderedGainsKrw = realizedTaxGainByTradeId.values.asSequence(),
                    )
            } catch (failure: ArithmeticException) {
                realizedTaxGainByTradeId[tradeId] = oldGainKrw
                throw failure
            }
        }
    }

    private data class NativePosition(
        val quantity: Double,
        val averagePrice: Double,
        val realizedProfit: Double,
    ) {
        val costBasis: Double get() = quantity * averagePrice
    }

    private class MutableTaxLot(
        val lotId: String,
        val stockId: String,
        val acquiredOn: LocalDate,
        val purchaseQuantity: Double,
        var remainingQuantity: Double,
        var initialCostBasisKrw: Long,
        var remainingCostBasisKrw: Long,
        val basisMutations: MutableList<LotBasisMutation> = mutableListOf(),
    )

    /** Basis/quantity transitions after a pending U.S. purchase, retained only until T+1. */
    private sealed interface LotBasisMutation {
        data class QuantityMultiplier(val multiplier: Double) : LotBasisMutation

        data class ReturnOfCapital(
            val sourceId: String,
            val allocatedRocKrw: Long,
            var appliedReductionKrw: Long,
        ) : LotBasisMutation

        data class Sale(
            val sellTradeId: String,
            val quantity: Double,
            var allocatedBasisKrw: Long,
        ) : LotBasisMutation
    }

    private fun compareBoundaries(
        left: AccountingObservationBoundary,
        right: AccountingObservationBoundary,
    ): Int = left.timestamp.compareTo(right.timestamp).takeIf { it != 0 }
        ?: left.accountingSequenceExclusiveUpperBound.compareTo(
            right.accountingSequenceExclusiveUpperBound,
        )

    private data class Event(
        val accountingSequence: Long,
        val occurredAt: Instant,
        val kind: Kind,
        val id: String,
    )

    private enum class Kind {
        TRADE,
        CORPORATE_ACTION,
        DISTRIBUTION_ORIGIN,
        DIVIDEND,
        FX,
        TAX_PAYMENT,
        DEBUG_ADJUSTMENT,
    }

    private data class SettlementEvent(
        val tradeId: String,
        val settledAt: Instant,
    )

    private const val QUANTITY_EPSILON: Double = 1e-8
}
