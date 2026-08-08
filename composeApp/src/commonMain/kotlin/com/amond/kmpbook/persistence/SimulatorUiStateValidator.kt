package com.amond.kmpbook.persistence

import com.amond.kmpbook.domain.model.CorporateActionKind
import com.amond.kmpbook.domain.model.CorporateActionNewsTransition
import com.amond.kmpbook.domain.model.CorporateActionSource
import com.amond.kmpbook.domain.model.CausalEconomicFactor
import com.amond.kmpbook.domain.model.CausalSignalDirection
import com.amond.kmpbook.domain.model.EventImpactCoveragePolicy
import com.amond.kmpbook.domain.model.EventImpactHorizon
import com.amond.kmpbook.domain.model.EventImpactTargetKind
import com.amond.kmpbook.domain.model.EventRecordKind
import com.amond.kmpbook.domain.model.EventTradingHaltKind
import com.amond.kmpbook.domain.model.EventScope
import com.amond.kmpbook.domain.model.EventSeverity
import com.amond.kmpbook.domain.model.EventType
import com.amond.kmpbook.domain.model.GameEvent
import com.amond.kmpbook.domain.model.GameEventImpact
import com.amond.kmpbook.domain.model.ImpactDirection
import com.amond.kmpbook.domain.model.IndustrySegment
import com.amond.kmpbook.domain.model.InstrumentTradingHalt
import com.amond.kmpbook.domain.model.InstrumentType
import com.amond.kmpbook.domain.model.InstrumentTerminationKind
import com.amond.kmpbook.domain.model.InstrumentTerminationValuationMethod
import com.amond.kmpbook.domain.model.InvestmentAlertStatus
import com.amond.kmpbook.domain.model.KrxCircuitBreakerPhase
import com.amond.kmpbook.domain.model.KrxSidecarPhase
import com.amond.kmpbook.domain.model.KrxViPhase
import com.amond.kmpbook.domain.model.ListingLifecycleStatus
import com.amond.kmpbook.domain.model.ListingLifecycleReason
import com.amond.kmpbook.domain.model.ListingLifecycleState
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.MarketActionKind
import com.amond.kmpbook.domain.model.MarketActionReference
import com.amond.kmpbook.domain.model.MarketActionTransition
import com.amond.kmpbook.domain.model.MarketIndexId
import com.amond.kmpbook.domain.model.OrderSide
import com.amond.kmpbook.domain.model.ListingLifecycleLedgerEvent
import com.amond.kmpbook.domain.model.ListingLifecycleEventKind
import com.amond.kmpbook.domain.model.ListingFinalDispositionType
import com.amond.kmpbook.domain.model.PublishedInstrumentTerminationNotice
import com.amond.kmpbook.domain.model.StockDefinition
import com.amond.kmpbook.domain.model.TradingHaltReason
import com.amond.kmpbook.domain.model.TradingHaltStatus
import com.amond.kmpbook.domain.model.TradeSettlementKind
import com.amond.kmpbook.domain.model.UsLuldPhase
import com.amond.kmpbook.domain.model.UsMwcbPhase
import com.amond.kmpbook.domain.model.investmentAlertOccurrenceId
import com.amond.kmpbook.domain.model.krxCircuitBreakerOccurrenceId
import com.amond.kmpbook.domain.model.krxSidecarOccurrenceId
import com.amond.kmpbook.domain.model.krxViOccurrenceId
import com.amond.kmpbook.domain.model.rawEffectiveTradingDate
import com.amond.kmpbook.domain.model.resolveInstrumentTerminationAtSessionClose
import com.amond.kmpbook.domain.model.scheduledTerminationOn
import com.amond.kmpbook.domain.model.usLuldOccurrenceId
import com.amond.kmpbook.domain.model.usMwcbOccurrenceId
import com.amond.kmpbook.domain.simulation.DefaultEventTemplates
import com.amond.kmpbook.domain.simulation.DeterministicRandom
import com.amond.kmpbook.domain.simulation.EventEngine
import com.amond.kmpbook.domain.simulation.ListingLifecyclePolicyCatalog
import com.amond.kmpbook.domain.simulation.ScheduledEventEngine
import com.amond.kmpbook.domain.time.GameCalendar
import com.amond.kmpbook.presentation.SimulatorUiState
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus

private val TERMINAL_LISTING_STATUSES: Set<ListingLifecycleStatus> = setOf(
    ListingLifecycleStatus.DELISTED,
    ListingLifecycleStatus.TERMINATED,
)

private val CURRENT_EVENT_TEMPLATE_IDS: Set<String> =
    DefaultEventTemplates.all.mapTo(linkedSetOf()) { it.id }

