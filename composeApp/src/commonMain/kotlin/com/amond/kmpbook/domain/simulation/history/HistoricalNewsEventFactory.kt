package com.amond.kmpbook.domain.simulation.history

import com.amond.kmpbook.domain.model.event.EventRecordKind
import com.amond.kmpbook.domain.model.event.EventScope
import com.amond.kmpbook.domain.model.event.EventSeverity
import com.amond.kmpbook.domain.model.event.EventType
import com.amond.kmpbook.domain.model.event.GameEvent
import com.amond.kmpbook.domain.model.event.GameEventImpact
import com.amond.kmpbook.domain.model.event.ImpactDirection
import com.amond.kmpbook.domain.model.corporateaction.CorporateActionKind
import com.amond.kmpbook.domain.model.corporateaction.CorporateActionRecord
import com.amond.kmpbook.domain.model.corporateaction.CorporateActionSource
import com.amond.kmpbook.domain.model.corporateaction.PendingCorporateAction
import com.amond.kmpbook.domain.model.corporateaction.toAnnouncementNewsReference
import com.amond.kmpbook.domain.model.corporateaction.toAppliedNewsReference
import com.amond.kmpbook.domain.model.history.HistoricalCorporateAction
import com.amond.kmpbook.domain.model.history.HistoricalCorporateActionKind
import com.amond.kmpbook.domain.model.history.HistoricalEventOccurrence
import com.amond.kmpbook.domain.model.history.HistoricalScenarioPack
import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.time.GameCalendar
import kotlin.time.Instant
import kotlin.time.Duration.Companion.nanoseconds

/** 런타임 표시와 저장 canonical 검증이 공유하는 역사 뉴스 투영이다. */
object HistoricalNewsEventFactory {
    const val DURATION_HOURS: Int = 24

    fun idPrefix(pack: HistoricalScenarioPack): String =
        "historical:${pack.definition.id}:"

    fun event(
        pack: HistoricalScenarioPack,
        occurrence: HistoricalEventOccurrence,
        stocksById: Map<String, StockDefinition>,
    ): GameEvent {
        require(pack.events.any { it.id == occurrence.id }) {
            "현재 역사 시나리오에 없는 사건은 뉴스로 만들 수 없습니다: ${occurrence.id}"
        }
        val affectedStockIds = occurrence.affectedInstrumentIds.filterTo(linkedSetOf(), stocksById::containsKey)
        require(affectedStockIds.size == occurrence.affectedInstrumentIds.size) {
            "역사 사건에 현재 종목 카탈로그가 알 수 없는 대상이 있습니다: ${occurrence.id}"
        }
        val affectedMarkets = buildSet {
            addAll(occurrence.affectedMarkets)
            affectedStockIds.mapNotNullTo(this) { stockId -> stocksById[stockId]?.market }
        }
        val scope = when {
            affectedStockIds.isNotEmpty() -> EventScope.STOCK
            affectedMarkets.isNotEmpty() -> EventScope.MARKET
            else -> EventScope.GLOBAL
        }
        val sources = pack.sources.filter { it.id in occurrence.sourceIds }
        require(sources.mapTo(linkedSetOf()) { it.id } == occurrence.sourceIds) {
            "역사 사건 출처를 현재 시나리오에서 모두 찾을 수 없습니다: ${occurrence.id}"
        }
        val reactionSummary = occurrence.marketReactions.joinToString(" · ") { reaction ->
            val local = GameCalendar.marketLocalDateTime(reaction.market, reaction.priceDiscoveryAt)
            "${reaction.market.displayName} ${local.date} ${local.time}"
        }
        return GameEvent(
            id = "${idPrefix(pack)}${occurrence.id}",
            title = occurrence.title,
            description = if (reactionSummary.isBlank()) {
                occurrence.summary
            } else {
                "${occurrence.summary}\n가격발견: $reactionSummary"
            },
            scope = scope,
            type = occurrence.type,
            severity = occurrence.severity,
            impact = GameEventImpact(direction = ImpactDirection.NEUTRAL),
            startsAt = occurrence.publishedAt,
            durationHours = DURATION_HOURS,
            affectedMarkets = affectedMarkets,
            affectedStockIds = affectedStockIds,
            sourceLabel = sources.map { source -> source.publisher }.distinct().joinToString(" · "),
            reportedFacts = occurrence.reportedFacts,
        )
    }

