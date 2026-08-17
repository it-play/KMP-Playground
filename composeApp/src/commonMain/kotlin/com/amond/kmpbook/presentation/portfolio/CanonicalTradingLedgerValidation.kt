package com.amond.kmpbook.presentation.portfolio

import com.amond.kmpbook.domain.model.corporateaction.CorporateActionKind
import com.amond.kmpbook.domain.model.corporateaction.CorporateActionRecord
import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingFinalDispositionType
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingLifecycleState
import com.amond.kmpbook.domain.model.portfolio.Holding
import com.amond.kmpbook.domain.model.trading.Order
import com.amond.kmpbook.domain.model.trading.OrderSide
import com.amond.kmpbook.domain.model.trading.OrderStatus
import com.amond.kmpbook.domain.model.trading.OrderType
import com.amond.kmpbook.domain.model.trading.Trade
import com.amond.kmpbook.domain.model.trading.TradeSettlementKind
import com.amond.kmpbook.domain.model.trading.TimeInForce
import com.amond.kmpbook.domain.simulation.market.MarketMicrostructure
import com.amond.kmpbook.domain.time.GameCalendar
import kotlin.math.abs
import kotlin.time.Instant

/** Reflection decoders cannot bypass these constructor and cross-record trading invariants. */
object CanonicalTradingLedgerValidation {
    fun validate(
        orders: List<Order>,
        trades: List<Trade>,
        stocksById: Map<String, StockDefinition>,
        holdingsByStockId: Map<String, Holding>,
        listingLifecycleStates: Map<String, ListingLifecycleState>,
        corporateActions: List<CorporateActionRecord>,
        currentTime: Instant,
    ): String? {
        if (orders.any { order -> runCatching { order.copy() }.isFailure }) {
            return "주문이 생성자 불변식을 위반합니다."
        }
        if (trades.any { trade -> runCatching { trade.copy() }.isFailure }) {
            return "체결이 생성자 불변식을 위반합니다."
        }
        val ordersById = orders.associateBy(Order::id)
        if (ordersById.size != orders.size) return "주문 ID가 중복되었습니다."
        val tradesById = trades.associateBy(Trade::id)
        if (tradesById.size != trades.size) return "체결 ID가 중복되었습니다."
        val tradesByOrderId = trades.groupBy(Trade::orderId)
        if (tradesByOrderId.keys.any { orderId -> orderId !in ordersById }) {
            return "알 수 없는 주문을 참조하는 체결이 있습니다."
        }

        for (order in orders) {
            val stock = stocksById[order.stockId]
                ?: return "알 수 없는 종목을 참조하는 주문이 있습니다."
            if (invalidOrder(
                    order = order,
                    stock = stock,
                    currentHolding = holdingsByStockId[stock.id],
                    listing = listingLifecycleStates[stock.id],
                    currentTime = currentTime,
                )
            ) {
                return "주문의 종목·수량단위·시각·상태와 체결 정보가 일치하지 않습니다."
            }
            val orderTrades = tradesByOrderId[order.id].orEmpty()
            if (order.status == OrderStatus.FILLED) {
                if (orderTrades.size != 1) return "완료 주문에는 정확히 한 건의 체결이 필요합니다."
                val trade = orderTrades.single()
                if (trade.quantity.toBits() != order.filledQuantity.toBits() ||
                    trade.price.toBits() != requireNotNull(order.averageFilledPrice).toBits() ||
                    trade.executedAt != order.updatedAt
                ) {
                    return "완료 주문의 체결수량·평균가격·체결시각이 체결 원장과 다릅니다."
                }
            } else if (orderTrades.isNotEmpty()) {
                return "미체결·취소·거부 주문에는 체결 원장이 있을 수 없습니다."
            }
        }

        val acceptedSellReservations = orders.asSequence()
            .filter { order -> order.status == OrderStatus.ACCEPTED && order.side == OrderSide.SELL }
            .groupBy(Order::stockId)
            .mapValues { (_, stockOrders) -> stockOrders.sumOf(Order::remainingQuantity) }
        if (acceptedSellReservations.any { (stockId, reservedQuantity) ->
                val heldQuantity = holdingsByStockId[stockId]?.quantity ?: 0.0
                !reservedQuantity.isFinite() || reservedQuantity > heldQuantity + QUANTITY_EPSILON
            }
        ) {
            return "미체결 매도 주문의 예약 수량 합이 현재 보유 수량을 초과합니다."
        }

        for (trade in trades) {
            if (invalidTrade(
                    trade = trade,
                    order = ordersById[trade.orderId],
                    stock = stocksById[trade.stockId],
                    listing = listingLifecycleStates[trade.stockId],
                    corporateActions = corporateActions,
                    currentTime = currentTime,
                )
            ) {
                return "체결의 주문·종목·수량·시각·처분 계보 또는 결제 종류가 유효하지 않습니다."
            }
        }
        return null
    }

