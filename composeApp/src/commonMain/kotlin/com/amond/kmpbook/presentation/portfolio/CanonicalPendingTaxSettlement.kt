package com.amond.kmpbook.presentation.portfolio

import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.trading.Trade
import com.amond.kmpbook.domain.model.trading.TradeSettlementKind
import com.amond.kmpbook.domain.time.GameCalendar
import com.amond.kmpbook.domain.time.SecuritiesSettlementCalendar
import kotlin.time.Instant

/** 현재 시각에 결제일 환율 확정을 기다려야 하는 미국 거래소 체결 ID의 정본 집합이다. */
fun canonicalPendingTaxSettlementTradeIds(
    trades: List<Trade>,
    stocksById: Map<String, StockDefinition>,
    currentTime: Instant,
): Set<String> = trades.asSequence()
    .filter { trade -> trade.settlementKind == TradeSettlementKind.EXCHANGE_TRADE }
    .filter { trade -> stocksById.getValue(trade.stockId).market.isUnitedStates }
    .filter { trade ->
        val market = stocksById.getValue(trade.stockId).market
        val tradedOn = GameCalendar.marketLocalDateTime(market, trade.executedAt).date
        require(trade.settlementDateOverride == null)
        val settledOn = SecuritiesSettlementCalendar.settlementDate(market, tradedOn)
        GameCalendar.marketLocalDateTime(market, currentTime).date < settledOn
    }
    .mapTo(linkedSetOf(), Trade::id)

/** Pending U.S. T+1 trades still use their execution-time observed FX fact. */
fun pendingTaxSettlementRatesMatchExecutionFacts(
    pendingTradeIds: Set<String>,
    transactionCosts: List<TransactionCostRecord>,
    taxExchangeRatesByTradeId: Map<String, Double>,
): Boolean {
    val costsByTradeId = transactionCosts.associateBy(TransactionCostRecord::tradeId)
    if (costsByTradeId.size != transactionCosts.size) return false
    return pendingTradeIds.all { tradeId ->
        costsByTradeId[tradeId]?.exchangeRateToKrw?.toBits() ==
            taxExchangeRatesByTradeId[tradeId]?.toBits()
    }
}