internal fun validateSimulatorUiState(state: SimulatorUiState): String? {
    if (state.turn < 0L) return "턴 번호가 음수입니다."
    if (state.nextSequence < 0L) return "다음 원장 시퀀스가 음수입니다."
    if (state.eventEngineSnapshot.sequence < 0L) return "이벤트 엔진 시퀀스가 음수입니다."
    if (state.stocks.map { it.id }.distinct().size != state.stocks.size) return "종목 ID가 중복되었습니다."
    val stocksById = state.stocks.associateBy { it.id }
    val stockIds = stocksById.keys
    val eventSchemaEngine = EventEngine(seed = 0L)
    val scheduledEventSchemaEngine = ScheduledEventEngine(
        DeterministicRandom.mixSeed(state.options.seed, ScheduledEventEngine.STREAM_ID),
    )
    val possibleEventTriggerKeys = eventSchemaEngine.possibleTriggerKeys(state.stocks)
    val eventCooldowns = state.eventEngineSnapshot.lastTriggeredEpochSeconds
    if (eventCooldowns.keys.any { it.isBlank() || it !in possibleEventTriggerKeys } ||
        eventCooldowns.values.any { epochSeconds ->
            epochSeconds < GameCalendar.startInstant.epochSeconds ||
                epochSeconds > state.currentTime.epochSeconds
        }
    ) {
        return "이벤트 엔진 쿨다운 키 또는 마지막 발생 시각이 현재 규칙·게임 시간과 일치하지 않습니다."
    }
    if (state.stocks.any { stock ->
            stock.industrySegments.any { (it as IndustrySegment?) == null }
        }
    ) {
        return "종목의 세부 산업 enum이 유효하지 않습니다."
    }
    if (state.selectedStockId != null && state.stocks.none { it.id == state.selectedStockId }) {
        return "선택 종목이 종목 목록에 없습니다."
    }
    if (state.cashByCurrency.values.any { !it.isFinite() || it < 0.0 }) return "현금 잔액이 유효하지 않습니다."
    if (state.holdings.any { (id, holding) -> id != holding.stockId }) return "보유 종목 맵 키가 일치하지 않습니다."
    if (state.quotes.any { (id, quote) -> id != quote.stockId }) return "시세 맵 키가 일치하지 않습니다."
    if (state.priceHistory.any { (id, bars) -> bars.any { it.stockId != id } }) {
        return "가격 히스토리 종목 키가 일치하지 않습니다."
    }
    if (state.portfolioSnapshots.any { snapshot ->
            snapshot.holdingCostBasisKrw.keys != snapshot.holdings.mapTo(linkedSetOf()) { it.stockId } ||
                snapshot.holdingCostBasisKrw.values.any { !it.isFinite() || it < 0.0 }
        }
    ) {
        return "포트폴리오 스냅샷에 모든 보유 종목의 FIFO 원가가 필요합니다."
    }
    if (state.pendingEtfReferenceReturns.any { (stockId, returnRate) ->
            stockId !in stockIds || !returnRate.isFinite()
        }
    ) {
        return "ETF 기초시장 이월 수익률이 유효하지 않습니다."
    }
    if (state.pendingClosedEventLogReturns.any { (stockId, logReturn) ->
            stockId !in stockIds || !logReturn.isFinite()
        }
    ) {
        return "폐장 중 이벤트 이월 수익률이 유효하지 않습니다."
    }
    val requiredIndexIds = MarketIndexId.entries.toSet()
    if (state.marketIndices.keys != requiredIndexIds ||
        state.marketIndices.any { (id, snapshot) -> id != snapshot.id }
    ) {
        return "대표 지수 현재값에 필수 지수가 없거나 맵 키와 지수 ID가 일치하지 않습니다."
    }
    if (state.marketIndexHistory.keys != requiredIndexIds ||
        state.marketIndexHistory.any { (id, values) ->
            values.isEmpty() || values.any { it.id != id || it.timestamp > state.currentTime } ||
                values.zipWithNext().any { (previous, next) -> previous.timestamp >= next.timestamp }
        }
    ) {
        return "대표 지수 이력에 필수 지수가 없거나 시간·ID 순서가 올바르지 않습니다."
    }
    if (state.annualTaxLedgers.any { (year, ledger) -> year != ledger.taxYear }) {
        return "연간 세금 원장의 연도 키가 일치하지 않습니다."
    }
    val tradesById = state.trades.associateBy { it.id }
    val tradeIds = tradesById.keys
    if (tradeIds.size != state.trades.size) return "체결 ID가 중복되었습니다."
    val transactionCostTradeIds = state.transactionCosts.map { it.tradeId }
    if (transactionCostTradeIds.toSet() != tradeIds ||
        transactionCostTradeIds.distinct().size != transactionCostTradeIds.size ||
        state.transactionCosts.any { cost ->
            val trade = tradesById[cost.tradeId]
            trade == null || trade.stockId != cost.stockId || trade.currency != cost.currency ||
                !cost.exchangeRateToKrw.isFinite() || cost.exchangeRateToKrw <= 0.0
        }
    ) {
        return "모든 체결에는 종목·통화가 일치하는 유효한 거래비용 원장이 하나씩 필요합니다."
    }
    if (state.taxExchangeRatesByTradeId.keys != tradeIds ||
        state.taxExchangeRatesByTradeId.any { (_, rate) -> !rate.isFinite() || rate <= 0.0 }
    ) {
        return "체결별 세무 환율 원장에 모든 체결의 유효한 환율이 필요합니다."
    }
    if (state.pendingTaxSettlementTradeIds.any { it !in tradeIds }) {
        return "미결제 세무 환율 원장에 알 수 없는 체결이 있습니다."
    }
    if (state.watchlistedStockIds.any { it !in stockIds }) {
        return "관심 종목에 알 수 없는 종목 ID가 있습니다."
    }
    if (state.pendingCorporateActions.map { it.id }.distinct().size != state.pendingCorporateActions.size ||
        state.pendingCorporateActions.any { it.stockId !in stockIds }
    ) {
        return "대기 기업행동 원장의 ID 또는 종목이 유효하지 않습니다."
    }
    if (state.corporateActionLedger.map { it.id }.distinct().size != state.corporateActionLedger.size ||
        state.corporateActionLedger.any { it.stockId !in stockIds }
    ) {
        return "적용 기업행동 원장의 ID 또는 종목이 유효하지 않습니다."
    }
    if (state.dividendLedger.map { it.id }.distinct().size != state.dividendLedger.size ||
        state.dividendLedger.any { entry ->
            entry.stockId !in stockIds || !entry.taxableIncomeAmount.isFinite() ||
                !entry.returnOfCapitalAmount.isFinite() || entry.taxableIncomeAmount < 0.0 ||
                entry.returnOfCapitalAmount < 0.0 || entry.excessReturnOfCapitalGainKrw < 0L
        }
    ) {
        return "분배 원장의 ID·종목·과세소득·원금환급 금액이 유효하지 않습니다."
    }
    val accountingSequences = buildList {
        state.trades.mapTo(this) { it.accountingSequence }
        state.dividendLedger.mapTo(this) { it.accountingSequence }
        state.corporateActionLedger.mapTo(this) { it.accountingSequence }
    }
    if (accountingSequences.any { it <= 0L || it >= state.nextSequence } ||
        accountingSequences.distinct().size != accountingSequences.size
    ) {
        return "회계 원장 시퀀스가 양수가 아니거나 중복되었거나 다음 시퀀스보다 작지 않습니다."
    }
    if (state.activeEvents.map { it.id }.distinct().size != state.activeEvents.size) {
        return "활성 이벤트 ID가 중복되었습니다."
    }
    if (state.newsEvents.map { it.id }.distinct().size != state.newsEvents.size) {
        return "뉴스 이벤트 ID가 중복되었습니다."
    }
    if (state.newsEvents.any { it.startsAt > state.currentTime }) {
        return "아직 발표되지 않은 이벤트가 뉴스 원장에 포함되었습니다."
    }
    if (state.eventEngineSnapshot.activeEvents.map { it.id }.distinct().size !=
        state.eventEngineSnapshot.activeEvents.size
    ) {
        return "이벤트 엔진 활성 이벤트 ID가 중복되었습니다."
    }
    if (state.eventEngineSnapshot.activeEvents.any { it.generatorTemplateId == null }) {
        return "이벤트 엔진 활성 이벤트에 생성 템플릿 참조가 없습니다."
    }
    val newsEventsById = state.newsEvents.associateBy(GameEvent::id)
    val expectedActiveEventsById = state.newsEvents
        .filter { event ->
            when {
                event.generatorTemplateId != null -> event.isActiveAt(state.currentTime)
                event.recordKind == EventRecordKind.SCHEDULED_RELEASE ->
                    state.currentTime >= event.effectStartsAt && state.currentTime < event.effectEndsAt
                else -> false
            }
        }
        .associateBy(GameEvent::id)
    val actualActiveEventsById = state.activeEvents.associateBy(GameEvent::id)
    val generatedActiveEventsById = state.eventEngineSnapshot.activeEvents.associateBy(GameEvent::id)
    val expectedGeneratedActiveEventsById = expectedActiveEventsById.values
        .filter { it.generatorTemplateId != null }
        .associateBy(GameEvent::id)
    if (actualActiveEventsById != expectedActiveEventsById ||
        generatedActiveEventsById != expectedGeneratedActiveEventsById ||
        state.activeEvents.any { event -> newsEventsById[event.id] != event }
    ) {
        return "뉴스 원장의 현재 영향 구간과 활성 이벤트·이벤트 엔진 상태가 동일하지 않습니다."
    }
    val generatedEvents = (state.newsEvents + state.eventEngineSnapshot.activeEvents)
        .distinctBy(GameEvent::id)
        .filter { it.generatorTemplateId != null }
    if (generatedEvents.any { event ->
            if (eventSchemaEngine.generatedEventInvariantViolation(event, state.stocks) != null) {
                true
            } else {
                val triggerKey = eventSchemaEngine.triggerKeyFor(event) ?: return@any true
                eventCooldowns[triggerKey]?.let { it >= event.startsAt.epochSeconds } != true
            }
        }
    ) {
        return "확률 뉴스의 생성 규칙·대상과 쿨다운 원장이 일치하지 않습니다."
    }
    if (state.newsEvents.any { event ->
            event.recordKind == EventRecordKind.SCHEDULED_RELEASE &&
                !scheduledEventSchemaEngine.isCanonicalNewsEvent(event, state.stocks)
        }
    ) {
        return "정기 발표 뉴스가 현재 일정·시드·종목 카탈로그에서 재생한 원본과 일치하지 않습니다."
    }
    validateCorporateActionNewsLineage(state, stocksById)?.let { violation ->
        return violation
    }
    listOf(
        "활성 이벤트" to state.activeEvents,
        "뉴스 이벤트" to state.newsEvents,
        "이벤트 엔진 활성 이벤트" to state.eventEngineSnapshot.activeEvents,
    ).forEach { (label, events) ->
        events.forEach { event ->
            validateGameEvent(event, stocksById, state.listingLifecycleLedger)?.let { error ->
                return "$label: $error"
            }
        }
    }
    val actionKeys = state.newsEvents.mapNotNull { event -> event.marketAction?.let { action ->
        Triple(action.kind, action.occurrenceId, action.transition)
    } }
    if (actionKeys.distinct().size != actionKeys.size) {
        return "시장조치 뉴스의 발생 ID와 전이 조합이 중복되었습니다."
    }
    val actionsByOccurrence = state.newsEvents.mapNotNull(GameEvent::marketAction)
        .groupBy { action -> action.kind to action.occurrenceId }
    actionsByOccurrence.forEach { (_, actions) ->
        actions.forEach { action ->
            val requiredPrecursors = when (action.kind to action.transition) {
                MarketActionKind.KRX_CIRCUIT_BREAKER to MarketActionTransition.REOPENED,
                MarketActionKind.US_MARKET_WIDE_CIRCUIT_BREAKER to MarketActionTransition.REOPENED,
                MarketActionKind.US_LIMIT_UP_LIMIT_DOWN to MarketActionTransition.REOPENED,
                -> setOf(MarketActionTransition.HALT_STARTED)
                MarketActionKind.KRX_SIDECAR to MarketActionTransition.RELEASED ->
                    setOf(MarketActionTransition.PROGRAM_FLOW_SUSPENDED)
                MarketActionKind.INSTRUMENT_TRADING_HALT to MarketActionTransition.RELEASED ->
                    setOf(MarketActionTransition.HALT_SCHEDULED, MarketActionTransition.HALT_STARTED)
                MarketActionKind.INVESTMENT_ALERT to MarketActionTransition.RELEASE_ANNOUNCED ->
                    setOf(MarketActionTransition.DESIGNATED)
                else -> emptySet()
            }
            if (requiredPrecursors.isNotEmpty() && actions.none { precursor ->
                    precursor.transition in requiredPrecursors &&
                        precursor.announcedAt <= action.announcedAt &&
                        precursor.effectiveAt <= action.effectiveAt
                }
            ) {
                return "시장조치 ${action.kind}의 ${action.transition} 전이에 같은 발생 ID의 선행 조치가 없습니다."
            }
        }
    }

    val listings = state.listingLifecycleStates
    if (listings.keys != stockIds) {
        return "모든 종목의 현재 상장 생명주기 상태가 필요합니다."
    }
    if (listings.any { (stockId, listing) -> stockId != listing.stockId }) {
        return "상장 생명주기 맵 키와 상태 종목 ID가 일치하지 않습니다."
    }
    if (listings.any { (stockId, listing) ->
            val stock = stocksById[stockId]
            stock == null || stock.market != listing.market || stock.instrumentType != listing.instrumentType
        }
    ) {
        return "상장 생명주기 상태가 종목 시장·상품 유형과 일치하지 않습니다."
    }
    listings.forEach { (stockId, listing) ->
        listing.finalDisposition?.semanticInvariantViolation()?.let { violation ->
            return "${stockId}의 최종 잔고 처분이 유효하지 않습니다: $violation"
        }
    }
    if (listings.values.any { listing ->
            listing.lastEvaluatedTradingDate?.let { it > state.currentDate } == true
        }
    ) {
        return "상장 생명주기 최종 평가일이 현재 게임 날짜보다 미래입니다."
    }
    if (listings.values.any { listing ->
            listing.status in TERMINAL_LISTING_STATUSES && listing.finalDisposition == null
        }
    ) {
        return "최종 상장 상태에 잔고 처분 방식이 없습니다."
    }
    if (listings.any { (stockId, listing) ->
            val disposition = listing.finalDisposition ?: return@any false
            val stock = stocksById.getValue(stockId)
            val entitlementMissing = disposition.entitledQuantity == null ||
                disposition.entitledCostBasis == null
            when {
                disposition.effectiveOn > state.currentDate -> true
                listing.settlementDueOn != disposition.settlementDueOn -> true
                listing.status == ListingLifecycleStatus.LIQUIDATION_PENDING -> {
                    val holding = state.holdings[stockId]
                    val entitlementDoesNotMatchHolding = holding?.let {
                        it.currency != stock.currency ||
                            !it.quantity.isFinite() || it.quantity <= 0.0 ||
                            !it.averagePrice.isFinite() || it.averagePrice < 0.0 ||
                            disposition.entitledQuantity != it.quantity ||
                            disposition.entitledCostBasis != it.costBasis
                    } ?: (disposition.entitledQuantity != 0.0 || disposition.entitledCostBasis != 0.0)
                    disposition.type != ListingFinalDispositionType.CASH_LIQUIDATION ||
                        entitlementMissing ||
                        entitlementDoesNotMatchHolding
                }
                listing.status == ListingLifecycleStatus.DELISTED ->
                    disposition.type == ListingFinalDispositionType.CASH_LIQUIDATION ||
                        stock.instrumentType == InstrumentType.ETN
                listing.status == ListingLifecycleStatus.TERMINATED -> when (disposition.type) {
                    ListingFinalDispositionType.CASH_LIQUIDATION ->
                        entitlementMissing ||
                            requireNotNull(disposition.settlementDueOn) > state.currentDate
                    else -> stock.instrumentType != InstrumentType.ETN
                }
                else -> true
            }
        }
    ) {
        return "최종 잔고 처분의 상태·효력일·지급일·확정 권리·결제 통화가 종목과 일치하지 않습니다."
    }
    if (listings.any { (stockId, listing) ->
            val occurrenceId = listing.controllingTerminationOccurrenceId
            val priority = listing.controllingTerminationNoticePriority
            val rawEffectiveOn = listing.controllingTerminationRawEffectiveOn
            val orderlyStage = listing.activeReason.isOrderlyTerminationReason() &&
                listing.status.isOrderlyTerminationStatus()
            occurrenceId?.isBlank() == true || priority?.let { it < 0 } == true ||
                (occurrenceId != null) != orderlyStage || (priority != null) != orderlyStage ||
                (rawEffectiveOn != null) != orderlyStage ||
                !listing.orderlyTerminationReasonMatches(stocksById.getValue(stockId).instrumentType) ||
                occurrenceId != null && !state.newsEvents.hasExactTerminationNotice(
                    occurrenceId = occurrenceId,
                    stock = stocksById.getValue(stockId),
                    priority = priority,
                    rawEffectiveOn = rawEffectiveOn,
                )
        }
    ) {
        return "상품 종료 상태의 사유·단계·상품 유형·지배 공시 ID가 일치하지 않습니다."
    }

    val lifecycleLedger = state.listingLifecycleLedger
    if (lifecycleLedger.any { it.stockId !in stockIds }) {
        return "상장 생명주기 원장에 알 수 없는 종목 ID가 있습니다."
    }
    if (lifecycleLedger.any { it.reason == null }) {
        return "상장 생명주기 원장의 모든 전이에는 직접 원인이 필요합니다."
    }
    if (lifecycleLedger.any { it.sequence <= 0L }) return "상장 생명주기 원장 시퀀스가 양수가 아닙니다."
    if (lifecycleLedger.map { it.id }.distinct().size != lifecycleLedger.size) {
        return "상장 생명주기 원장 이벤트 ID가 중복되었습니다."
    }
    lifecycleLedger.forEach { ledgerEvent ->
        ledgerEvent.disposition?.semanticInvariantViolation()?.let { violation ->
            return "${ledgerEvent.id}의 최종 잔고 처분이 유효하지 않습니다: $violation"
        }
    }
    if (lifecycleLedger.any { ledgerEvent ->
            val disposition = ledgerEvent.disposition
            when (ledgerEvent.kind) {
                ListingLifecycleEventKind.LIQUIDATION_STARTED ->
                    disposition == null ||
                        disposition.type != ListingFinalDispositionType.CASH_LIQUIDATION ||
                        disposition.entitledQuantity == null || disposition.entitledCostBasis == null ||
                        disposition.effectiveOn != ledgerEvent.tradingDate ||
                        disposition.settlementDueOn != ledgerEvent.deadline ||
                        ledgerEvent.toStatus != ListingLifecycleStatus.LIQUIDATION_PENDING

                ListingLifecycleEventKind.DELISTED ->
                    disposition == null ||
                        disposition.type == ListingFinalDispositionType.CASH_LIQUIDATION ||
                        disposition.effectiveOn != ledgerEvent.tradingDate ||
                        ledgerEvent.deadline != null ||
                        ledgerEvent.toStatus != ListingLifecycleStatus.DELISTED

                ListingLifecycleEventKind.TERMINATED -> when {
                    disposition == null || ledgerEvent.deadline != null ||
                        ledgerEvent.toStatus != ListingLifecycleStatus.TERMINATED -> true
                    disposition.type == ListingFinalDispositionType.CASH_LIQUIDATION ->
                        disposition.entitledQuantity == null || disposition.entitledCostBasis == null ||
                            requireNotNull(disposition.settlementDueOn) > ledgerEvent.tradingDate
                    else -> disposition.effectiveOn != ledgerEvent.tradingDate ||
                        stocksById.getValue(ledgerEvent.stockId).instrumentType != InstrumentType.ETN
                }

                else -> disposition != null
            }
        }
    ) {
        return "최종 잔고 처분이 상장 원장의 전이·효력일·지급일·확정 권리와 일치하지 않습니다."
    }
    if (lifecycleLedger.any { ledgerEvent ->
            val occurrenceId = ledgerEvent.controllingTerminationOccurrenceId
            val priority = ledgerEvent.controllingTerminationNoticePriority
            val rawEffectiveOn = ledgerEvent.controllingTerminationRawEffectiveOn
            val orderlyStage = ledgerEvent.reason.isOrderlyTerminationReason() &&
                ledgerEvent.toStatus.isOrderlyTerminationStatus()
            val instrumentType = stocksById.getValue(ledgerEvent.stockId).instrumentType
            occurrenceId?.isBlank() == true || priority?.let { it < 0 } == true ||
                (occurrenceId != null) != orderlyStage || (priority != null) != orderlyStage ||
                (rawEffectiveOn != null) != orderlyStage ||
                !ledgerEvent.reason.orderlyTerminationReasonMatches(instrumentType) ||
                occurrenceId != null && !state.newsEvents.hasExactTerminationNotice(
                    occurrenceId = occurrenceId,
                    stock = stocksById.getValue(ledgerEvent.stockId),
                    priority = priority,
                    rawEffectiveOn = rawEffectiveOn,
                )
        }
    ) {
        return "상품 종료 원장의 사유·단계·상품 유형·지배 공시 ID가 일치하지 않습니다."
    }
    if (lifecycleLedger.any { ledgerEvent ->
            val occurrenceId = ledgerEvent.controllingTerminationOccurrenceId ?: return@any false
            val stock = stocksById.getValue(ledgerEvent.stockId)
            val notice = state.newsEvents.exactTerminationNotice(occurrenceId, stock) ?: return@any true
            val profile = ListingLifecyclePolicyCatalog[state.listingLifecycleStates.getValue(stock.id).profileId]
            when (ledgerEvent.kind) {
                ListingLifecycleEventKind.DELISTING_SCHEDULED ->
                    ledgerEvent.deadline != notice.scheduledTerminationOn(stock, ledgerEvent.tradingDate) ||
                        ledgerEvent.disposition != null
                ListingLifecycleEventKind.LIQUIDATION_STARTED -> {
                    val disposition = ledgerEvent.disposition
                    val expectedSettlementOn = ledgerEvent.tradingDate.plus(
                        profile.liquidationSettlementCalendarDays,
                        DateTimeUnit.DAY,
                    )
                    disposition?.type != ListingFinalDispositionType.CASH_LIQUIDATION ||
                        disposition.effectiveOn != ledgerEvent.tradingDate ||
                        disposition.settlementDueOn != expectedSettlementOn ||
                        ledgerEvent.deadline != expectedSettlementOn
                }
                ListingLifecycleEventKind.TERMINATED -> {
                    val disposition = ledgerEvent.disposition
                    disposition?.type != ListingFinalDispositionType.CASH_LIQUIDATION ||
                        disposition.settlementDueOn?.let { ledgerEvent.tradingDate < it } != false
                }
                else -> false
            }
        }
    ) {
        return "상품 종료 원장의 예정일·평가 효력일·지급일이 지배 공시와 현재 정책에서 파생되지 않았습니다."
    }
    val ledgerByStock = lifecycleLedger.groupBy { it.stockId }
    if (ledgerByStock.values.any { events ->
            events.withIndex().any { (index, event) -> event.sequence != index + 1L } ||
                events.any { it.tradingDate > state.currentDate } ||
                events.zipWithNext().any { (previous, next) ->
                    previous.tradingDate > next.tradingDate ||
                        previous.toStatus != next.fromStatus ||
                        previous.tradingDate == next.tradingDate && !(
                        previous.kind == ListingLifecycleEventKind.DELISTING_SCHEDULED &&
                            next.kind == ListingLifecycleEventKind.LIQUIDATION_STARTED &&
                            previous.controllingTerminationOccurrenceId ==
                            next.controllingTerminationOccurrenceId
                        )
                } ||
                events.firstOrNull()?.fromStatus?.let { it != ListingLifecycleStatus.LISTED } == true ||
                events.any { event -> event.toStatus != event.kind.expectedListingStatus() }
        }
    ) {
        return "상장 생명주기 원장의 연속 시퀀스·거래일·상태 전이가 엔진 규칙과 일치하지 않습니다."
    }
    for ((stockId, events) in ledgerByStock) {
        val stock = stocksById.getValue(stockId)
        var incumbentOccurrenceId: String? = null
        for (event in events) {
            val orderlyStage = event.reason.isOrderlyTerminationReason() &&
                event.toStatus.isOrderlyTerminationStatus()
            if (event.kind in setOf(
                    ListingLifecycleEventKind.DELISTING_SCHEDULED,
                    ListingLifecycleEventKind.LIQUIDATION_STARTED,
                ) && orderlyStage
            ) {
                val decision = runCatching {
                    resolveInstrumentTerminationAtSessionClose(
                        stock = stock,
                        events = state.newsEvents,
                        evaluatedOn = event.tradingDate,
                        incumbentOccurrenceId = incumbentOccurrenceId,
                    )
                }.getOrNull()
                val controllerMismatch = decision?.let { expected ->
                    event.controllingTerminationOccurrenceId != expected.notice.event.id ||
                        event.controllingTerminationNoticePriority != expected.notice.terms.kind.noticePriority ||
                        event.controllingTerminationRawEffectiveOn != expected.rawEffectiveOn
                } != false
                val liquidationBreaksFrozenLineage =
                    event.kind == ListingLifecycleEventKind.LIQUIDATION_STARTED &&
                        event.controllingTerminationOccurrenceId != incumbentOccurrenceId
                val scheduleDateMismatch = event.kind == ListingLifecycleEventKind.DELISTING_SCHEDULED &&
                    event.deadline != decision?.scheduledTerminationOn
                if (controllerMismatch ||
                    liquidationBreaksFrozenLineage || scheduleDateMismatch
                ) {
                    return "${event.id}의 상품 종료 지배 공시와 예정일이 해당 거래일 종가의 canonical 결정과 일치하지 않습니다."
                }
            } else if (orderlyStage &&
                event.controllingTerminationOccurrenceId != incumbentOccurrenceId
            ) {
                return "${event.id}의 상품 종료 계보가 직전 확정 공시와 연속되지 않습니다."
            }
            incumbentOccurrenceId = event.controllingTerminationOccurrenceId
        }
    }
    for ((stockId, listing) in listings) {
        val disposition = listing.finalDisposition
            ?.takeIf { listing.status == ListingLifecycleStatus.TERMINATED }
            ?.takeIf { it.type == ListingFinalDispositionType.CASH_LIQUIDATION }
            ?: continue
        val quantity = requireNotNull(disposition.entitledQuantity)
        val terminalLedgerEvent = ledgerByStock[stockId]
            ?.lastOrNull()
            ?.takeIf { it.kind == ListingLifecycleEventKind.TERMINATED }
            ?: return "${stockId}의 계약상 현금청산에 상품 종료 원장이 없습니다."
        if (stockId in state.holdings || state.fifoCostBasisBook.lots.any { it.stockId == stockId }) {
            return "${stockId}의 계약상 현금청산 뒤에도 보유 잔고 또는 FIFO 권리가 남아 있습니다."
        }
        val settlementTrades = state.trades.filter { trade ->
            trade.stockId == stockId &&
                trade.side == OrderSide.SELL &&
                trade.settlementKind == TradeSettlementKind.CONTRACTUAL_CASH_SETTLEMENT
        }
        val settlementTradeMatches = settlementTrades.singleOrNull()?.let { trade ->
            quantity > 0.0 &&
                trade.quantity == quantity &&
                trade.price == disposition.cashPerUnit &&
                trade.currency == stocksById.getValue(stockId).currency &&
                trade.settlementDateOverride == terminalLedgerEvent.tradingDate
        } ?: (quantity == 0.0 && settlementTrades.isEmpty())
        if (!settlementTradeMatches) {
            return "${stockId}의 계약상 현금청산 체결이 확정 권리·단가·통화·실제 지급일과 일치하지 않습니다."
        }
    }
    if (ledgerByStock.values.any { events ->
            events.zipWithNext().any { (previous, next) ->
                val previousId = previous.controllingTerminationOccurrenceId
                val nextId = next.controllingTerminationOccurrenceId
                when {
                    previousId == null || nextId == null -> false
                    previousId == nextId ->
                        previous.controllingTerminationNoticePriority !=
                            next.controllingTerminationNoticePriority ||
                            previous.controllingTerminationRawEffectiveOn !=
                            next.controllingTerminationRawEffectiveOn
                    else -> {
                        val previousRaw = requireNotNull(previous.controllingTerminationRawEffectiveOn)
                        val nextRaw = requireNotNull(next.controllingTerminationRawEffectiveOn)
                        val preempts = nextRaw < previousRaw ||
                            nextRaw == previousRaw &&
                            requireNotNull(next.controllingTerminationNoticePriority) <
                            requireNotNull(previous.controllingTerminationNoticePriority)
                        next.kind != ListingLifecycleEventKind.DELISTING_SCHEDULED ||
                            !preempts
                    }
                }
            }
        }
    ) {
        return "상품 종료 지배 공시는 더 이른 효력일 또는 같은 날의 높은 계약 우선순위로만 교체할 수 있습니다."
    }
    if (ledgerByStock.values.any { events ->
            events.groupBy(ListingLifecycleLedgerEvent::controllingTerminationOccurrenceId)
                .filterKeys { it != null }
                .values
                .any { lineage ->
                    val dispositions = lineage.mapNotNull(ListingLifecycleLedgerEvent::disposition)
                    dispositions.isNotEmpty() && dispositions.distinct().size != 1
                }
        }
    ) {
        return "같은 상품 종료 공시의 확정 청산 조건이 원장 단계 사이에서 달라졌습니다."
    }
    if (listings.any { (stockId, listing) ->
            val lastLedgerEvent = ledgerByStock[stockId]?.lastOrNull()
            val expectedDeadline = when (listing.status) {
                ListingLifecycleStatus.DEFICIENCY_NOTICE -> listing.cureDeadline
                ListingLifecycleStatus.UNDER_REVIEW,
                ListingLifecycleStatus.TRADING_SUSPENDED,
                -> listing.reviewDeadline
                ListingLifecycleStatus.DELISTING_SCHEDULED -> listing.scheduledDelistingOn
                ListingLifecycleStatus.LIQUIDATION_PENDING -> listing.settlementDueOn
                else -> null
            }
            val activeReasonMismatch = if (listing.status == ListingLifecycleStatus.LISTED) {
                listing.activeReason != null
            } else {
                listing.activeReason == null || lastLedgerEvent?.reason != listing.activeReason
            }
            listing.ledgerSequence != (lastLedgerEvent?.sequence ?: 0L) ||
                activeReasonMismatch ||
                lastLedgerEvent != null && (
                    listing.status != lastLedgerEvent.toStatus ||
                        listing.finalDisposition != lastLedgerEvent.disposition ||
                        expectedDeadline != lastLedgerEvent.deadline ||
                        listing.controllingTerminationOccurrenceId !=
                        lastLedgerEvent.controllingTerminationOccurrenceId ||
                        listing.controllingTerminationNoticePriority !=
                        lastLedgerEvent.controllingTerminationNoticePriority ||
                        listing.controllingTerminationRawEffectiveOn !=
                        lastLedgerEvent.controllingTerminationRawEffectiveOn
                    )
        }
    ) {
        return "상장 생명주기 상태와 원장의 마지막 시퀀스·상태·원인이 일치하지 않습니다."
    }

    val protection = state.tradingProtectionSnapshot
    val krxMarkets = Market.entries.filter(Market::isKorean).toSet()
    val krxStockIds = stocksById.filterValues { it.market.isKorean }.keys
    val usStockIds = stocksById.filterValues { it.market.isUnitedStates }.keys
    if (protection.krxCircuitBreakers.keys != krxMarkets ||
        protection.krxCircuitBreakers.any { (market, protectionState) ->
            market != protectionState.market
        }
    ) {
        return "KRX 서킷브레이커에 필수 시장 상태가 없거나 맵 키가 일치하지 않습니다."
    }
    if (protection.krxSidecars.keys != krxMarkets ||
        protection.krxSidecars.any { (market, protectionState) ->
            market != protectionState.market
        }
    ) {
        return "KRX 사이드카에 필수 시장 상태가 없거나 맵 키가 일치하지 않습니다."
    }
    if (protection.krxVolatilityInterruptions.keys != krxStockIds ||
        protection.krxVolatilityInterruptions.any { (stockId, protectionState) ->
            val stock = stocksById[stockId]
            stockId != protectionState.stockId || stock == null || stock.market != protectionState.market
        }
    ) {
        return "KRX VI에 필수 종목 상태가 없거나 맵 키·종목·시장이 일치하지 않습니다."
    }
    if (protection.instrumentTradingHalts.any { (stockId, protectionState) ->
            stockId != protectionState.stockId || stockId !in stockIds ||
                invalidInstrumentTradingHalt(protectionState)
        }
    ) {
        return "종목 거래정지의 발생 ID·맵 키·종목 ID가 유효하지 않습니다."
    }
    if (protection.scheduledInstrumentTradingHalts.any { (scheduleId, protectionState) ->
            scheduleId.isBlank() || scheduleId != protectionState.occurrenceId ||
                protectionState.stockId !in stockIds ||
                protectionState.scheduledReleaseAt == null ||
                protectionState.status != TradingHaltStatus.ACTIVE ||
                invalidInstrumentTradingHalt(protectionState)
        }
    ) {
        return "예정 종목 거래정지의 발생 ID·종목·해제 시각이 올바르지 않습니다."
    }
    val haltOccurrenceIds = protection.instrumentTradingHalts.values.map { it.occurrenceId } +
        protection.scheduledInstrumentTradingHalts.values.map { it.occurrenceId }
    if (haltOccurrenceIds.distinct().size != haltOccurrenceIds.size) {
        return "현재·예정 종목 거래정지의 발생 ID가 중복되었습니다."
    }
    if (protection.investmentAlerts.any { (stockId, protectionState) ->
            stockId != protectionState.stockId || stocksById[stockId]?.market?.isKorean != true
        }
    ) {
        return "투자경보 맵 키와 KRX 종목 ID가 일치하지 않습니다."
    }
    if (protection.usLuldStates.keys != usStockIds ||
        protection.usLuldStates.any { (stockId, protectionState) ->
            val stock = stocksById[stockId]
            stockId != protectionState.stockId || stock == null || stock.market != protectionState.primaryMarket
        }
    ) {
        return "미국 LULD에 필수 종목 상태가 없거나 맵 키·종목·주 상장시장이 일치하지 않습니다."
    }
    val mwcb = protection.usMarketWideCircuitBreaker
        ?: return "미국 MWCB 상태가 없습니다."
    val requiredVenues = Market.entries.filter(Market::isUnitedStates).toSet()
    if (mwcb.venueStatuses.keys != requiredVenues) {
        return "미국 MWCB 상태에 필수 주 상장시장이 모두 포함되지 않았습니다."
    }
    if (mwcb.venueStatuses.any { (market, venue) ->
            market != venue.market || !market.isUnitedStates
        }
    ) {
        return "미국 MWCB 거래소 맵 키와 내부 시장이 일치하지 않습니다."
    }

    val marketActions = state.newsEvents.mapNotNull(GameEvent::marketAction)
    fun hasMarketAction(
        kind: MarketActionKind,
        occurrenceId: String,
        predicate: (MarketActionReference) -> Boolean,
    ): Boolean = marketActions.any { action ->
        action.kind == kind && action.occurrenceId == occurrenceId && predicate(action)
    }

    val krxCircuitBreakerLineageInvalid = protection.krxCircuitBreakers.values.any { current ->
        if (current.phase == KrxCircuitBreakerPhase.NORMAL) return@any false
        val level = current.activeLevel ?: return@any true
        val triggeredAt = current.triggeredAt ?: return@any true
        val occurrenceId = krxCircuitBreakerOccurrenceId(current.market, level, triggeredAt)
        !hasMarketAction(MarketActionKind.KRX_CIRCUIT_BREAKER, occurrenceId) { action ->
            action.effectiveAt == triggeredAt && action.markets == setOf(current.market) &&
                action.stage == level.ordinal + 1 && action.transition in setOf(
                MarketActionTransition.HALT_STARTED,
                MarketActionTransition.MARKET_CLOSED_FOR_DAY,
            )
        }
    }
    val krxSidecarLineageInvalid = protection.krxSidecars.values.any { current ->
        if (current.phase != KrxSidecarPhase.PROGRAM_FLOW_SUSPENDED) return@any false
        val triggeredAt = current.triggeredAt ?: return@any true
        val occurrenceId = krxSidecarOccurrenceId(current.market, triggeredAt)
        !hasMarketAction(MarketActionKind.KRX_SIDECAR, occurrenceId) { action ->
            action.transition == MarketActionTransition.PROGRAM_FLOW_SUSPENDED &&
                action.effectiveAt == triggeredAt && action.markets == setOf(current.market)
        }
    }
    val krxViLineageInvalid = protection.krxVolatilityInterruptions.values.any { current ->
        if (current.phase != KrxViPhase.CALL_AUCTION) return@any false
        val triggeredAt = current.triggeredAt ?: return@any true
        val occurrenceId = krxViOccurrenceId(current.stockId, current.triggerCount, triggeredAt)
        !hasMarketAction(MarketActionKind.KRX_VOLATILITY_INTERRUPTION, occurrenceId) { action ->
            action.transition == MarketActionTransition.CALL_AUCTION_STARTED &&
                action.effectiveAt == triggeredAt && action.stockId == current.stockId &&
                action.markets == setOf(current.market) && action.triggerSequence == current.triggerCount
        }
    }
    val usMwcbLineageInvalid = if (mwcb.phase == UsMwcbPhase.NORMAL) {
        false
    } else {
        val level = mwcb.activeLevel
        val triggeredAt = mwcb.triggeredAt
        if (level == null || triggeredAt == null) {
            true
        } else {
            val occurrenceId = usMwcbOccurrenceId(level, triggeredAt)
            !hasMarketAction(MarketActionKind.US_MARKET_WIDE_CIRCUIT_BREAKER, occurrenceId) { action ->
                action.effectiveAt == triggeredAt && action.stage == level.ordinal + 1 &&
                    action.transition in setOf(
                    MarketActionTransition.HALT_STARTED,
                    MarketActionTransition.MARKET_CLOSED_FOR_DAY,
                )
            }
        }
    }
    val usLuldLineageInvalid = protection.usLuldStates.values.any { current ->
        if (current.phase !in setOf(
                UsLuldPhase.TRADING_PAUSE,
                UsLuldPhase.REOPENING_AUCTION,
                UsLuldPhase.CLOSING_AUCTION_ONLY,
            )
        ) {
            return@any false
        }
        val pauseStartedAt = current.pauseStartedAt ?: return@any true
        val occurrenceId = usLuldOccurrenceId(current.stockId, pauseStartedAt)
        !hasMarketAction(MarketActionKind.US_LIMIT_UP_LIMIT_DOWN, occurrenceId) { action ->
            action.stockId == current.stockId && action.markets == setOf(current.primaryMarket) &&
                action.transition in setOf(
                MarketActionTransition.HALT_STARTED,
                MarketActionTransition.CLOSING_AUCTION_STARTED,
            )
        }
    }
    val instrumentHaltLineageInvalid = (
        protection.instrumentTradingHalts.values + protection.scheduledInstrumentTradingHalts.values
        ).any { halt ->
        if (halt.status != TradingHaltStatus.ACTIVE) return@any false
        if (halt.reason in setOf(
                TradingHaltReason.LISTING_MAINTENANCE_REVIEW,
                TradingHaltReason.DELISTING_PROCESS,
            )
        ) {
            return@any listings[halt.stockId]?.isTradable != false
        }
        !hasMarketAction(MarketActionKind.INSTRUMENT_TRADING_HALT, halt.occurrenceId) { action ->
            action.stockId == halt.stockId && action.effectiveAt == halt.startedAt &&
                action.transition in setOf(
                MarketActionTransition.HALT_SCHEDULED,
                MarketActionTransition.HALT_STARTED,
            )
        }
    }
    val investmentAlertLineageInvalid = protection.investmentAlerts.values.any { designation ->
        if (designation.status != InvestmentAlertStatus.ACTIVE) return@any false
        val occurrenceId = investmentAlertOccurrenceId(designation.stockId, designation.designatedAt)
        !hasMarketAction(MarketActionKind.INVESTMENT_ALERT, occurrenceId) { action ->
            action.transition == MarketActionTransition.DESIGNATED &&
                action.stockId == designation.stockId && action.alertLevel == designation.level &&
                action.effectiveOn == designation.designatedOn
        }
    }
    if (krxCircuitBreakerLineageInvalid || krxSidecarLineageInvalid || krxViLineageInvalid ||
        usMwcbLineageInvalid || usLuldLineageInvalid || instrumentHaltLineageInvalid ||
        investmentAlertLineageInvalid
    ) {
        return "현재 거래 보호상태와 시장조치 뉴스의 발생 계보가 일치하지 않습니다."
    }

    if (state.dailyTradingSurveillance.keys != stockIds ||
        state.dailyTradingSurveillance.any { (_, points) ->
                points.zipWithNext().any { (previous, next) -> previous.date >= next.date } ||
                points.any { it.date > state.currentDate }
        }
    ) {
        return "일별 시장감시 이력의 종목·날짜 순서가 올바르지 않습니다."
    }
    return null
}

