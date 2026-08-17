package com.amond.kmpbook.presentation.simulator

import com.amond.kmpbook.domain.model.event.GameEvent
import com.amond.kmpbook.domain.model.instrument.StockDefinition

/** Shared restore/save invariant for read markers after bounded news retention. */
internal fun canonicalReadEventLedgerViolation(
    newsEvents: Collection<GameEvent>,
    readEventIds: Set<String>,
    readStockNewsEventIds: Map<String, Set<String>>,
    stocksById: Map<String, StockDefinition>,
): String? {
    val newsEventsById = newsEvents.associateBy(GameEvent::id)
    if (readEventIds.any { eventId -> eventId !in newsEventsById }) {
        return "전체 뉴스 읽음 원장에 보존 뉴스 원장에 없는 ID가 있습니다."
    }
    if (readStockNewsEventIds.keys.any { stockId -> stockId !in stocksById } ||
        readStockNewsEventIds.any { (stockId, readIds) ->
            val stock = stocksById[stockId] ?: return@any true
            readIds.any { eventId -> newsEventsById[eventId]?.affects(stock) != true }
        }
    ) {
        return "종목별 연관 뉴스 읽음 원장이 현재 종목·뉴스 원장과 일치하지 않습니다."
    }
    return null
}