    private fun invalidOrder(
        order: Order,
        stock: StockDefinition,
        currentHolding: Holding?,
        listing: ListingLifecycleState?,
        currentTime: Instant,
    ): Boolean {
        if (order.side !in OrderSide.entries || order.type !in OrderType.entries ||
            order.status !in OrderStatus.entries || order.timeInForce !in TimeInForce.entries ||
            listing == null || order.createdAt !in GameCalendar.startInstant..currentTime ||
            order.updatedAt !in order.createdAt..currentTime ||
            !order.quantity.isFinite() || order.averageFilledPrice?.isFinite() == false ||
            order.rejectionReason?.let { it.isBlank() || it.length > MAX_REASON_LENGTH } == true ||
            order.status in setOf(OrderStatus.PENDING, OrderStatus.PARTIALLY_FILLED)
        ) {
            return true
        }
        if (order.limitPrice != null && abs(
                MarketMicrostructure.roundNearest(stock, order.limitPrice) - order.limitPrice,
            ) > PRICE_EPSILON
        ) {
            return true
        }
        if (order.status == OrderStatus.ACCEPTED) {
            if (!listing.isOrderAllowed) return true
            if (order.timeInForce !in setOf(TimeInForce.DAY, TimeInForce.GOOD_TILL_CANCELLED)) {
                return true
            }
            if (order.timeInForce == TimeInForce.DAY &&
                canonicalDayOrderSessionClose(stock.market, order.createdAt)?.let { close ->
                    currentTime < close
                } != true
            ) {
                return true
            }
            val fullCorporateRemainder = order.side == OrderSide.SELL && currentHolding?.let { holding ->
                abs(holding.quantity - order.remainingQuantity) <= QUANTITY_EPSILON
            } == true
            if (!stock.acceptsQuantity(order.remainingQuantity) && !fullCorporateRemainder) return true
        }

        val listingContractOrder = order.id.startsWith(LISTING_DISPOSITION_ORDER_PREFIX)
        if (order.isNonMarketDisposition != listingContractOrder) return true
        if (order.isNonMarketDisposition &&
            (order.status != OrderStatus.FILLED || order.type != OrderType.MARKET ||
                order.rejectionReason.isNullOrBlank())
        ) {
            return true
        }

        val filledExactly = order.filledQuantity.toBits() == order.quantity.toBits()
        return when (order.status) {
            OrderStatus.ACCEPTED -> order.filledQuantity != 0.0 || order.averageFilledPrice != null ||
                order.rejectionReason != null
            OrderStatus.FILLED -> !filledExactly || order.averageFilledPrice == null
            OrderStatus.REJECTED -> order.filledQuantity != 0.0 || order.averageFilledPrice != null ||
                order.rejectionReason.isNullOrBlank()
            OrderStatus.CANCELLED,
            OrderStatus.EXPIRED,
            -> order.filledQuantity != 0.0 || order.averageFilledPrice != null
            OrderStatus.PENDING,
            OrderStatus.PARTIALLY_FILLED,
            -> true
        }
    }

