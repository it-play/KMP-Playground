package com.amond.kmpbook.presentation.portfolio

import com.amond.kmpbook.domain.model.corporateaction.CorporateActionRecord
import com.amond.kmpbook.domain.model.corporateaction.CorporateActionKind
import com.amond.kmpbook.domain.model.instrument.DistributionEntitlementOrigin
import com.amond.kmpbook.domain.model.instrument.EtfTaxCategory
import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.market.Currency
import com.amond.kmpbook.domain.model.portfolio.PortfolioSnapshot
import com.amond.kmpbook.domain.model.trading.Order
import com.amond.kmpbook.domain.model.trading.OrderSide
import com.amond.kmpbook.domain.model.trading.Trade
import com.amond.kmpbook.domain.model.trading.TradeSettlementKind
import com.amond.kmpbook.domain.tax.liability.StockGainTaxTreatmentResolver
import com.amond.kmpbook.domain.tax.liability.AccountingObservationBoundary
import com.amond.kmpbook.domain.tax.liability.CanonicalHoldingQuantityHistory
import com.amond.kmpbook.domain.tax.dividend.DistributionReturnOfCapitalPolicy
import com.amond.kmpbook.domain.tax.lot.FifoCostBasisBook
import com.amond.kmpbook.domain.time.GameCalendar
import com.amond.kmpbook.domain.time.SecuritiesSettlementCalendar
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round

/**
 * Rebuilds the tax lot book and every realized gain from independently persisted accounting
 * sources. Trade-settlement FX, dividend pay FX, and ex-date ROC-basis FX remain explicit observed
 * facts because the bounded save history cannot replay the full historical macro path.
 *
 * Private event types remain nested because they are coupled to this replay's ordering and lot
 * mutation lifecycle; exposing them as module-wide implementation types would weaken encapsulation.
 */