private fun invalidInstrumentTradingHalt(halt: InstrumentTradingHalt): Boolean {
    val reason = halt.reason as TradingHaltReason?
    val status = halt.status as TradingHaltStatus?
    if (halt.occurrenceId.isBlank() || halt.stockId.isBlank() || halt.detail.isBlank() ||
        reason == null || reason !in TradingHaltReason.entries ||
        status == null || status !in TradingHaltStatus.entries ||
        halt.scheduledReleaseAt?.let { it < halt.startedAt } == true
    ) {
        return true
    }
    return when (status) {
        TradingHaltStatus.ACTIVE -> halt.releasedAt != null
        TradingHaltStatus.RELEASED -> halt.releasedAt?.let { it < halt.startedAt } != false
    }
}

private fun ListingLifecycleReason?.isOrderlyTerminationReason(): Boolean = this in setOf(
    ListingLifecycleReason.ETF_VOLUNTARY_LIQUIDATION,
    ListingLifecycleReason.ETN_MATURITY_OR_EARLY_REDEMPTION,
)

private fun ListingLifecycleStatus.isOrderlyTerminationStatus(): Boolean = this in setOf(
    ListingLifecycleStatus.DELISTING_SCHEDULED,
    ListingLifecycleStatus.LIQUIDATION_PENDING,
    ListingLifecycleStatus.TERMINATED,
)