    private fun invalidTrade(
        trade: Trade,
        order: Order?,
        stock: StockDefinition?,
        listing: ListingLifecycleState?,
        corporateActions: List<CorporateActionRecord>,
        currentTime: Instant,
    ): Boolean {
        if (order == null || stock == null || listing == null ||
            trade.side !in OrderSide.entries ||
            trade.currency !in com.amond.kmpbook.domain.model.market.Currency.entries ||
            trade.settlementKind !in TradeSettlementKind.entries ||
            trade.executedAt !in GameCalendar.startInstant..currentTime ||
            trade.stockId != order.stockId || trade.side != order.side ||
            trade.currency != stock.currency || trade.executedAt != order.updatedAt ||
            trade.quantity.toBits() != order.filledQuantity.toBits() ||
            trade.price.toBits() != requireNotNull(order.averageFilledPrice).toBits()
        ) {
            return true
        }
        val listingContractOrder = order.id.startsWith(LISTING_DISPOSITION_ORDER_PREFIX)
        val listingMarketOrder = order.id.startsWith(LISTING_MARKET_SALE_ORDER_PREFIX)
        val cashInLieuOrder = order.id.startsWith(CASH_IN_LIEU_ORDER_PREFIX)
        if (listOf(listingContractOrder, listingMarketOrder, cashInLieuOrder).count { it } > 1) return true

        return when (trade.settlementKind) {
            TradeSettlementKind.EXCHANGE_TRADE -> when {
                trade.settlementDateOverride != null || order.isNonMarketDisposition || listingContractOrder -> true
                abs(MarketMicrostructure.roundNearest(stock, trade.price) - trade.price) >
                    PRICE_EPSILON -> true
                listingMarketOrder -> !matchesListingDisposition(
                    listing = listing,
                    expectedType = ListingFinalDispositionType.MARKET_SALE,
                    order = order,
                    trade = trade,
                )
                cashInLieuOrder -> !matchesCashInLieu(corporateActions, order, trade)
                else -> {
                    val localDate = GameCalendar.marketLocalDateTime(stock.market, trade.executedAt).date
                    val session = GameCalendar.regularSessionWindow(stock.market, localDate)
                    session == null || trade.executedAt < session.opensAt ||
                        trade.executedAt >= session.closesAt
                }
            }
            TradeSettlementKind.CONTRACTUAL_CASH_SETTLEMENT ->
                !listingContractOrder || !order.isNonMarketDisposition || !matchesListingDisposition(
                    listing = listing,
                    expectedType = ListingFinalDispositionType.CASH_LIQUIDATION,
                    order = order,
                    trade = trade,
                )
        }
    }

    private fun matchesListingDisposition(
        listing: ListingLifecycleState,
        expectedType: ListingFinalDispositionType,
        order: Order,
        trade: Trade,
    ): Boolean {
        if (!listing.isTerminal) return false
        val disposition = listing.finalDisposition ?: return false
        if (disposition.type != expectedType) return false
        val tradedOn = GameCalendar.marketLocalDateTime(listing.market, trade.executedAt).date
        return when (expectedType) {
            ListingFinalDispositionType.CASH_LIQUIDATION ->
                disposition.settlementDueOn == tradedOn &&
                    trade.settlementDateOverride == tradedOn &&
                    disposition.entitledQuantity?.toBits() == trade.quantity.toBits() &&
                    disposition.cashPerUnit?.toBits() == trade.price.toBits() &&
                    order.createdAt == trade.executedAt
            ListingFinalDispositionType.MARKET_SALE ->
                disposition.effectiveOn == tradedOn &&
                    trade.settlementDateOverride == null &&
                    order.createdAt == trade.executedAt &&
                    !order.rejectionReason.isNullOrBlank()
            else -> false
        }
    }

    private fun matchesCashInLieu(
        corporateActions: List<CorporateActionRecord>,
        order: Order,
        trade: Trade,
    ): Boolean = order.rejectionReason == CASH_IN_LIEU_REASON &&
        corporateActions.any { action ->
            action.stockId == trade.stockId && action.kind == CorporateActionKind.REVERSE_SPLIT &&
                action.effectiveAt == trade.executedAt &&
                action.postActionPrice.toBits() == trade.price.toBits() &&
                action.accountingSequence < trade.accountingSequence
        }

    private const val LISTING_DISPOSITION_ORDER_PREFIX: String = "listing-disposition-order-"
    private const val LISTING_MARKET_SALE_ORDER_PREFIX: String = "listing-market-sale-order-"
    private const val CASH_IN_LIEU_ORDER_PREFIX: String = "cash-in-lieu-order-"
    private const val CASH_IN_LIEU_REASON: String = "주식병합 단주 현금정산"
    private const val MAX_REASON_LENGTH: Int = 1_024
    private const val QUANTITY_EPSILON: Double = 1e-7
    private const val PRICE_EPSILON: Double = 1e-8
}