object CanonicalTaxAccountingReplay {
    fun replay(
        stocksById: Map<String, StockDefinition>,
        orders: List<Order>,
        trades: List<Trade>,
        transactionCosts: List<TransactionCostRecord>,
        taxExchangeRatesByTradeId: Map<String, Double>,
        corporateActions: List<CorporateActionRecord>,
        distributionOrigins: List<DistributionEntitlementOrigin>,
        dividendEntries: List<DividendLedgerEntry>,
        portfolioSnapshots: List<PortfolioSnapshot>,
    ): CanonicalTaxAccountingResult {
        val ordersById = orders.associateBy(Order::id)
        require(ordersById.size == orders.size) { "주문 ID가 중복되었습니다." }
        val tradesById = trades.associateBy(Trade::id)
        require(tradesById.size == trades.size) { "체결 ID가 중복되었습니다." }
        val costsByTradeId = transactionCosts.associateBy(TransactionCostRecord::tradeId)
        require(costsByTradeId.size == transactionCosts.size && costsByTradeId.keys == tradesById.keys) {
            "모든 체결에는 정확히 하나의 거래비용 원장이 필요합니다."
        }
        require(taxExchangeRatesByTradeId.keys == tradesById.keys)
        require(taxExchangeRatesByTradeId.values.all { rate -> rate.isFinite() && rate > 0.0 })
        val actionsById = corporateActions.associateBy(CorporateActionRecord::id)
        require(actionsById.size == corporateActions.size)
        val originsById = distributionOrigins.associateBy(DistributionEntitlementOrigin::id)
        require(originsById.size == distributionOrigins.size)
        val originsByDistributionKey = distributionOrigins.associateBy { origin ->
            origin.stockId to origin.exDate
        }
        require(originsByDistributionKey.size == distributionOrigins.size)
        val dividendsById = dividendEntries.associateBy(DividendLedgerEntry::id)
        require(dividendsById.size == dividendEntries.size)
        val canonicalSnapshotQuantities = CanonicalHoldingQuantityHistory.replay(
            stocksById = stocksById,
            trades = trades,
            corporateActions = corporateActions,
            observationBoundaries = portfolioSnapshots.map { snapshot ->
                AccountingObservationBoundary(
                    snapshot.timestamp,
                    snapshot.accountingSequenceExclusiveUpperBound,
                )
            },
        )
        portfolioSnapshots.forEach { snapshot ->
            val actual = snapshot.holdings.associate { holding -> holding.stockId to holding.quantity }
            require(actual.size == snapshot.holdings.size)
            val canonical = canonicalSnapshotQuantities.getValue(
                AccountingObservationBoundary(
                    snapshot.timestamp,
                    snapshot.accountingSequenceExclusiveUpperBound,
                ),
            )
            require(actual.keys == canonical.keys && actual.all { (stockId, quantity) ->
                quantity.toBits() == canonical.getValue(stockId).toBits()
            }) { "일별 보유 수량이 canonical 거래·기업행동 prefix와 다릅니다." }
        }

        val events = buildList {
            corporateActions.forEach { action ->
                add(ReplayEvent(action.accountingSequence, ReplayEventKind.CORPORATE_ACTION, action.id))
            }
            trades.forEach { trade ->
                add(ReplayEvent(trade.accountingSequence, ReplayEventKind.TRADE, trade.id))
            }
            distributionOrigins.forEach { origin ->
                add(ReplayEvent(origin.accountingSequence, ReplayEventKind.DISTRIBUTION_ORIGIN, origin.id))
            }
            dividendEntries.asSequence()
                .filter { entry ->
                    entry.returnOfCapitalAmount > 0.0 &&
                        (entry.stockId to entry.exDate) !in originsByDistributionKey
                }
                .forEach { entry ->
                    add(ReplayEvent(entry.accountingSequence, ReplayEventKind.NON_ETF_ROC, entry.id))
                }
        }.sortedBy(ReplayEvent::accountingSequence)
        require(events.map(ReplayEvent::accountingSequence).distinct().size == events.size) {
            "세무 재생 입력의 회계 순번이 중복되었습니다."
        }

        var fifoBook = FifoCostBasisBook()
        val nativePositions = linkedMapOf<String, NativePosition>()
        val rebuiltGains = mutableListOf<RealizedGainRecord>()
        val originExcess = linkedMapOf<String, Long>()
        val dividendExcess = dividendEntries.associateTo(linkedMapOf()) { entry -> entry.id to 0L }

        for (event in events) {
            when (event.kind) {
                ReplayEventKind.CORPORATE_ACTION -> {
                    val action = actionsById.getValue(event.id)
                    require(action.stockId in stocksById)
                    fifoBook = fifoBook.applyQuantityMultiplier(
                        action.stockId,
                        action.quantityMultiplier,
                    )
                    nativePositions[action.stockId]?.let { previous ->
                        nativePositions[action.stockId] = previous.copy(
                            quantity = previous.quantity * action.quantityMultiplier,
                            averagePrice = previous.averagePrice / action.quantityMultiplier,
                        )
                    }
                }

                ReplayEventKind.TRADE -> {
                    val trade = tradesById.getValue(event.id)
                    val stock = stocksById.getValue(trade.stockId)
                    val order = ordersById.getValue(trade.orderId)
                    val cost = costsByTradeId.getValue(trade.id)
                    require(
                        order.stockId == trade.stockId && order.side == trade.side &&
                            cost.stockId == trade.stockId && cost.market == stock.market &&
                            cost.paidAt == trade.executedAt && cost.currency == trade.currency &&
                            trade.currency == stock.currency &&
                            cost.commission.toBits() == trade.commission.toBits() &&
                            cost.saleTax.toBits() == trade.tax.toBits(),
                    ) { "체결·주문·거래비용 원장의 종목·시장·시각·금액이 다릅니다." }
                    val taxExchangeRate = taxExchangeRatesByTradeId.getValue(trade.id)
                    require(
                        cost.exchangeRateToKrw.isFinite() &&
                            when (stock.currency) {
                                Currency.KRW -> cost.exchangeRateToKrw.toBits() == 1.0.toBits() &&
                                    taxExchangeRate.toBits() == 1.0.toBits()
                                Currency.USD -> cost.exchangeRateToKrw in MIN_USD_KRW..MAX_USD_KRW &&
                                    taxExchangeRate in MIN_USD_KRW..MAX_USD_KRW
                            },
                    ) { "거래비용 실행환율 또는 세무 결제환율 fact가 유효하지 않습니다." }
                    val settledOn = when (trade.settlementKind) {
                        TradeSettlementKind.EXCHANGE_TRADE -> {
                            require(trade.settlementDateOverride == null)
                            SecuritiesSettlementCalendar.settlementDate(
                                stock.market,
                                GameCalendar.marketLocalDateTime(stock.market, trade.executedAt).date,
                            )
                        }
                        TradeSettlementKind.CONTRACTUAL_CASH_SETTLEMENT ->
                            requireNotNull(trade.settlementDateOverride)
                    }
                    val roundedGross = canonicalTradeGrossCash(trade)
                    val previousNativePosition = nativePositions[stock.id]
                    val costMode = canonicalCostMode(order, trade)
                    validateDispositionProvenance(
                        order = order,
                        trade = trade,
                        stock = stock,
                        previous = previousNativePosition,
                        corporateActions = corporateActions,
                        mode = costMode,
                    )
                    val tradedOn = GameCalendar.marketLocalDateTime(
                        stock.market,
                        trade.executedAt,
                    ).date
                    val projectedCost = CanonicalTradeCostProjection.project(
                        stock = stock,
                        side = trade.side,
                        quantity = trade.quantity,
                        grossCash = roundedGross,
                        tradedOn = tradedOn,
                        preSaleAveragePrice = previousNativePosition?.averagePrice,
                        mode = costMode,
                    )
                    val expectedCost = TransactionCostRecord(
                        tradeId = trade.id,
                        stockId = stock.id,
                        market = stock.market,
                        paidAt = trade.executedAt,
                        currency = stock.currency,
                        commission = projectedCost.commission,
                        saleTax = projectedCost.saleTax,
                        exchangeRateToKrw = cost.exchangeRateToKrw,
                        feeBreakdown = projectedCost.feeBreakdown,
                        taxBreakdown = projectedCost.taxBreakdown,
                    )
                    require(
                        trade.commission.toBits() == projectedCost.commission.toBits() &&
                            trade.tax.toBits() == projectedCost.saleTax.toBits() &&
                            cost == expectedCost,
                    ) { "체결 수수료·거래세와 상세 원장이 canonical 정책 계산과 다릅니다." }
                    if (trade.side == OrderSide.BUY) {
                        val previous = previousNativePosition
                        val nextQuantity = (previous?.quantity ?: 0.0) + trade.quantity
                        val nextCost = (previous?.costBasis ?: 0.0) + roundedGross + trade.commission
                        nativePositions[stock.id] = NativePosition(
                            quantity = nextQuantity,
                            averagePrice = nextCost / nextQuantity,
                            realizedProfit = previous?.realizedProfit ?: 0.0,
                        )
                        fifoBook = fifoBook.addPurchase(
                            lotId = trade.id,
                            stockId = stock.id,
                            acquiredOn = settledOn,
                            quantity = trade.quantity,
                            purchasePriceKrw = round(roundedGross * taxExchangeRate).toLong(),
                            directPurchaseCostsKrw = round(trade.commission * taxExchangeRate).toLong(),
                        )
                    } else {
                        val previous = requireNotNull(previousNativePosition) {
                            "매도 전에 재생 가능한 보유 수량이 없습니다: ${trade.id}"
                        }
                        require(previous.quantity + QUANTITY_EPSILON >= trade.quantity)
                        val nativeCostBasis = previous.averagePrice * trade.quantity
                        val fifoSale = fifoBook.sell(
                            stockId = stock.id,
                            soldOn = settledOn,
                            quantity = trade.quantity,
                            grossProceedsKrw = round(roundedGross * taxExchangeRate).toLong(),
                            directSellingCostsKrw =
                                round((trade.commission + trade.tax) * taxExchangeRate).toLong(),
                        )
                        fifoBook = fifoSale.updatedBook
                        val realizedProfit = roundedGross - nativeCostBasis - trade.commission - trade.tax
                        val remainingQuantity = (previous.quantity - trade.quantity).coerceAtLeast(0.0)
                        if (remainingQuantity < stock.quantityStep / 2.0) {
                            nativePositions.remove(stock.id)
                        } else {
                            nativePositions[stock.id] = previous.copy(
                                quantity = remainingQuantity,
                                realizedProfit = previous.realizedProfit + realizedProfit,
                            )
                        }
                        val (treatment, baseNotes) = StockGainTaxTreatmentResolver.resolve(
                            stock = stock,
                            assessedOn = tradedOn,
                            assessedAt = trade.executedAt,
                            assessedAccountingSequence = trade.accountingSequence,
                            preSaleQuantity = previous.quantity,
                            portfolioSnapshots = portfolioSnapshots,
                            canonicalSnapshotQuantities = canonicalSnapshotQuantities,
                            corporateActions = corporateActions,
                        )
                        val taxableFinancialIncomeKrw = if (
                            stock.etfProfile?.taxCategory == EtfTaxCategory.KOREAN_OTHER
                        ) {
                            requireNotNull(cost.taxBreakdown).taxableBase.minorUnits
                        } else {
                            0L
                        }
                        rebuiltGains += RealizedGainRecord(
                            tradeId = trade.id,
                            stockId = stock.id,
                            market = stock.market,
                            soldAt = trade.executedAt,
                            settlementDate = settledOn,
                            quantity = trade.quantity,
                            proceeds = roundedGross,
                            costBasis = nativeCostBasis,
                            commission = trade.commission,
                            saleTax = trade.tax,
                            currency = stock.currency,
                            exchangeRateToKrw = taxExchangeRate,
                            taxTreatment = treatment,
                            assessmentNotes = baseNotes + dispositionAssessmentNote(order, trade),
                            taxGrossProceedsKrw = fifoSale.grossProceedsKrw,
                            taxCostBasisKrw = fifoSale.allocatedCostBasisKrw,
                            taxDirectSellingCostsKrw = fifoSale.directSellingCostsKrw,
                            taxGainKrw = fifoSale.realizedGainKrw,
                            taxableFinancialIncomeKrw = taxableFinancialIncomeKrw,
                        )
                    }
                }

                ReplayEventKind.DISTRIBUTION_ORIGIN -> {
                    val origin = originsById.getValue(event.id)
                    val stock = stocksById.getValue(origin.stockId)
                    require(
                        origin.taxableCoverageRatio.toBits() ==
                            DistributionReturnOfCapitalPolicy
                                .modeledTaxableCoverageRatio(stock)
                                .toBits(),
                    ) { "ETF 분배 origin의 세무 coverage가 상품의 ROC 정책과 다릅니다." }
                    require(
                        DistributionReturnOfCapitalPolicy.isEligible(stock) ||
                            origin.returnOfCapitalAmount.toBits() == 0.0.toBits(),
                    ) { "ROC 비대상 상품의 ETF 분배 origin에 원가감소 금액이 있습니다." }
                    val nativeQuantity = nativePositions[stock.id]?.quantity ?: 0.0
                    val taxLotQuantity = fifoBook.lots.asSequence()
                        .filter { lot -> lot.stockId == stock.id }
                        .sumOf { lot -> lot.remainingQuantity }
                    require(abs(nativeQuantity - origin.entitledQuantity) <= QUANTITY_EPSILON)
                    require(abs(taxLotQuantity - origin.entitledQuantity) <= QUANTITY_EPSILON)
                    val (updatedBook, excess) = fifoBook.applyReturnOfCapital(
                        stockId = stock.id,
                        amountKrw = round(
                            origin.returnOfCapitalAmount * origin.taxBasisExchangeRateToKrw,
                        ).toLong(),
                    )
                    fifoBook = updatedBook
                    originExcess[origin.id] = excess
                }

                ReplayEventKind.NON_ETF_ROC -> {
                    val dividend = dividendsById.getValue(event.id)
                    val stock = stocksById.getValue(dividend.stockId)
                    require(DistributionReturnOfCapitalPolicy.isEligible(stock)) {
                        "ROC 비대상 상품의 분배 원장에 원가감소 금액이 있습니다."
                    }
                    val (updatedBook, excess) = fifoBook.applyReturnOfCapital(
                        stockId = dividend.stockId,
                        amountKrw = round(
                            dividend.returnOfCapitalAmount * dividend.exchangeRateToKrw,
                        ).toLong(),
                    )
                    fifoBook = updatedBook
                    dividendExcess[dividend.id] = excess
                }
            }
        }

        dividendEntries.forEach { entry ->
            originsByDistributionKey[entry.stockId to entry.exDate]?.let { origin ->
                dividendExcess[entry.id] = originExcess.getValue(origin.id)
            }
        }
        val affectedYears = buildSet {
            rebuiltGains.mapTo(this) { gain -> gain.settlementDate.year }
            dividendEntries.mapTo(this) { entry -> GameCalendar.campaignDate(entry.paidAt).year }
        }
        return CanonicalTaxAccountingResult(
            fifoCostBasisBook = fifoBook,
            realizedGains = rebuiltGains,
            nativeHoldingsByStockId = nativePositions.mapValuesTo(linkedMapOf()) { (stockId, position) ->
                CanonicalTaxNativeHoldingFact(
                    stockId = stockId,
                    quantity = position.quantity,
                    averagePrice = position.averagePrice,
                    currency = stocksById.getValue(stockId).currency,
                    realizedProfit = position.realizedProfit,
                )
            },
            originExcessReturnOfCapitalGainKrw = originExcess,
            dividendExcessReturnOfCapitalGainKrw = dividendExcess,
            affectedTaxYears = affectedYears,
        )
    }