private fun ListingLifecycleEventKind.expectedListingStatus(): ListingLifecycleStatus = when (this) {
    ListingLifecycleEventKind.DEFICIENCY_DESIGNATED,
    ListingLifecycleEventKind.DEFICIENCY_REDESIGNATED,
    -> ListingLifecycleStatus.DEFICIENCY_NOTICE
    ListingLifecycleEventKind.REVIEW_STARTED -> ListingLifecycleStatus.UNDER_REVIEW
    ListingLifecycleEventKind.TRADING_SUSPENDED -> ListingLifecycleStatus.TRADING_SUSPENDED
    ListingLifecycleEventKind.DEFICIENCY_CURED,
    ListingLifecycleEventKind.TRADING_RESUMED,
    -> ListingLifecycleStatus.LISTED
    ListingLifecycleEventKind.DELISTING_SCHEDULED -> ListingLifecycleStatus.DELISTING_SCHEDULED
    ListingLifecycleEventKind.LIQUIDATION_STARTED -> ListingLifecycleStatus.LIQUIDATION_PENDING
    ListingLifecycleEventKind.DELISTED -> ListingLifecycleStatus.DELISTED
    ListingLifecycleEventKind.TERMINATED -> ListingLifecycleStatus.TERMINATED
}

private fun ListingLifecycleReason?.orderlyTerminationReasonMatches(instrumentType: InstrumentType): Boolean = when {
    !isOrderlyTerminationReason() -> true
    this == ListingLifecycleReason.ETN_MATURITY_OR_EARLY_REDEMPTION -> instrumentType == InstrumentType.ETN
    else -> instrumentType in setOf(InstrumentType.ETF, InstrumentType.CLOSED_END_FUND)
}

