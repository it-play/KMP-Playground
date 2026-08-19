package com.amond.kmpbook.domain.simulation.history

import com.amond.kmpbook.domain.model.history.HistoricalCorporateAction
import com.amond.kmpbook.domain.model.history.HistoricalDailyBar
import com.amond.kmpbook.domain.model.history.HistoricalEventOccurrence
import com.amond.kmpbook.domain.model.history.HistoricalEventReference
import com.amond.kmpbook.domain.model.history.HistoricalScenarioPack
import com.amond.kmpbook.domain.model.history.HistoricalSourceReference
import com.amond.kmpbook.domain.model.market.Market
import kotlinx.datetime.LocalDate
import kotlin.math.ln
import kotlin.time.Instant

/**
 * 검증된 역사 시나리오에서 일봉·사건·기업행동을 결정론적으로 조회하는 순수 엔진이다.
 * 가격 생성이나 런타임 상태 변경은 담당하지 않는다.
 */
class HistoricalScenarioEngine(
    val pack: HistoricalScenarioPack,
) {
    private val barsByInstrument: Map<String, List<HistoricalDailyBar>> = pack.dailyBarsByInstrument
    private val eventsById: Map<String, HistoricalEventOccurrence> = pack.events
        .associateBy(HistoricalEventOccurrence::id)
    private val eventsByPublishedAt: List<HistoricalEventOccurrence> = pack.events
        .sortedWith(compareBy(HistoricalEventOccurrence::publishedAt, HistoricalEventOccurrence::id))
    private val actionsByStock: Map<String, List<HistoricalCorporateAction>> = pack.corporateActions
        .groupBy(HistoricalCorporateAction::stockId)
        .mapValues { (_, actions) -> actions.sortedBy(HistoricalCorporateAction::effectiveAt) }

    val instrumentIds: Set<String> = barsByInstrument.keys

    fun dailyBar(instrumentId: String, tradingDate: LocalDate): HistoricalDailyBar? {
        val bars = barsByInstrument[instrumentId].orEmpty()
        val index = bars.binarySearch { bar -> bar.tradingDate.compareTo(tradingDate) }
        return bars.getOrNull(index)
    }

    fun dailyBars(
        instrumentId: String,
        fromInclusive: LocalDate,
        throughInclusive: LocalDate,
    ): List<HistoricalDailyBar> {
        require(fromInclusive <= throughInclusive) { "역사 일봉 조회 시작일은 종료일보다 늦을 수 없습니다." }
        val bars = barsByInstrument[instrumentId].orEmpty()
        val fromIndex = bars.lowerBound(fromInclusive)
        val throughIndex = bars.upperBound(throughInclusive)
        return if (fromIndex >= throughIndex) emptyList() else bars.subList(fromIndex, throughIndex)
    }

    fun latestDailyBarOnOrBefore(instrumentId: String, tradingDate: LocalDate): HistoricalDailyBar? {
        val bars = barsByInstrument[instrumentId].orEmpty()
        return bars.getOrNull(bars.upperBound(tradingDate) - 1)
    }

    fun nextDailyBarAfter(instrumentId: String, tradingDate: LocalDate): HistoricalDailyBar? {
        val bars = barsByInstrument[instrumentId].orEmpty()
        return bars.getOrNull(bars.upperBound(tradingDate))
    }

    fun closeToCloseLogReturn(
        instrumentId: String,
        fromTradingDate: LocalDate,
        toTradingDate: LocalDate,
        adjusted: Boolean = false,
    ): Double? {
        require(fromTradingDate < toTradingDate) { "수익률 종료 거래일은 시작 거래일보다 늦어야 합니다." }
        val from = dailyBar(instrumentId, fromTradingDate) ?: return null
        val to = dailyBar(instrumentId, toTradingDate) ?: return null
        val fromClose = if (adjusted) from.adjustedClose ?: return null else from.close
        val toClose = if (adjusted) to.adjustedClose ?: return null else to.close
        return ln(toClose / fromClose)
    }

    fun event(occurrenceId: String): HistoricalEventOccurrence? = eventsById[occurrenceId]

    fun eventReference(occurrenceId: String): HistoricalEventReference = pack.eventReference(occurrenceId)

    fun eventsPublishedBetween(
        fromInclusive: Instant,
        toExclusive: Instant,
    ): List<HistoricalEventOccurrence> {
        require(fromInclusive <= toExclusive) { "역사 사건 조회 시작 시각은 종료 시각보다 늦을 수 없습니다." }
        return eventsByPublishedAt.filter { it.publishedAt >= fromInclusive && it.publishedAt < toExclusive }
    }

    fun marketReactionsBetween(
        market: Market,
        fromInclusive: Instant,
        toExclusive: Instant,
    ): List<HistoricalEventOccurrence> {
        require(fromInclusive <= toExclusive) { "시장 반응 조회 시작 시각은 종료 시각보다 늦을 수 없습니다." }
        return pack.events
            .asSequence()
            .filter { event ->
                event.marketReactions.any { reaction ->
                    reaction.market == market &&
                        reaction.priceDiscoveryAt >= fromInclusive &&
                        reaction.priceDiscoveryAt < toExclusive
                }
            }
            .sortedWith(
                compareBy<HistoricalEventOccurrence> { event ->
                    event.marketReactions.single { it.market == market }.priceDiscoveryAt
                }.thenBy(HistoricalEventOccurrence::id),
            )
            .toList()
    }

    fun corporateActions(
        stockId: String,
        fromInclusive: Instant,
        toExclusive: Instant,
    ): List<HistoricalCorporateAction> {
        require(fromInclusive <= toExclusive) { "기업행동 조회 시작 시각은 종료 시각보다 늦을 수 없습니다." }
        return actionsByStock[stockId]
            .orEmpty()
            .filter { it.effectiveAt >= fromInclusive && it.effectiveAt < toExclusive }
    }

    fun sourcesFor(event: HistoricalEventOccurrence): List<HistoricalSourceReference> =
        pack.sources.filter { it.id in event.sourceIds }

    private fun List<HistoricalDailyBar>.lowerBound(date: LocalDate): Int {
        var low = 0
        var high = size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (this[middle].tradingDate < date) low = middle + 1 else high = middle
        }
        return low
    }

    private fun List<HistoricalDailyBar>.upperBound(date: LocalDate): Int {
        var low = 0
        var high = size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (this[middle].tradingDate <= date) low = middle + 1 else high = middle
        }
        return low
    }
}