    private fun dispositionAssessmentNote(order: Order, trade: Trade): List<String> = when {
        trade.settlementKind == TradeSettlementKind.CONTRACTUAL_CASH_SETTLEMENT -> listOf(
            "${requireNotNull(order.rejectionReason)} 지급일의 환율과 원천징수를 처분 원장에 반영했습니다.",
        )
        order.id.startsWith("listing-market-sale-order-") -> listOf(
            "${requireNotNull(order.rejectionReason)} 처분을 일반 매도 원장에 반영했습니다.",
        )
        order.id.startsWith("cash-in-lieu-order-") ->
            listOf("주식병합 단주를 현금정산 처분으로 반영했습니다.")
        else -> emptyList()
    }

    private fun canonicalCostMode(
        order: Order,
        trade: Trade,
    ): CanonicalTradeCostMode = when {
        trade.settlementKind == TradeSettlementKind.CONTRACTUAL_CASH_SETTLEMENT ->
            CanonicalTradeCostMode.CONTRACTUAL_CASH_SETTLEMENT
        order.id.startsWith(CASH_IN_LIEU_ORDER_PREFIX) ->
            CanonicalTradeCostMode.CORPORATE_ACTION_CASH_IN_LIEU
        else -> CanonicalTradeCostMode.REGULAR_EXCHANGE
    }