private fun ListingLifecycleState.orderlyTerminationReasonMatches(instrumentType: InstrumentType): Boolean =
    activeReason.orderlyTerminationReasonMatches(instrumentType)

private fun List<GameEvent>.hasExactTerminationNotice(
    occurrenceId: String,
    stock: StockDefinition,
    priority: Int?,
    rawEffectiveOn: kotlinx.datetime.LocalDate?,
): Boolean =
    exactTerminationNotice(occurrenceId, stock)?.let { notice ->
        notice.terms.kind.noticePriority == priority &&
            notice.rawEffectiveOn == rawEffectiveOn
    } == true

private fun List<GameEvent>.exactTerminationNotice(
    occurrenceId: String,
    stock: StockDefinition,
): PublishedInstrumentTerminationNotice? =
    singleOrNull { event -> event.id == occurrenceId }?.let { event ->
        val terms = event.instrumentTermination ?: return@let null
        if (stock.id !in event.affectedStockIds || !terms.isEligibleFor(stock)) return@let null
        PublishedInstrumentTerminationNotice(
            event = event,
            terms = terms,
            rawEffectiveOn = runCatching { terms.rawEffectiveTradingDate(stock) }.getOrNull()
                ?: return@let null,
        )
    }

/**
 * 기업행동의 상태는 뉴스 만료 시각이 아니라 공시 → 적용/취소 계보로 검증한다.
 * 모든 대기 항목에는 공시가, 모든 적용 원장에는 공시와 완료 전이가, 상장 종료로
 * 취소된 항목에는 공시와 해당 상장 원장에 묶인 취소 전이가 정확히 하나씩 있어야 한다.
 */