    fun cashDividend(
        pack: HistoricalScenarioPack,
        action: HistoricalCorporateAction,
        stock: StockDefinition,
    ): GameEvent {
        require(action.kind == HistoricalCorporateActionKind.CASH_DIVIDEND)
        require(action.stockId == stock.id)
        require(pack.corporateActions.any { it.id == action.id }) {
            "현재 역사 시나리오에 없는 배당은 뉴스로 만들 수 없습니다: ${action.id}"
        }
        val amount = requireNotNull(action.cashAmount)
        val currency = requireNotNull(action.currency)
        val source = pack.sources.single { it.id == action.sourceId }
        return GameEvent(
            id = "${idPrefix(pack)}${action.id}",
            title = "${stock.name} 현금배당락 반영",
            description = "외부 역사 자료에 기록된 주당 $amount ${currency.name} 현금배당락 사건입니다. " +
                "원시 가격 경로에는 해당 거래일의 가격발견이 이미 포함되어 있습니다.",
            scope = EventScope.STOCK,
            type = EventType.CORPORATE_ACTION,
            severity = EventSeverity.MINOR,
            impact = GameEventImpact(direction = ImpactDirection.NEUTRAL),
            startsAt = action.effectiveAt,
            durationHours = DURATION_HOURS,
            recordKind = EventRecordKind.NEWS,
            affectedMarkets = setOf(stock.market),
            affectedSectors = setOf(stock.sector),
            affectedStockIds = setOf(stock.id),
            sourceLabel = source.publisher,
        )
    }

    fun splitPending(
        pack: HistoricalScenarioPack,
        action: HistoricalCorporateAction,
    ): PendingCorporateAction {
        require(action.kind == HistoricalCorporateActionKind.STOCK_SPLIT)
        require(pack.corporateActions.any { it.id == action.id })
        val multiplier = requireNotNull(action.splitNumerator).toDouble() /
            requireNotNull(action.splitDenominator).toDouble()
        return PendingCorporateAction(
            id = "${idPrefix(pack)}${action.id}",
            stockId = action.stockId,
            kind = if (multiplier > 1.0) {
                CorporateActionKind.FORWARD_SPLIT
            } else {
                CorporateActionKind.REVERSE_SPLIT
            },
            // 공급자 action에는 효력 시각만 있으므로 공시 시각을 꾸미지 않는다. 원장 모델이
            // 요구하는 엄격한 선행 경계만 1ns로 두고 화면에도 그 한계를 명시한다.
            announcedAt = action.effectiveAt - 1.nanoseconds,
            effectiveNotBefore = action.effectiveAt,
            quantityMultiplier = multiplier,
            source = CorporateActionSource.HISTORICAL_DATA,
            rationale = SPLIT_RATIONALE,
        )
    }

    fun splitRatioLabel(action: HistoricalCorporateAction): String {
        require(action.kind == HistoricalCorporateActionKind.STOCK_SPLIT)
        val numerator = requireNotNull(action.splitNumerator)
        val denominator = requireNotNull(action.splitDenominator)
        return if (numerator > denominator) {
            "$denominator:$numerator 분할"
        } else {
            "$denominator:$numerator 병합"
        }
    }

