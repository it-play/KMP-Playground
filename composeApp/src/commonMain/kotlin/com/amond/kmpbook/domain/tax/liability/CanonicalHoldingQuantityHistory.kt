package com.amond.kmpbook.domain.tax.liability

import com.amond.kmpbook.domain.model.corporateaction.CorporateActionRecord
import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.trading.OrderSide
import com.amond.kmpbook.domain.model.trading.Trade
import kotlin.time.Instant

/**
 * Replays native share quantities at historical observation instants from durable trades and
 * corporate actions. Historical mark prices remain explicit observed facts; they never supply
 * the share count used for a major-shareholder assessment.
 */
object CanonicalHoldingQuantityHistory {
    fun replay(
        stocksById: Map<String, StockDefinition>,
        trades: List<Trade>,
        corporateActions: List<CorporateActionRecord>,
        observationBoundaries: Collection<AccountingObservationBoundary>,
    ): Map<AccountingObservationBoundary, Map<String, Double>> {
        val orderedBoundaries = observationBoundaries
            .sortedWith(compareBy(AccountingObservationBoundary::timestamp)
                .thenBy(AccountingObservationBoundary::accountingSequenceExclusiveUpperBound))
            .also { boundaries ->
                require(boundaries.distinct().size == boundaries.size) {
                    "보유 수량 회계 관측 경계가 중복되었습니다."
                }
        }
        val events = buildList {
            trades.forEach { trade ->
                add(Event(trade.accountingSequence, trade.executedAt, trade.stockId, trade.quantity, trade.side))
            }
            corporateActions.forEach { action ->
                add(
                    Event(
                        accountingSequence = action.accountingSequence,
                        occurredAt = action.effectiveAt,
                        stockId = action.stockId,
                        quantity = action.quantityMultiplier,
                        side = null,
                    ),
                )
            }
        }.sortedBy(Event::accountingSequence)
        require(events.map(Event::accountingSequence).distinct().size == events.size)
        require(events.zipWithNext().all { (left, right) -> left.occurredAt <= right.occurredAt }) {
            "보유 수량 원장의 회계 순번과 적용 시각이 다릅니다."
        }

        val quantities = linkedMapOf<String, Double>()
        val result = linkedMapOf<AccountingObservationBoundary, Map<String, Double>>()
        var eventIndex = 0
        orderedBoundaries.forEach { boundary ->
            while (eventIndex < events.size) {
                val event = events[eventIndex]
                if (!boundary.includes(event.occurredAt, event.accountingSequence)) break
                val stock = stocksById.getValue(event.stockId)
                when (event.side) {
                    null -> quantities[event.stockId]?.let { previous ->
                        quantities[event.stockId] = previous * event.quantity
                    }
                    OrderSide.BUY -> {
                        quantities[event.stockId] = (quantities[event.stockId] ?: 0.0) + event.quantity
                    }
                    OrderSide.SELL -> {
                        val previous = requireNotNull(quantities[event.stockId])
                        require(previous + QUANTITY_EPSILON >= event.quantity)
                        val remaining = (previous - event.quantity).coerceAtLeast(0.0)
                        if (remaining < stock.quantityStep / 2.0) quantities.remove(event.stockId)
                        else quantities[event.stockId] = remaining
                    }
                }
                eventIndex += 1
            }
            result[boundary] = quantities.toMap()
        }
        return result
    }

    /**
     * One merged accounting event; null [side] denotes a corporate-action multiplier. It shares
     * this replay's ordering lifecycle and stays nested so private accounting details do not need
     * internal visibility in a separate file.
     */
    private data class Event(
        val accountingSequence: Long,
        val occurredAt: Instant,
        val stockId: String,
        val quantity: Double,
        val side: OrderSide?,
    )

    private const val QUANTITY_EPSILON: Double = 1e-8
}