private fun validateCorporateActionNewsLineage(
    state: SimulatorUiState,
    stocksById: Map<String, StockDefinition>,
): String? {
    val pendingById = state.pendingCorporateActions.associateBy { action -> action.id }
    val appliedById = state.corporateActionLedger.associateBy { action -> action.id }
    if (pendingById.keys.any(appliedById::containsKey)) {
        return "같은 기업행동이 대기 원장과 적용 원장에 동시에 존재합니다."
    }

    val references = state.newsEvents.mapNotNull { event ->
        event.corporateActionReference?.let { reference -> event to reference }
    }
    references.forEach { (event, reference) ->
        reference.semanticInvariantViolation()?.let { violation ->
            return "${event.id}의 기업행동 원장 참조가 유효하지 않습니다: $violation"
        }
    }
    val transitionKeys = references.map { (_, reference) ->
        reference.occurrenceId to reference.transition
    }
    if (transitionKeys.distinct().size != transitionKeys.size) {
        return "같은 기업행동 발생 ID에 동일한 뉴스 전이가 중복되었습니다."
    }

    val referencesByOccurrence = references.groupBy { (_, reference) -> reference.occurrenceId }
    val occurrenceIds = buildSet {
        addAll(pendingById.keys)
        addAll(appliedById.keys)
        addAll(referencesByOccurrence.keys)
    }
    occurrenceIds.forEach { occurrenceId ->
        val lineage = referencesByOccurrence[occurrenceId].orEmpty()
        val announcements = lineage.filter { (_, reference) ->
            reference.transition == CorporateActionNewsTransition.ANNOUNCED
        }
        val applications = lineage.filter { (_, reference) ->
            reference.transition == CorporateActionNewsTransition.APPLIED
        }
        val cancellations = lineage.filter { (_, reference) ->
            reference.transition == CorporateActionNewsTransition.CANCELLED
        }
        if (announcements.size != 1) {
            return "기업행동 $occurrenceId 계보에는 선행 공시 전이가 정확히 하나 필요합니다."
        }
        val (announcementEvent, announcement) = announcements.single()
        val stock = stocksById[announcement.stockId]
            ?: return "기업행동 $occurrenceId 공시에 알 수 없는 종목이 있습니다."
        if (lineage.any { (event, reference) ->
                reference.stockId != stock.id ||
                    event.affectedMarkets != setOf(stock.market) ||
                    event.affectedSectors != setOf(stock.sector) ||
                    event.affectedStockIds != setOf(stock.id) ||
                    event.sourceLabel != reference.source.displayName
            }
        ) {
            return "기업행동 $occurrenceId 뉴스의 종목·시장·산업·출처가 원장과 일치하지 않습니다."
        }
        if (announcementEvent.startsAt != announcement.announcedAt) {
            return "기업행동 $occurrenceId 선행 공시 시각이 원장 참조와 다릅니다."
        }

        val pending = pendingById[occurrenceId]
        val applied = appliedById[occurrenceId]
        when {
            pending != null -> {
                if (applications.isNotEmpty() || cancellations.isNotEmpty() || lineage.size != 1) {
                    return "대기 기업행동 $occurrenceId 계보에 적용 또는 취소 전이가 함께 있습니다."
                }
                announcement.pendingLineageViolation(pending)?.let { violation ->
                    return "기업행동 $occurrenceId 공시가 대기 원장과 일치하지 않습니다: $violation"
                }
            }
            applied != null -> {
                if (applications.size != 1 || cancellations.isNotEmpty() || lineage.size != 2) {
                    return "적용 기업행동 $occurrenceId 계보에는 공시와 완료 전이가 정확히 하나씩 필요합니다."
                }
                announcement.announcementLineageViolation(applied)?.let { violation ->
                    return "기업행동 $occurrenceId 공시가 적용 원장과 일치하지 않습니다: $violation"
                }
                val (applicationEvent, application) = applications.single()
                application.appliedLineageViolation(applied)?.let { violation ->
                    return "기업행동 $occurrenceId 완료 전이가 적용 원장과 일치하지 않습니다: $violation"
                }
                if (applicationEvent.startsAt != applied.effectiveAt) {
                    return "기업행동 $occurrenceId 완료 뉴스가 실제 적용과 동시에 생성되지 않았습니다."
                }
            }
            else -> {
                if (applications.isNotEmpty() || cancellations.size != 1 || lineage.size != 2) {
                    return "원장에서 종료된 기업행동 $occurrenceId 계보에는 상장 원장에 연결된 취소 전이가 필요합니다."
                }
                val (cancellationEvent, cancellation) = cancellations.single()
                val listingEvent = state.listingLifecycleLedger.singleOrNull { ledgerEvent ->
                    ledgerEvent.id == cancellation.cancellingListingEventId &&
                        ledgerEvent.sequence == cancellation.cancellingListingLedgerSequence
                } ?: return "기업행동 $occurrenceId 취소 전이가 가리키는 상장 원장 이벤트가 없습니다."
                cancellation.cancellationLineageViolation(announcement, listingEvent)?.let { violation ->
                    return "기업행동 $occurrenceId 취소 계보가 유효하지 않습니다: $violation"
                }
                if (cancellationEvent.startsAt != cancellation.cancelledAt) {
                    return "기업행동 $occurrenceId 취소 뉴스 시각이 취소 전이와 다릅니다."
                }
            }
        }
    }
    return null
}