    private fun validateDispositionProvenance(
        order: Order,
        trade: Trade,
        stock: StockDefinition,
        previous: NativePosition?,
        corporateActions: List<CorporateActionRecord>,
        mode: CanonicalTradeCostMode,
    ) {
        if (trade.side != OrderSide.SELL) {
            require(mode == CanonicalTradeCostMode.REGULAR_EXCHANGE)
            return
        }
        val position = requireNotNull(previous)
        when (mode) {
            CanonicalTradeCostMode.CORPORATE_ACTION_CASH_IN_LIEU -> {
                val action = corporateActions.asSequence()
                    .filter { candidate ->
                        candidate.stockId == stock.id &&
                            candidate.kind == CorporateActionKind.REVERSE_SPLIT &&
                            candidate.effectiveAt == trade.executedAt &&
                            candidate.postActionPrice.toBits() == trade.price.toBits() &&
                            candidate.accountingSequence < trade.accountingSequence
                    }
                    .maxByOrNull(CorporateActionRecord::accountingSequence)
                require(action != null && order.rejectionReason == CASH_IN_LIEU_REASON)
                val tradableQuantity = floor(
                    (position.quantity + CASH_IN_LIEU_QUANTITY_EPSILON) / stock.quantityStep,
                ) * stock.quantityStep
                val expectedRemainder = (position.quantity - tradableQuantity).coerceAtLeast(0.0)
                require(expectedRemainder >= CASH_IN_LIEU_QUANTITY_EPSILON)
                require(abs(expectedRemainder - trade.quantity) <= CASH_IN_LIEU_QUANTITY_EPSILON)
            }

            CanonicalTradeCostMode.CONTRACTUAL_CASH_SETTLEMENT,
            -> require(abs(position.quantity - trade.quantity) <= QUANTITY_EPSILON)

            CanonicalTradeCostMode.REGULAR_EXCHANGE -> {
                if (order.id.startsWith(LISTING_MARKET_SALE_ORDER_PREFIX)) {
                    require(abs(position.quantity - trade.quantity) <= QUANTITY_EPSILON)
                }
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

    private data class ReplayEvent(
        val accountingSequence: Long,
        val kind: ReplayEventKind,
        val id: String,
    )

    private enum class ReplayEventKind {
        CORPORATE_ACTION,
        TRADE,
        DISTRIBUTION_ORIGIN,
        NON_ETF_ROC,
    }

    private const val QUANTITY_EPSILON: Double = 1e-8
    private const val CASH_IN_LIEU_QUANTITY_EPSILON: Double = 1e-7
    private const val CASH_IN_LIEU_ORDER_PREFIX: String = "cash-in-lieu-order-"
    private const val LISTING_MARKET_SALE_ORDER_PREFIX: String = "listing-market-sale-order-"
    private const val CASH_IN_LIEU_REASON: String = "주식병합 단주 현금정산"
    private const val MIN_USD_KRW: Double = 800.0
    private const val MAX_USD_KRW: Double = 2_500.0
}