    fun splitAnnouncement(
        pack: HistoricalScenarioPack,
        action: HistoricalCorporateAction,
        stock: StockDefinition,
    ): GameEvent {
        require(action.stockId == stock.id)
        val pending = splitPending(pack, action)
        return GameEvent(
            id = "${pending.id}:announcement",
            title = "${stock.name} 실제 ${pending.kind.displayName} 효력 발생",
            description = "외부 역사 자료가 기록한 효력 시각과 ${splitRatioLabel(action)} 비율입니다. " +
                "실제 공시 시각은 이 데이터에 포함되지 않았으며, 보유 수량·주당원가를 " +
                "같은 효력 경계에서 반영합니다.",
            scope = EventScope.STOCK,
            type = EventType.CORPORATE_ACTION,
            severity = EventSeverity.MINOR,
            impact = GameEventImpact(direction = ImpactDirection.NEUTRAL),
            startsAt = pending.announcedAt,
            durationHours = DURATION_HOURS,
            recordKind = EventRecordKind.CORPORATE_ACTION,
            corporateActionReference = pending.toAnnouncementNewsReference(),
            affectedMarkets = setOf(stock.market),
            affectedSectors = setOf(stock.sector),
            affectedStockIds = setOf(stock.id),
            sourceLabel = CorporateActionSource.HISTORICAL_DATA.displayName,
        )
    }

    fun splitEffective(
        pack: HistoricalScenarioPack,
        action: HistoricalCorporateAction,
        stock: StockDefinition,
        record: CorporateActionRecord,
        settledFraction: Boolean,
    ): GameEvent {
        require(action.stockId == stock.id && record.id == "${idPrefix(pack)}${action.id}")
        val ratioLabel = splitRatioLabel(action)
        return GameEvent(
            id = "${record.id}:effective",
            title = "${stock.name} ${record.kind.displayName} 효력 발생",
            description = if (settledFraction) {
                "${ratioLabel}이 반영됐습니다. 정수 거래단위 미만 단주는 조정가격으로 " +
                    "현금정산하고 FIFO 원가와 양도손익 원장에 기록했습니다."
            } else {
                "${ratioLabel}이 반영됐습니다. 보유 수량과 주당원가를 서로 반대 비율로 " +
                    "조정해 총 평가액과 FIFO 총원가는 보존했습니다."
            },
            scope = EventScope.STOCK,
            type = EventType.CORPORATE_ACTION,
            severity = EventSeverity.MINOR,
            impact = GameEventImpact(direction = ImpactDirection.NEUTRAL),
            startsAt = record.effectiveAt,
            durationHours = DURATION_HOURS,
            recordKind = EventRecordKind.CORPORATE_ACTION,
            corporateActionReference = record.toAppliedNewsReference(),
            affectedMarkets = setOf(stock.market),
            affectedSectors = setOf(stock.sector),
            affectedStockIds = setOf(stock.id),
            sourceLabel = CorporateActionSource.HISTORICAL_DATA.displayName,
        )
    }

    fun publishedNewsThrough(
        pack: HistoricalScenarioPack,
        stocksById: Map<String, StockDefinition>,
        throughInclusive: Instant,
    ): List<GameEvent> = buildList {
        pack.events.asSequence()
            .filter { occurrence -> occurrence.publishedAt <= throughInclusive }
            .map { occurrence -> event(pack, occurrence, stocksById) }
            .forEach(::add)
        pack.corporateActions.asSequence()
            .filter { action ->
                action.kind == HistoricalCorporateActionKind.CASH_DIVIDEND &&
                    action.effectiveAt >= pack.definition.eventLookbackStartsAt &&
                    action.effectiveAt <= throughInclusive
            }
            .map { action ->
                val stock = requireNotNull(stocksById[action.stockId])
                cashDividend(pack, action, stock)
            }
            .forEach(::add)
    }.sortedWith(compareBy(GameEvent::startsAt, GameEvent::id))

    const val SPLIT_RATIONALE: String =
        "외부 역사 자료의 실제 분할 비율을 기준 경로와 보유 수량에 함께 반영했습니다."
}