private fun validateGameEvent(
    event: GameEvent,
    stocksById: Map<String, StockDefinition>,
    listingLifecycleLedger: List<ListingLifecycleLedgerEvent>,
): String? {
    val stockIds = stocksById.keys
    if (event.id.isBlank() || event.title.isBlank() || event.description.isBlank()) {
        return "ID·제목·본문은 비어 있을 수 없습니다."
    }
    if (event.generatorTemplateId?.isBlank() == true) {
        return "${event.id}의 생성 템플릿 ID는 비어 있을 수 없습니다."
    }
    if (event.generatorTemplateId?.let { it !in CURRENT_EVENT_TEMPLATE_IDS } == true) {
        return "${event.id}가 현재 카탈로그에 없는 생성 템플릿을 참조합니다."
    }
    val scope = event.scope as EventScope?
        ?: return "${event.id}의 범위 enum이 유효하지 않습니다."
    val type = event.type as EventType?
        ?: return "${event.id}의 뉴스 유형 enum이 유효하지 않습니다."
    val severity = event.severity as EventSeverity?
        ?: return "${event.id}의 뉴스 중요도 enum이 유효하지 않습니다."
    val recordKind = event.recordKind as EventRecordKind?
        ?: return "${event.id}의 뉴스 기록 유형 enum이 유효하지 않습니다."
    val coveragePolicy = event.impactCoveragePolicy as EventImpactCoveragePolicy?
        ?: return "${event.id}의 영향 커버리지 enum이 유효하지 않습니다."
    if (type !in EventType.entries || severity !in EventSeverity.entries ||
        recordKind !in EventRecordKind.entries || coveragePolicy !in EventImpactCoveragePolicy.entries
    ) {
        return "${event.id}의 뉴스 유형·중요도·기록 유형·영향 커버리지가 유효하지 않습니다."
    }
    if ((recordKind == EventRecordKind.MARKET_ACTION) != (event.marketAction != null)) {
        return "${event.id}의 뉴스 기록 유형과 시장조치 참조가 일치하지 않습니다."
    }
    if ((recordKind == EventRecordKind.SCHEDULED_RELEASE) != (event.scheduledEventReference != null)) {
        return "${event.id}의 정기 발표 기록 유형과 일정 발생 참조가 일치하지 않습니다."
    }
    if ((recordKind == EventRecordKind.CORPORATE_ACTION) != (event.corporateActionReference != null)) {
        return "${event.id}의 기업행동 기록 유형과 원장 참조가 일치하지 않습니다."
    }
    if (event.generatorTemplateId != null && recordKind == EventRecordKind.MARKET_ACTION) {
        return "${event.id}의 런타임 시장조치에 확률 이벤트 템플릿이 연결되었습니다."
    }
    event.corporateActionReference?.let { reference ->
        val transition = reference.transition as CorporateActionNewsTransition?
            ?: return "${event.id}의 기업행동 전이 enum이 유효하지 않습니다."
        val kind = reference.kind as CorporateActionKind?
            ?: return "${event.id}의 기업행동 종류 enum이 유효하지 않습니다."
        val source = reference.source as CorporateActionSource?
            ?: return "${event.id}의 기업행동 출처 enum이 유효하지 않습니다."
        if (transition !in CorporateActionNewsTransition.entries || kind !in CorporateActionKind.entries ||
            source !in CorporateActionSource.entries
        ) {
            return "${event.id}의 기업행동 전이·종류·출처 enum이 유효하지 않습니다."
        }
        reference.semanticInvariantViolation()?.let { violation ->
            return "${event.id}의 기업행동 원장 참조가 유효하지 않습니다: $violation"
        }
        val expectedStart = when (transition) {
            CorporateActionNewsTransition.ANNOUNCED -> reference.announcedAt
            CorporateActionNewsTransition.APPLIED -> reference.appliedAt
            CorporateActionNewsTransition.CANCELLED -> reference.cancelledAt
        }
        if (type != EventType.CORPORATE_ACTION || scope != EventScope.STOCK ||
            event.affectedStockIds != setOf(reference.stockId) || event.startsAt != expectedStart ||
            event.sourceLabel != source.displayName || event.impact != GameEventImpact(ImpactDirection.NEUTRAL)
        ) {
            return "${event.id}의 기업행동 참조와 뉴스 대상·시각·출처·가치중립 영향이 일치하지 않습니다."
        }
    }
    event.instrumentTermination?.let { terms ->
        val terminationKind = terms.kind as InstrumentTerminationKind?
            ?: return "${event.id}의 상품 종료 종류 enum이 유효하지 않습니다."
        val valuationMethod = terms.valuationMethod as InstrumentTerminationValuationMethod?
            ?: return "${event.id}의 상품 종료 평가 방식 enum이 유효하지 않습니다."
        if (terminationKind !in InstrumentTerminationKind.entries ||
            valuationMethod !in InstrumentTerminationValuationMethod.entries
        ) {
            return "${event.id}의 상품 종료 종류·평가 방식이 유효하지 않습니다."
        }
        terms.semanticInvariantViolation()?.let { violation ->
            return "${event.id}의 상품 종료 조건이 유효하지 않습니다: $violation"
        }
        val targetStock = event.affectedStockIds.singleOrNull()?.let(stocksById::get)
        if (recordKind != EventRecordKind.INSTRUMENT_LIFECYCLE ||
            scope != EventScope.STOCK ||
            targetStock == null ||
            !terms.isEligibleFor(targetStock) ||
            terms.effectiveNotBefore?.let { it < event.startsAt } == true ||
            event.listingFinalDispositionHint != null ||
            terms.listingRiskTag in event.listingRiskTags
        ) {
            return "${event.id}의 상품 종료 조건과 뉴스 대상·기록 유형이 일치하지 않습니다."
        }
    }
    event.tradingHaltDirective?.let { directive ->
        val haltKind = directive.kind as EventTradingHaltKind?
            ?: return "${event.id}의 이벤트 거래정지 종류 enum이 유효하지 않습니다."
        val haltReason = directive.reason as TradingHaltReason?
            ?: return "${event.id}의 이벤트 거래정지 사유 enum이 유효하지 않습니다."
        val eligibleMarkets = directive.eligibleMarkets as Set<Market>?
            ?: return "${event.id}의 이벤트 거래정지 대상 시장이 필요합니다."
        val detail = directive.detail as String?
            ?: return "${event.id}의 이벤트 거래정지 안내가 필요합니다."
        if (haltKind !in EventTradingHaltKind.entries || haltReason !in TradingHaltReason.entries ||
            eligibleMarkets.any { (it as Market?) == null } || detail.isBlank()
        ) {
            return "${event.id}의 이벤트 거래정지 지시자 값이 유효하지 않습니다."
        }
        directive.semanticInvariantViolation()?.let { violation ->
            return "${event.id}의 이벤트 거래정지 지시자가 유효하지 않습니다: $violation"
        }
        if (scope != EventScope.STOCK || event.affectedStockIds.isEmpty() ||
            event.affectedStockIds.any { stockId ->
                stocksById[stockId]?.market !in directive.eligibleMarkets
            }
        ) {
            return "${event.id}의 이벤트 거래정지 지시자와 뉴스 대상이 일치하지 않습니다."
        }
    }
    val direction = event.impact.direction as ImpactDirection?
        ?: return "${event.id}의 영향 방향 enum이 유효하지 않습니다."
    if (direction !in ImpactDirection.entries) return "${event.id}의 영향 방향이 유효하지 않습니다."
    if (event.durationHours <= 0 || event.effectDurationHours <= 0) {
        return "${event.id}의 뉴스·실제 반영 기간은 양수여야 합니다."
    }
    if (event.sourceLabel.isBlank()) return "${event.id}의 출처 표시는 비어 있을 수 없습니다."
    if (coveragePolicy == EventImpactCoveragePolicy.EXPLICIT_PATHS_ONLY &&
        event.impactInsights.isEmpty() && event.causalSignals.isEmpty()
    ) {
        return "${event.id}의 명시 경로 전용 정책에는 영향 분석 또는 인과 신호가 필요합니다."
    }
    if (event.effectStartsAt < event.startsAt) {
        return "${event.id}의 실제 반영 시작이 발표보다 빠릅니다."
    }
    val effectWindowValid = runCatching { event.effectEndsAt <= event.endsAt }.getOrDefault(false)
    if (!effectWindowValid) return "${event.id}의 실제 반영 구간이 뉴스 구간을 벗어납니다."
    if (event.impact.shockReturn <= -1.0 || !event.impact.shockReturn.isFinite() ||
        !event.impact.hourlyDrift.isFinite() ||
        !event.impact.volatilityMultiplier.isFinite() || event.impact.volatilityMultiplier < 0.0 ||
        !event.impact.volumeMultiplier.isFinite() || event.impact.volumeMultiplier < 0.0 ||
        !event.impact.liquidityMultiplier.isFinite() || event.impact.liquidityMultiplier < 0.0 ||
        !event.impact.sentiment.isFinite() || event.impact.sentiment !in -1.0..1.0
    ) {
        return "${event.id}의 가격 영향 계수가 유효하지 않습니다."
    }
    if (event.affectedStockIds.any { it.isBlank() || it !in stockIds }) {
        return "${event.id}에 알 수 없거나 빈 대상 종목 ID가 있습니다."
    }
    if (scope in setOf(EventScope.COUNTRY, EventScope.MARKET) && event.affectedMarkets.isEmpty() ||
        scope == EventScope.COUNTRY && event.affectedMarkets.map(Market::countryName).distinct().size != 1 ||
        scope == EventScope.SECTOR && event.affectedSectors.isEmpty() ||
        scope == EventScope.STOCK && event.affectedStockIds.isEmpty()
    ) {
        return "${event.id}의 범위와 대상이 일치하지 않습니다."
    }
    event.impactInsights.forEachIndexed { index, insight ->
        val targetKind = insight.targetKind as EventImpactTargetKind?
            ?: return "${event.id} 분석 $index 대상 enum이 유효하지 않습니다."
        val insightDirection = insight.direction as ImpactDirection?
            ?: return "${event.id} 분석 $index 방향 enum이 유효하지 않습니다."
        val horizon = insight.horizon as EventImpactHorizon?
            ?: return "${event.id} 분석 $index 시간축 enum이 유효하지 않습니다."
        if (insightDirection !in ImpactDirection.entries || horizon !in EventImpactHorizon.entries) {
            return "${event.id} 분석 $index enum이 유효하지 않습니다."
        }
        if (insight.targetLabel.isBlank() || insight.rationale.isBlank() ||
            !insight.relativeSensitivity.isFinite() || insight.relativeSensitivity !in 0.0..3.0 ||
            insight.relativeSensitivity == 0.0
        ) {
            return "${event.id} 분석 ${index}의 이름·근거·민감도가 유효하지 않습니다."
        }
        if (insight.stockId?.let { it.isBlank() || it !in stockIds } == true) {
            return "${event.id} 분석 ${index}에 알 수 없는 종목 ID가 있습니다."
        }
        val targetIsValid = when (targetKind) {
            EventImpactTargetKind.MARKET -> insight.markets.isNotEmpty() && insight.sector == null &&
                insight.industrySegment == null && insight.stockId == null
            EventImpactTargetKind.INDUSTRY -> insight.sector != null && insight.industrySegment == null &&
                insight.stockId == null
            EventImpactTargetKind.INDUSTRY_SEGMENT -> insight.sector != null &&
                insight.industrySegment?.parentSector == insight.sector && insight.stockId == null
            EventImpactTargetKind.STOCK -> insight.stockId != null && insight.industrySegment == null
        }
        if (!targetIsValid) return "${event.id} 분석 ${index}의 대상 조합이 유효하지 않습니다."
    }
    if (event.causalSignals.map { it.factor }.distinct().size != event.causalSignals.size) {
        return "${event.id}에 같은 경제 요인의 인과 신호가 중복되었습니다."
    }
    event.causalSignals.forEachIndexed { index, signal ->
        val factor = signal.factor as CausalEconomicFactor?
            ?: return "${event.id} 인과 신호 $index 경제 요인 enum이 유효하지 않습니다."
        val signalDirection = signal.direction as CausalSignalDirection?
            ?: return "${event.id} 인과 신호 $index 방향 enum이 유효하지 않습니다."
        if (factor !in CausalEconomicFactor.entries || signalDirection !in CausalSignalDirection.entries) {
            return "${event.id} 인과 신호 $index enum이 유효하지 않습니다."
        }
        if (!signal.strength.isFinite() || signal.strength <= 0.0 || signal.strength > 1.0 ||
            !signal.confidence.isFinite() || signal.confidence <= 0.0 || signal.confidence > 1.0
        ) {
            return "${event.id} 인과 신호 $index 강도·신뢰도가 유효하지 않습니다."
        }
    }
    if (event.reportedFacts.any { fact ->
            fact.label.isBlank() || fact.actual.isBlank() || fact.comparison?.isBlank() == true
        }
    ) {
        return "${event.id}의 발표 수치 이름·실제값·비교값이 유효하지 않습니다."
    }
    event.marketAction?.let { action ->
        val kind = action.kind as MarketActionKind?
            ?: return "${event.id}의 시장조치 종류 enum이 유효하지 않습니다."
        val transition = action.transition as MarketActionTransition?
            ?: return "${event.id}의 시장조치 전이 enum이 유효하지 않습니다."
        if (kind !in MarketActionKind.entries || transition !in MarketActionTransition.entries) {
            return "${event.id}의 시장조치 enum이 유효하지 않습니다."
        }
        action.semanticInvariantViolation()?.let { violation ->
            return "${event.id}의 시장조치가 유효하지 않습니다: $violation"
        }
        if (action.announcedAt != event.startsAt ||
            action.stockId?.let { it !in event.affectedStockIds } == true ||
            !event.affectedMarkets.containsAll(action.markets)
        ) {
            return "${event.id}의 시장조치 참조가 뉴스의 발표 시각·직접 대상과 일치하지 않습니다."
        }
        val actionStock = action.stockId?.let(stocksById::get)
        if (actionStock != null && actionStock.market !in action.markets) {
            return "${event.id}의 시장조치 시장이 실제 종목 상장시장과 일치하지 않습니다."
        }
        if (action.stockId?.let { it.isBlank() || it !in stockIds } == true) {
            return "${event.id}의 시장조치 대상 종목이 유효하지 않습니다."
        }
        if (kind == MarketActionKind.LISTING_LIFECYCLE && listingLifecycleLedger.none { ledgerEvent ->
                ledgerEvent.id == action.occurrenceId &&
                    ledgerEvent.stockId == action.stockId &&
                    ledgerEvent.sequence == action.listingLedgerSequence &&
                    ledgerEvent.toStatus == action.listingStatus
            }
        ) {
            return "${event.id}의 상장 원장 참조와 실제 원장 전이가 일치하지 않습니다."
        }
    }
    return null
}
