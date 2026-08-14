package com.amond.kmpbook.presentation.news

import com.amond.kmpbook.domain.model.causal.CausalStockImpact
import com.amond.kmpbook.domain.model.corporateaction.CorporateActionNewsReference
import com.amond.kmpbook.domain.model.corporateaction.CorporateActionNewsTransition
import com.amond.kmpbook.domain.model.corporateaction.CorporateActionRecord
import com.amond.kmpbook.domain.model.corporateaction.PendingCorporateAction
import com.amond.kmpbook.domain.model.event.EventImpactResolutionSource
import com.amond.kmpbook.domain.model.event.EventImpactTargetKind
import com.amond.kmpbook.domain.model.event.EventRecordKind
import com.amond.kmpbook.domain.model.event.EventScope
import com.amond.kmpbook.domain.model.event.GameEvent
import com.amond.kmpbook.domain.model.event.ImpactDirection
import com.amond.kmpbook.domain.model.event.impactCoverageFor
import com.amond.kmpbook.domain.model.instrument.EtfAssetClass
import com.amond.kmpbook.domain.model.instrument.InstrumentStrategy
import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.listing.alert.InvestmentAlertLevel
import com.amond.kmpbook.domain.model.listing.alert.InvestmentAlertStatus
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingLifecycleEventKind
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingLifecycleLedgerEvent
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingLifecycleState
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingLifecycleStatus
import com.amond.kmpbook.domain.model.listing.lifecycle.blocksOrderlyProductTermination
import com.amond.kmpbook.domain.model.listing.termination.InstrumentTerminationKind
import com.amond.kmpbook.domain.model.listing.termination.InstrumentTerminationValuationMethod
import com.amond.kmpbook.domain.model.listing.termination.resolvePublishedInstrumentTerminationNotice
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.market.ReferenceCurrency
import com.amond.kmpbook.domain.model.market.Sector
import com.amond.kmpbook.domain.model.marketaction.MarketActionKind
import com.amond.kmpbook.domain.model.marketaction.MarketActionReference
import com.amond.kmpbook.domain.model.marketaction.MarketActionTransition
import com.amond.kmpbook.domain.model.news.NewsRelevance
import com.amond.kmpbook.domain.model.news.directionFor
import com.amond.kmpbook.domain.model.news.relevanceTo
import com.amond.kmpbook.domain.model.news.resolvedImpactFor
import com.amond.kmpbook.domain.model.protection.core.TradingHaltStatus
import com.amond.kmpbook.domain.model.protection.core.TradingProtectionSnapshot
import com.amond.kmpbook.domain.model.protection.krx.KrxCircuitBreakerPhase
import com.amond.kmpbook.domain.model.protection.krx.KrxSidecarPhase
import com.amond.kmpbook.domain.model.protection.krx.KrxViPhase
import com.amond.kmpbook.domain.model.protection.us.UsLuldPhase
import com.amond.kmpbook.domain.model.protection.us.UsMwcbPhase
import com.amond.kmpbook.domain.simulation.protection.TradingProtectionEngine
import com.amond.kmpbook.domain.time.GameCalendar
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

data class NewsUiProjection(
    val stories: List<NewsStoryUi>,
    val stockGroups: List<NewsStockGroupUi>,
    val sectorGroups: List<NewsSectorGroupUi>,
    val marketEffectActiveCount: Int,
    val operationalActiveCount: Int,
    val personalCount: Int,
    val homeStories: List<NewsStoryUi>,
    val storiesByStockId: Map<String, List<NewsStoryUi>>,
) {
    /** 직접 대상·구체성·활성 상태·발생 시각·ID 순으로 안정 정렬된 종목별 기사다. */
    fun storiesForStock(stockId: String): List<NewsStoryUi> = storiesByStockId[stockId].orEmpty()
}

/**
 * 시뮬레이션 상태를 뉴스 화면에 필요한 읽기 모델로 투영한다.
 *
 * 가격 효과 상태는 임의의 [GameEvent.durationHours]가 아니라 [activeEventIds]를 우선한다.
 * 거래소 조치는 현재 보호장치·상장 상태를 사용해 과거 발동 뉴스가 계속 `영향 중`으로
 * 남지 않게 한다.
 *
 * 전체 기사는 활동 우선순위, 개인 관련성, 최신 시각, ID 순으로 결정론적으로 정렬한다.
 * 홈은 개인 관련성 뒤에 미열람을 추가하고, 종목별 목록은 직접 대상, 구체성, 활성 여부,
 * 활동 우선순위, 최신 시각, ID 순으로 다시 정렬한다.
 */
fun buildNewsUiProjection(
    currentTime: Instant,
    events: List<GameEvent>,
    activeEventIds: Set<String>,
    readEventIds: Set<String>,
    stocks: List<StockDefinition>,
    holdingIds: Set<String>,
    watchlistIds: Set<String>,
    listingStates: Map<String, ListingLifecycleState>,
    listingLifecycleLedger: List<ListingLifecycleLedgerEvent>,
    pendingCorporateActions: List<PendingCorporateAction>,
    corporateActionLedger: List<CorporateActionRecord>,
    tradingProtectionSnapshot: TradingProtectionSnapshot,
): NewsUiProjection {
    val stockById = stocks.associateBy(StockDefinition::id)
    val stories = events
        .distinctBy(GameEvent::id)
        .map { event ->
            val relevance = event.relevanceTo(stocks, holdingIds, watchlistIds)
            val impactPaths = event.impactPaths(stockById, holdingIds, watchlistIds)
            val relatedStocks = event.relatedStocks(
                stockById = stockById,
                holdingIds = holdingIds,
                watchlistIds = watchlistIds,
            )
            val isMarketAction = event.recordKind == EventRecordKind.MARKET_ACTION
            val isScheduled = event.recordKind == EventRecordKind.SCHEDULED_RELEASE
            val instrumentTermination = event.instrumentTerminationUi(
                currentTime = currentTime,
                allEvents = events,
                stockById = stockById,
                listingStates = listingStates,
                listingLifecycleLedger = listingLifecycleLedger,
            )
            val operationalStatus = instrumentTermination?.status ?: event.operationalStatus(
                currentTime = currentTime,
                listingStates = listingStates,
                listingLifecycleLedger = listingLifecycleLedger,
                pendingCorporateActions = pendingCorporateActions,
                corporateActionLedger = corporateActionLedger,
                tradingProtectionSnapshot = tradingProtectionSnapshot,
                allEvents = events,
            )
            val marketEffectStatus = event.marketEffectStatus(
                currentTime = currentTime,
                activeEventIds = activeEventIds,
                isScheduled = isScheduled,
            )
            val status = when {
                marketEffectStatus != null && marketEffectStatus.state.activityPriority >
                    (operationalStatus?.state?.activityPriority ?: -1) -> marketEffectStatus
                operationalStatus != null -> operationalStatus
                marketEffectStatus != null -> marketEffectStatus
                else -> informationStatus()
            }
            NewsStoryUi(
                event = event,
                status = status,
                marketEffectStatus = marketEffectStatus,
                operationalStatus = operationalStatus,
                relevance = relevance,
                personalDirection = event.summaryDirection(relevance, relatedStocks, impactPaths),
                impactPaths = impactPaths,
                relatedStocks = relatedStocks,
                instrumentTermination = instrumentTermination,
                isUnread = event.id !in readEventIds,
                isMarketAction = isMarketAction,
                isScheduled = isScheduled,
                isOperational = event.recordKind in OPERATIONAL_RECORD_KINDS,
            )
        }
        .sortedWith(
            compareByDescending<NewsStoryUi>(NewsStoryUi::activityPriority)
                .thenByDescending { it.relevance.isPersonal }
                .thenByDescending { it.event.startsAt }
                .thenBy { it.event.id },
        )

    val directlyRelatedStockIds = stories
        .flatMap(NewsStoryUi::relatedStocks)
        .mapTo(linkedSetOf(), NewsRelatedStockUi::stockId)
    val relatedByStockId = stories
        .flatMap(NewsStoryUi::relatedStocks)
        .groupBy(NewsRelatedStockUi::stockId)
    val stockGroups = (holdingIds + watchlistIds + directlyRelatedStockIds)
        .mapNotNull(stockById::get)
        .mapNotNull { stock ->
            val related = relatedByStockId[stock.id].orEmpty()
            val count = related.size
            if (count == 0) return@mapNotNull null
            val held = stock.id in holdingIds
            val watched = stock.id in watchlistIds
            val strongestRelation = related.maxWithOrNull(
                compareBy<NewsRelatedStockUi> { it.directTarget }.thenBy { it.specificity },
            ) ?: return@mapNotNull null
            NewsStockGroupUi(
                key = "stock:${stock.id}",
                stockId = stock.id,
                label = stock.name,
                detail = buildString {
                    append(
                        when {
                            held && watched -> "보유 · 관심"
                            held -> "보유"
                            watched -> "관심"
                            else -> strongestRelation.relationKind.displayName
                        },
                    )
                    append(" · ${stock.market.displayName} ${stock.symbol}")
                },
                count = count,
                held = held,
                watched = watched,
                directTarget = related.any(NewsRelatedStockUi::directTarget),
                specificity = related.maxOf(NewsRelatedStockUi::specificity),
                relationKind = strongestRelation.relationKind,
            )
        }
        .sortedWith(
            compareByDescending<NewsStockGroupUi> { it.held }
                .thenByDescending { it.watched }
                .thenByDescending { it.directTarget }
                .thenByDescending { it.specificity }
                .thenBy { it.label }
                .thenBy { it.stockId },
        )

    val mentionedIndustries = stories.flatMapTo(linkedSetOf()) { story ->
        story.impactPaths.flatMap { path ->
            val sector = path.sector ?: return@flatMap emptyList()
            buildList {
                add(NewsIndustryKey(sector, null))
                path.industrySegment?.let { segment -> add(NewsIndustryKey(sector, segment)) }
            }
        }
    }
    val sectorGroups = mentionedIndustries.mapNotNull { industry ->
        val matchingStories = stories.filter { it.targetsIndustry(industry) }
        if (matchingStories.isEmpty()) return@mapNotNull null
        NewsSectorGroupUi(
            key = industry.key,
            sector = industry.sector,
            industrySegment = industry.segment,
            label = industry.segment?.displayName ?: industry.sector.displayName,
            count = matchingStories.size,
            personalCount = matchingStories.count { story -> story.hasPersonalIndustryPath(industry) },
        )
    }.sortedWith(
        compareByDescending<NewsSectorGroupUi> { it.personalCount }
            .thenByDescending { it.count }
            .thenBy { it.label },
    )

    val homeStories = stories.sortedWith(
        compareByDescending<NewsStoryUi>(NewsStoryUi::activityPriority)
            .thenByDescending { it.relevance.isPersonal }
            .thenByDescending { it.isUnread }
            .thenByDescending { it.event.startsAt }
            .thenBy { it.event.id },
    ).take(HOME_STORY_LIMIT)

    val storiesByStockId = stockById.keys.sorted().mapNotNull { stockId ->
        val relatedStories = stories.filter { story ->
            story.relatedStocks.any { related -> related.stockId == stockId }
        }.sortedWith(
            compareByDescending<NewsStoryUi> { story ->
                story.relatedStocks.first { it.stockId == stockId }.directTarget
            }.thenByDescending { story ->
                story.relatedStocks.first { it.stockId == stockId }.specificity
            }.thenByDescending { story -> story.activityPriority > 0 }
                .thenByDescending(NewsStoryUi::activityPriority)
                .thenByDescending { it.event.startsAt }
                .thenBy { it.event.id },
        )
        relatedStories.takeIf(List<NewsStoryUi>::isNotEmpty)?.let { stockId to it }
    }.toMap(linkedMapOf())

    return NewsUiProjection(
        stories = stories,
        stockGroups = stockGroups,
        sectorGroups = sectorGroups,
        marketEffectActiveCount = stories.count {
            it.marketEffectStatus?.state == NewsEffectState.MARKET_ACTIVE
        },
        operationalActiveCount = stories.count {
            it.operationalStatus?.state in setOf(
                NewsEffectState.RESTRICTION_ACTIVE,
                NewsEffectState.PROCESS_ACTIVE,
            )
        },
        personalCount = stories.count { it.relevance.isPersonal },
        homeStories = homeStories,
        storiesByStockId = storiesByStockId,
    )
}

private fun GameEvent.summaryDirection(
    relevance: NewsRelevance,
    relatedStocks: List<NewsRelatedStockUi>,
    displayedPaths: List<NewsImpactPathUi>,
): ImpactDirection {
    val personalDirections = relatedStocks
        .filter { it.held || it.watched }
        .map(NewsRelatedStockUi::direction)
    if (relevance.isPersonal && personalDirections.isNotEmpty()) {
        return personalDirections.combinedDirection()
    }
    if ((impactInsights.isNotEmpty() || causalSignals.isNotEmpty()) && displayedPaths.isNotEmpty()) {
        return displayedPaths.map(NewsImpactPathUi::direction).combinedDirection()
    }
    return impact.direction
}

private fun GameEvent.instrumentTerminationUi(
    currentTime: Instant,
    allEvents: List<GameEvent>,
    stockById: Map<String, StockDefinition>,
    listingStates: Map<String, ListingLifecycleState>,
    listingLifecycleLedger: List<ListingLifecycleLedgerEvent>,
): NewsInstrumentTerminationUi? {
    val terms = instrumentTermination ?: return null
    val stock = affectedStockIds.asSequence().mapNotNull(stockById::get).firstOrNull()
    val state = stock?.let { listingStates[it.id] }
    val relevantLedger = stock?.let { target ->
        listingLifecycleLedger.filter { ledgerEvent ->
            ledgerEvent.stockId == target.id &&
                ledgerEvent.controllingTerminationOccurrenceId == id
        }
    }.orEmpty()
    val latestLedger = relevantLedger.maxByOrNull(ListingLifecycleLedgerEvent::sequence)
    val canonicalStateMatches = state?.controllingTerminationOccurrenceId == id
    val frozenSettlementOccurrenceId = state
        ?.takeIf { lifecycle ->
            lifecycle.status in setOf(
                ListingLifecycleStatus.LIQUIDATION_PENDING,
                ListingLifecycleStatus.TERMINATED,
            )
        }
        ?.controllingTerminationOccurrenceId
    val expectedWinner = stock?.takeIf { frozenSettlementOccurrenceId == null }?.let { target ->
        resolvePublishedInstrumentTerminationNotice(
            stock = target,
            events = allEvents,
            publishedAt = currentTime,
            incumbentOccurrenceId = state?.controllingTerminationOccurrenceId,
        )
    }
    val blockedByForcedProcedure = state?.let { lifecycle ->
        lifecycle.controllingTerminationOccurrenceId == null &&
            lifecycle.status in setOf(
                ListingLifecycleStatus.UNDER_REVIEW,
                ListingLifecycleStatus.TRADING_SUSPENDED,
            ) && lifecycle.activeReason?.blocksOrderlyProductTermination() == true
    } == true
    val nonProductLifecyclePreempts = state?.let { lifecycle ->
        if (lifecycle.controllingTerminationOccurrenceId != null) {
            false
        } else {
            when (lifecycle.status) {
                ListingLifecycleStatus.DELISTED,
                ListingLifecycleStatus.TERMINATED,
                -> true
                ListingLifecycleStatus.DELISTING_SCHEDULED -> expectedWinner == null ||
                    expectedWinner.rawEffectiveOn >= requireNotNull(lifecycle.scheduledDelistingOn)
                else -> false
            }
        }
    } == true
    val superseded = nonProductLifecyclePreempts || when {
        frozenSettlementOccurrenceId != null -> frozenSettlementOccurrenceId != id
        else -> expectedWinner?.event?.id?.let { winnerId -> winnerId != id } == true
    }
    val currentMarketDate = stock?.let { GameCalendar.marketLocalDateTime(it.market, currentTime).date }
    val termConditionReached = when {
        terms.contractualDate != null && currentMarketDate != null -> currentMarketDate >= terms.contractualDate
        terms.effectiveNotBefore != null -> currentTime >= terms.effectiveNotBefore
        else -> false
    }
    val completed = canonicalStateMatches && (
        state.status == ListingLifecycleStatus.TERMINATED ||
            latestLedger?.kind == ListingLifecycleEventKind.TERMINATED
        )
    val settlementInProgress = !completed && canonicalStateMatches && (
        state.status == ListingLifecycleStatus.LIQUIDATION_PENDING ||
            latestLedger?.kind == ListingLifecycleEventKind.LIQUIDATION_STARTED
        )
    val awaitingValuation = !completed && !settlementInProgress && (
        termConditionReached ||
            canonicalStateMatches && state.status == ListingLifecycleStatus.DELISTING_SCHEDULED &&
            currentMarketDate != null && state.scheduledDelistingOn?.let { currentMarketDate >= it } == true
        )
    val stage = when {
        blockedByForcedProcedure -> NewsInstrumentTerminationStageUi.BLOCKED_BY_FORCED_REVIEW
        superseded -> NewsInstrumentTerminationStageUi.SUPERSEDED
        completed -> NewsInstrumentTerminationStageUi.COMPLETED
        settlementInProgress -> NewsInstrumentTerminationStageUi.SETTLEMENT_IN_PROGRESS
        awaitingValuation -> NewsInstrumentTerminationStageUi.AWAITING_VALUATION
        else -> NewsInstrumentTerminationStageUi.SCHEDULED
    }
    val settlementDueOn = state
        ?.takeIf { canonicalStateMatches }
        ?.settlementDueOn
        ?: relevantLedger.asReversed().firstNotNullOfOrNull { it.disposition?.settlementDueOn }
    val status = when (stage) {
        NewsInstrumentTerminationStageUi.SCHEDULED -> NewsEffectStatusUi(
            state = NewsEffectState.UPCOMING,
            label = "상품 종료 예정",
            summary = "공시된 계약 조건의 효력 시점까지 상품 평가와 정산을 기다리고 있어요.",
        )
        NewsInstrumentTerminationStageUi.BLOCKED_BY_FORCED_REVIEW -> NewsEffectStatusUi(
            state = NewsEffectState.PROCESS_ACTIVE,
            label = "강제 절차로 보류",
            summary = "거래소의 강제 심사·정지 절차가 먼저 진행 중이에요. 해당 절차가 해제되면 공시된 상품 종료 조건을 다시 판단해요.",
        )
        NewsInstrumentTerminationStageUi.AWAITING_VALUATION -> NewsEffectStatusUi(
            state = NewsEffectState.WAITING_FOR_MARKET,
            label = "최종 평가 대기",
            summary = "종료 효력 조건이 충족돼 주 상장시장의 최종 평가값 확정을 기다리고 있어요.",
        )
        NewsInstrumentTerminationStageUi.SETTLEMENT_IN_PROGRESS -> NewsEffectStatusUi(
            state = NewsEffectState.PROCESS_ACTIVE,
            label = "정산 진행 중",
            summary = settlementDueOn?.let { dueOn ->
                "최종 평가값이 확정됐고 ${dueOn.displayDate()} 청산금 지급일까지 정산이 진행돼요."
            } ?: "최종 평가값이 확정됐고 청산금 지급 절차가 진행되고 있어요.",
        )
        NewsInstrumentTerminationStageUi.COMPLETED -> NewsEffectStatusUi(
            state = NewsEffectState.RESOLVED,
            label = "정산 완료",
            summary = "청산금 지급과 상품 종료가 완료돼 상장 원장에 최종 기록으로 남아 있어요.",
        )
        NewsInstrumentTerminationStageUi.SUPERSEDED -> NewsEffectStatusUi(
            state = NewsEffectState.RESOLVED,
            label = if (nonProductLifecyclePreempts) "상장폐지 절차가 우선" else "다른 종료 공시가 우선",
            summary = if (nonProductLifecyclePreempts) {
                "이미 확정된 비상품 상장폐지 절차가 우선 적용돼 이 종료 공시는 과거 기록으로 남아 있어요."
            } else {
                "더 이른 효력일 또는 같은 날의 우선 계약 조건이 적용돼 이 공시는 과거 기록으로 남아 있어요."
            },
        )
    }
    val market = stock?.market
    val scheduleLabel: String
    val scheduleValue: String
    if (terms.contractualDate != null) {
        scheduleLabel = "계약상 만기일"
        scheduleValue = terms.contractualDate.displayDate()
    } else {
        scheduleLabel = "최소 효력 시각"
        scheduleValue = requireNotNull(terms.effectiveNotBefore).let { effectiveAt ->
            market?.let { effectiveAt.displayMarketDateTime(it) }
                ?: "$effectiveAt"
        }
    }
    return NewsInstrumentTerminationUi(
        stage = stage,
        kindLabel = terms.kind.displayName(),
        scheduleLabel = scheduleLabel,
        scheduleValue = scheduleValue,
        valuationLabel = terms.valuationMethod.displayName(),
        valuationDescription = terms.valuationMethod.description(terms.accelerationRecoveryRate),
        settlementValue = settlementDueOn?.displayDate(),
        status = status,
    )
}

private fun InstrumentTerminationKind.displayName(): String = when (this) {
    InstrumentTerminationKind.CONTRACTUAL_MATURITY -> "계약상 만기"
    InstrumentTerminationKind.CREDIT_DEFAULT -> "발행사 신용사건"
    InstrumentTerminationKind.ISSUER_ACCELERATION -> "발행사 가속상환"
    InstrumentTerminationKind.OPTIONAL_CALL -> "선택적 조기상환"
    InstrumentTerminationKind.FUND_LIQUIDATION -> "펀드 청산"
}

private fun InstrumentTerminationValuationMethod.displayName(): String = when (this) {
    InstrumentTerminationValuationMethod.ETN_CONTRACT_SETTLEMENT -> "ETN 계약 상환액 기준"
    InstrumentTerminationValuationMethod.ETN_CREDIT_DEFAULT_RECOVERY -> "ETN 신용사건 회수액 기준"
    InstrumentTerminationValuationMethod.FINAL_NET_ASSET_VALUE -> "최종 순자산가치 기준"
}

private fun InstrumentTerminationValuationMethod.description(recoveryRate: Double?): String = when (this) {
    InstrumentTerminationValuationMethod.ETN_CONTRACT_SETTLEMENT ->
        "상품 조건의 지표가치 관측창·상환 배수·미지급 쿠폰을 ETN 계약 원장에서 계산해요."
    InstrumentTerminationValuationMethod.ETN_CREDIT_DEFAULT_RECOVERY -> {
        val recovery = requireNotNull(recoveryRate)
        "최종 지표가치에 공시에 확정된 회수율 ${(recovery * 100).toInt()}%를 적용해요."
    }
    InstrumentTerminationValuationMethod.FINAL_NET_ASSET_VALUE ->
        "펀드 회계 상태에 저장된 종료 효력일 최종 순자산가치로 계산해요."
}

private fun LocalDate.displayDate(): String =
    "$year.${month.number.toString().padStart(2, '0')}.${day.toString().padStart(2, '0')}"

private fun Instant.displayMarketDateTime(market: Market): String {
    val local = GameCalendar.marketLocalDateTime(market, this)
    val date = local.date.displayDate()
    val hour = local.hour.toString().padStart(2, '0')
    val minute = local.minute.toString().padStart(2, '0')
    return "$date $hour:$minute (${market.displayName} 현지)"
}

private fun GameEvent.operationalStatus(
    currentTime: Instant,
    listingStates: Map<String, ListingLifecycleState>,
    listingLifecycleLedger: List<ListingLifecycleLedgerEvent>,
    pendingCorporateActions: List<PendingCorporateAction>,
    corporateActionLedger: List<CorporateActionRecord>,
    tradingProtectionSnapshot: TradingProtectionSnapshot,
    allEvents: List<GameEvent>,
): NewsEffectStatusUi? {
    if (currentTime < startsAt) {
        return NewsEffectStatusUi(
            state = NewsEffectState.UPCOMING,
            label = "예정",
            summary = "정해진 시각이 되면 이 절차가 시작돼요.",
        )
    }

    marketAction?.let { action ->
        return action.effectStatus(
            currentTime = currentTime,
            listingStates = listingStates,
            listingLifecycleLedger = listingLifecycleLedger,
            snapshot = tradingProtectionSnapshot,
            allEvents = allEvents,
        )
    }

    corporateActionReference?.let { reference ->
        return reference.effectStatus(
            currentTime = currentTime,
            market = affectedMarkets.singleOrNull(),
            pendingCorporateActions = pendingCorporateActions,
            corporateActionLedger = corporateActionLedger,
            listingLifecycleLedger = listingLifecycleLedger,
            allEvents = allEvents,
        )
    }

    return null
}

private fun GameEvent.marketEffectStatus(
    currentTime: Instant,
    activeEventIds: Set<String>,
    isScheduled: Boolean,
): NewsEffectStatusUi? {
    val hasMarketEffect = impact.shockReturn != 0.0 || impact.hourlyDrift != 0.0 ||
        impact.volatilityMultiplier != 1.0 || impact.volumeMultiplier != 1.0 ||
        impact.liquidityMultiplier != 1.0 || impact.sentiment != 0.0
    if (!hasMarketEffect) return null
    if (isScheduled) {
        return when {
            currentTime < effectStartsAt -> NewsEffectStatusUi(
                state = NewsEffectState.WAITING_FOR_MARKET,
                label = "장 반영 대기",
                summary = "발표는 나왔지만 관련 정규장이 열릴 때 가격 과정에 반영돼요.",
            )
            currentTime < effectEndsAt -> NewsEffectStatusUi(
                state = NewsEffectState.MARKET_ACTIVE,
                label = "시장 반영 중",
                summary = "현재 관련 시장의 시세 과정에 발표 결과가 반영되고 있어요.",
            )
            else -> NewsEffectStatusUi(
                state = NewsEffectState.MARKET_ENDED,
                label = "영향 종료",
                summary = "이 발표의 가격 반영 구간이 끝났고 뉴스 기록으로 남아 있어요.",
            )
        }
    }

    return if (id in activeEventIds) {
        NewsEffectStatusUi(
            state = NewsEffectState.MARKET_ACTIVE,
            label = "시장 반영 중",
            summary = "현재 시세 과정에 이 뉴스의 영향이 반영되고 있어요.",
        )
    } else {
        NewsEffectStatusUi(
            state = NewsEffectState.MARKET_ENDED,
            label = "영향 종료",
            summary = "실질적인 가격 반영 구간이 끝났고 뉴스 기록으로 남아 있어요.",
        )
    }
}

private fun informationStatus(): NewsEffectStatusUi = NewsEffectStatusUi(
    state = NewsEffectState.INFORMATION,
    label = "소식",
    summary = "예상 등락을 단정하지 않고 확인할 사실과 절차를 전하는 뉴스예요.",
)

private fun CorporateActionNewsReference.effectStatus(
    currentTime: Instant,
    market: Market?,
    pendingCorporateActions: List<PendingCorporateAction>,
    corporateActionLedger: List<CorporateActionRecord>,
    listingLifecycleLedger: List<ListingLifecycleLedgerEvent>,
    allEvents: List<GameEvent>,
): NewsEffectStatusUi {
    val appliedRecord = corporateActionLedger.firstOrNull { record ->
        when (transition) {
            CorporateActionNewsTransition.ANNOUNCED -> announcementLineageViolation(record) == null
            CorporateActionNewsTransition.APPLIED -> appliedLineageViolation(record) == null
            CorporateActionNewsTransition.CANCELLED -> false
        }
    }
    val cancellation = allEvents.asSequence()
        .mapNotNull(GameEvent::corporateActionReference)
        .filter { candidate ->
            candidate.transition == CorporateActionNewsTransition.CANCELLED &&
                candidate.occurrenceId == occurrenceId
        }
        .firstOrNull { candidate ->
            val listingEvent = listingLifecycleLedger.firstOrNull { event ->
                event.id == candidate.cancellingListingEventId &&
                    event.sequence == candidate.cancellingListingLedgerSequence &&
                    event.stockId == candidate.stockId
            }
            listingEvent != null && when (transition) {
                CorporateActionNewsTransition.ANNOUNCED ->
                    candidate.cancellationLineageViolation(this, listingEvent) == null
                CorporateActionNewsTransition.APPLIED,
                CorporateActionNewsTransition.CANCELLED,
                -> candidate == this
            }
        }

    return when (transition) {
        CorporateActionNewsTransition.APPLIED -> resolvedStatus(
            label = "${kind.displayName} 적용 완료",
            summary = if (appliedRecord != null) {
                "보유 수량과 주당 기준가 조정이 기업행동 원장에 반영됐어요."
            } else {
                "기업행동 적용 기록이 완료 상태로 남아 있어요."
            },
        )
        CorporateActionNewsTransition.CANCELLED -> resolvedStatus(
            label = "${kind.displayName} 취소",
            summary = "상장·청산 절차가 우선 적용돼 예정된 기업행동은 실행되지 않았어요.",
        )
        CorporateActionNewsTransition.ANNOUNCED -> when {
            appliedRecord != null -> resolvedStatus(
                label = "${kind.displayName} 적용 완료",
                summary = "공시된 수량과 주당 기준가 조정이 기업행동 원장에 반영됐어요.",
            )
            cancellation != null -> resolvedStatus(
                label = "${kind.displayName} 취소",
                summary = "상장·청산 절차가 우선 적용돼 이 기업행동 일정은 종료됐어요.",
            )
            pendingCorporateActions.any { action -> pendingLineageViolation(action) == null } -> {
                val schedule = market?.let { effectiveNotBefore.displayMarketDateTime(it) }
                    ?: effectiveNotBefore.toString()
                if (currentTime < effectiveNotBefore) {
                    NewsEffectStatusUi(
                        state = NewsEffectState.UPCOMING,
                        label = "${kind.displayName} 예정",
                        summary = "$schedule 이후 첫 정규장에서 보유 수량과 기준가가 함께 조정돼요.",
                    )
                } else {
                    NewsEffectStatusUi(
                        state = NewsEffectState.WAITING_FOR_MARKET,
                        label = "정규장 적용 대기",
                        summary = "최소 효력 시각은 지났고 다음 정규장 적용 시점을 기다리고 있어요.",
                    )
                }
            }
            else -> resolvedStatus(
                label = "일정 종료",
                summary = "이 기업행동 공시는 더 이상 대기 중인 일정이 아니에요.",
            )
        }
    }
}

private fun MarketActionReference.effectStatus(
    currentTime: Instant,
    listingStates: Map<String, ListingLifecycleState>,
    listingLifecycleLedger: List<ListingLifecycleLedgerEvent>,
    snapshot: TradingProtectionSnapshot,
    allEvents: List<GameEvent>,
): NewsEffectStatusUi = when (kind) {
    MarketActionKind.KRX_CIRCUIT_BREAKER -> krxCircuitBreakerStatus(currentTime, snapshot)
    MarketActionKind.KRX_SIDECAR -> krxSidecarStatus(currentTime, snapshot)
    MarketActionKind.KRX_VOLATILITY_INTERRUPTION -> krxViStatus(currentTime, snapshot)
    MarketActionKind.US_MARKET_WIDE_CIRCUIT_BREAKER -> usMarketCircuitBreakerStatus(currentTime, snapshot)
    MarketActionKind.US_LIMIT_UP_LIMIT_DOWN -> usLuldStatus(currentTime, snapshot, allEvents)
    MarketActionKind.INSTRUMENT_TRADING_HALT -> instrumentHaltStatus(currentTime, snapshot)
    MarketActionKind.INVESTMENT_ALERT -> investmentAlertStatus(currentTime, snapshot, allEvents)
    MarketActionKind.LISTING_LIFECYCLE -> listingLifecycleStatus(listingStates, listingLifecycleLedger)
    MarketActionKind.LISTING_REMEDIATION -> resolvedStatus(
        label = "심사 반영",
        summary = "개선 심사 결과가 상장 상태 원장에 반영됐어요.",
    )
}

private fun MarketActionReference.krxCircuitBreakerStatus(
    currentTime: Instant,
    snapshot: TradingProtectionSnapshot,
): NewsEffectStatusUi {
    if (transition == MarketActionTransition.REOPENED) return releasedStatus()
    if (endsAt?.let { currentTime >= it } == true) {
        return releasedStatus("이 서킷브레이커 조치의 거래 제한 구간이 끝났어요.")
    }
    val market = markets.singleOrNull() ?: return releasedStatus()
    val state = snapshot.krxCircuitBreakers[market] ?: return releasedStatus()
    val sameOccurrence = state.triggeredAt == effectiveAt && state.activeLevel?.ordinal?.plus(1) == stage
    if (!sameOccurrence) return releasedStatus()
    if (state.phase == KrxCircuitBreakerPhase.CLOSED_FOR_DAY) {
        return restrictionStatus(
            label = "오늘 거래 종료",
            summary = "서킷브레이커 3단계로 이 시장의 오늘 거래가 종료됐어요.",
        )
    }
    val reopeningEndsAt = state.reopeningEndsAt ?: endsAt
    if (reopeningEndsAt != null && currentTime >= reopeningEndsAt) return releasedStatus()
    val haltEndsAt = state.haltEndsAt
    return if (haltEndsAt != null && currentTime >= haltEndsAt) {
        restrictionStatus("재개 단일가", "연속매매 재개 전 단일가로 주문을 모으고 있어요.")
    } else {
        restrictionStatus("거래정지 중", "서킷브레이커로 이 시장의 주문 체결이 잠시 멈췄어요.")
    }
}

private fun MarketActionReference.krxSidecarStatus(
    currentTime: Instant,
    snapshot: TradingProtectionSnapshot,
): NewsEffectStatusUi {
    if (transition == MarketActionTransition.RELEASED) return releasedStatus()
    if (endsAt?.let { currentTime >= it } == true) {
        return releasedStatus("프로그램매매 호가 제한이 끝났어요.")
    }
    val market = markets.singleOrNull() ?: return releasedStatus()
    val state = snapshot.krxSidecars[market] ?: return releasedStatus()
    val active = state.triggeredAt == effectiveAt &&
        state.phase == KrxSidecarPhase.PROGRAM_FLOW_SUSPENDED &&
        state.releasedAt == null &&
        state.suspensionEndsAt?.let { currentTime < it } == true
    return if (active) {
        restrictionStatus("사이드카 발동 중", "해당 방향의 프로그램매매 호가만 일시적으로 제한돼요.")
    } else {
        releasedStatus("프로그램매매 호가 제한이 끝났어요.")
    }
}

private fun MarketActionReference.krxViStatus(
    currentTime: Instant,
    snapshot: TradingProtectionSnapshot,
): NewsEffectStatusUi {
    if (endsAt?.let { currentTime >= it } == true) {
        return releasedStatus("이 VI 단일가 구간은 끝났어요.")
    }
    val stockId = stockId ?: return releasedStatus()
    val state = snapshot.krxVolatilityInterruptions[stockId] ?: return releasedStatus()
    val active = state.triggerCount == triggerSequence &&
        state.triggeredAt == effectiveAt &&
        state.phase == KrxViPhase.CALL_AUCTION &&
        state.auctionEndsAt?.let { currentTime < it } == true
    return if (active) {
        restrictionStatus("VI 단일가 중", "가격 급변을 완화하기 위해 주문을 모아 한 가격으로 체결해요.")
    } else {
        releasedStatus("이 VI 단일가 구간은 끝났어요.")
    }
}

private fun MarketActionReference.usMarketCircuitBreakerStatus(
    currentTime: Instant,
    snapshot: TradingProtectionSnapshot,
): NewsEffectStatusUi {
    if (transition == MarketActionTransition.REOPENED) return releasedStatus()
    if (endsAt?.let { currentTime >= it } == true) {
        return releasedStatus("이 시장 전체 서킷브레이커 조치의 거래 제한 구간이 끝났어요.")
    }
    val state = snapshot.usMarketWideCircuitBreaker ?: return releasedStatus()
    val sameOccurrence = state.triggeredAt == effectiveAt && state.activeLevel?.ordinal?.plus(1) == stage
    if (!sameOccurrence) return releasedStatus()
    if (state.phase == UsMwcbPhase.CLOSED_FOR_DAY) {
        return restrictionStatus(
            label = "오늘 거래 종료",
            summary = "3단계 시장 전체 서킷브레이커로 미국 정규장이 종료됐어요.",
        )
    }
    val haltEndsAt = state.haltEndsAt
    return when {
        state.phase == UsMwcbPhase.NORMAL -> releasedStatus()
        state.phase == UsMwcbPhase.REOPENING_AUCTIONS || haltEndsAt?.let { currentTime >= it } == true ->
            restrictionStatus("재개 경매 중", "미국 거래소별 재개 경매가 순차적으로 진행되고 있어요.")
        else -> restrictionStatus("거래정지 중", "미국 시장 전체의 정규장 체결이 잠시 멈췄어요.")
    }
}

private fun MarketActionReference.usLuldStatus(
    currentTime: Instant,
    snapshot: TradingProtectionSnapshot,
    allEvents: List<GameEvent>,
): NewsEffectStatusUi {
    if (transition == MarketActionTransition.REOPENED) return releasedStatus()
    if (endsAt?.let { currentTime >= it } == true) {
        return releasedStatus("이 LULD 거래 제한 구간은 끝났어요.")
    }
    val stockId = stockId ?: return releasedStatus()
    val state = snapshot.usLuldStates[stockId] ?: return releasedStatus()
    val occurrenceStartedAt = allEvents.asSequence()
        .mapNotNull(GameEvent::marketAction)
        .firstOrNull { candidate ->
            candidate.kind == MarketActionKind.US_LIMIT_UP_LIMIT_DOWN &&
                candidate.occurrenceId == occurrenceId &&
                candidate.transition == MarketActionTransition.HALT_STARTED
        }
        ?.effectiveAt
        ?: effectiveAt
    val sameOccurrence = state.pauseStartedAt == occurrenceStartedAt
    if (!sameOccurrence) return releasedStatus()
    return when (transition) {
        MarketActionTransition.HALT_STARTED -> when (state.phase) {
            UsLuldPhase.TRADING_PAUSE -> if (state.pauseEndsAt?.let { currentTime < it } == true) {
                restrictionStatus("LULD 거래정지", "가격 급변으로 이 종목의 체결이 잠시 멈췄어요.")
            } else {
                restrictionStatus("재개 경매 중", "거래 재개 가격을 정하기 위한 경매가 진행 중이에요.")
            }
            UsLuldPhase.REOPENING_AUCTION ->
                restrictionStatus("재개 경매 중", "거래 재개 가격을 정하기 위한 경매가 진행 중이에요.")
            else -> releasedStatus("이 LULD 거래정지 구간은 끝났어요.")
        }
        MarketActionTransition.CLOSING_AUCTION_STARTED -> if (state.phase == UsLuldPhase.CLOSING_AUCTION_ONLY) {
            restrictionStatus("종가 경매만", "오늘은 연속매매 없이 종가 경매 절차만 진행해요.")
        } else {
            releasedStatus("이 종가 경매 조치는 끝났어요.")
        }
        else -> releasedStatus()
    }
}

private fun MarketActionReference.instrumentHaltStatus(
    currentTime: Instant,
    snapshot: TradingProtectionSnapshot,
): NewsEffectStatusUi {
    val stockId = stockId ?: return releasedStatus()
    val market = markets.singleOrNull() ?: return releasedStatus()
    val otherActiveHalt = snapshot.instrumentTradingHalts[stockId]?.takeIf { halt ->
        halt.occurrenceId != occurrenceId && TradingProtectionEngine.isInstrumentHaltActive(halt, currentTime)
    }
    if (transition == MarketActionTransition.RELEASED) {
        return instrumentHaltEndedStatus(market, effectiveAt, otherActiveHalt != null)
    }
    if (endsAt?.let { currentTime >= it } == true) {
        return instrumentHaltEndedStatus(market, requireNotNull(endsAt), otherActiveHalt != null)
    }
    val scheduled = snapshot.scheduledInstrumentTradingHalts.values.firstOrNull { halt ->
        halt.occurrenceId == occurrenceId
    }
    val current = snapshot.instrumentTradingHalts[stockId]?.takeIf { halt ->
        halt.occurrenceId == occurrenceId
    }
    val halt = scheduled ?: current ?: return releasedStatus()
    if (currentTime < halt.startedAt) {
        return NewsEffectStatusUi(
            state = NewsEffectState.UPCOMING,
            label = "거래정지 예정",
            summary = "예고된 정규장에 이 종목의 거래가 멈출 예정이에요.",
        )
    }
    return if (halt.status == TradingHaltStatus.ACTIVE &&
        TradingProtectionEngine.isInstrumentHaltActive(halt, currentTime)
    ) {
        restrictionStatus("거래정지 중", halt.detail)
    } else {
        releasedStatus("이 뉴스가 알린 거래정지는 끝났어요.")
    }
}

private fun instrumentHaltEndedStatus(
    market: Market,
    endedAt: Instant,
    anotherHaltContinues: Boolean,
): NewsEffectStatusUi = when {
    anotherHaltContinues -> resolvedStatus(
        label = "사유 종료",
        summary = "이 거래정지 사유는 끝났지만 다른 거래정지 조치가 이어지고 있어요.",
    )
    GameCalendar.isRegularMarketOpen(market, endedAt) -> releasedStatus(
        "거래정지 사유가 해소돼 정규장 주문과 체결이 다시 가능해졌어요.",
    )
    else -> resolvedStatus(
        label = "정지 일정 종료",
        summary = "거래정지 조치는 끝났지만 정규장은 닫혀 있어 다음 개장부터 거래할 수 있어요.",
    )
}

private fun MarketActionReference.investmentAlertStatus(
    currentTime: Instant,
    snapshot: TradingProtectionSnapshot,
    allEvents: List<GameEvent>,
): NewsEffectStatusUi {
    val stockId = stockId ?: return releasedStatus()
    val level = alertLevel ?: return releasedStatus()
    val effectiveOn = effectiveOn ?: return releasedStatus()
    val designation = snapshot.investmentAlerts[stockId] ?: return releasedStatus()
    val relatedActions = allEvents.asSequence()
        .mapNotNull(GameEvent::marketAction)
        .filter { action -> action.kind == MarketActionKind.INVESTMENT_ALERT && action.stockId == stockId }
        .toList()

    return when (transition) {
        MarketActionTransition.DESIGNATION_NOTICE -> {
            val supersedingDesignation = relatedActions
                .filter { action ->
                    action.transition == MarketActionTransition.DESIGNATED &&
                        action.alertLevel == level &&
                        action.announcedAt >= announcedAt
                }
                .minByOrNull(MarketActionReference::effectiveAt)
            val noticeStillReferenced = designation.level == level.priorLevel() &&
                designation.escalationNoticeOn == effectiveOn &&
                designation.escalationNoticeReasons.isNotEmpty()
            if (!noticeStillReferenced ||
                endsAt?.let { currentTime >= it } == true ||
                supersedingDesignation?.let { currentTime >= it.effectiveAt } == true
            ) {
                return resolvedStatus(
                    label = "관찰 종료",
                    summary = "이 지정예고의 거래일 기준 관찰 구간이 끝났어요.",
                )
            }
            NewsEffectStatusUi(
                state = NewsEffectState.INFORMATION,
                label = if (currentTime < effectiveAt) "지정예고" else "지정요건 관찰",
                summary = if (currentTime < effectiveAt) {
                    "$effectiveOn 정규장부터 ${level.label()} 지정 요건을 관찰해요."
                } else {
                    "종목의 실제 거래일 기준으로 ${level.label()} 지정 요건을 관찰하고 있어요."
                },
            )
        }
        MarketActionTransition.RELEASE_ANNOUNCED -> {
            if (currentTime < effectiveAt) {
                NewsEffectStatusUi(
                    state = NewsEffectState.UPCOMING,
                    label = "해제 예정",
                    summary = "$effectiveOn 정규장 개장부터 ${level.label()} 지정이 해제돼요.",
                )
            } else {
                releasedStatus("공시된 정규장 개장 시각부터 ${level.label()} 지정이 해제됐어요.")
            }
        }
        MarketActionTransition.DESIGNATED -> {
            val release = relatedActions
                .filter { action ->
                    action.occurrenceId == occurrenceId &&
                        action.transition == MarketActionTransition.RELEASE_ANNOUNCED &&
                        action.announcedAt >= announcedAt
                }
                .maxByOrNull(MarketActionReference::announcedAt)
            val replacement = relatedActions
                .filter { action ->
                    action.transition == MarketActionTransition.DESIGNATED &&
                        action.occurrenceId != occurrenceId &&
                        action.announcedAt > announcedAt
                }
                .minByOrNull(MarketActionReference::effectiveAt)
            if (release?.let { currentTime >= it.effectiveAt } == true) {
                return releasedStatus("${level.label()} 지정의 해제 효력이 발생했어요.")
            }
            if (replacement?.let { currentTime >= it.effectiveAt } == true) {
                return resolvedStatus(
                    label = "이전 단계",
                    summary = "이 지정 단계는 다음 시장경보 단계로 대체됐어요.",
                )
            }
            if (currentTime < effectiveAt) {
                return NewsEffectStatusUi(
                    state = NewsEffectState.UPCOMING,
                    label = "${level.label()} 예정",
                    summary = "$effectiveOn 정규장 개장부터 ${level.label()} 지정이 적용돼요.",
                )
            }
            if (release != null && currentTime < release.effectiveAt) {
                return restrictionStatus(
                    level.label(),
                    "해제가 공시됐지만 효력 시각 전까지 ${level.label()} 상태가 유지돼요.",
                )
            }
            if (replacement != null && currentTime < replacement.effectiveAt) {
                return restrictionStatus(
                    level.label(),
                    "새 시장경보의 효력 시각 전까지 ${level.label()} 상태가 유지돼요.",
                )
            }
            val exactCurrent = designation.level == level && designation.designatedAt == announcedAt
            if (exactCurrent && designation.status == InvestmentAlertStatus.ACTIVE) {
                restrictionStatus(level.label(), "현재 ${level.label()} 지정이 적용되고 있어요.")
            } else {
                releasedStatus("${level.label()} 지정이 해제되거나 다음 단계로 넘어갔어요.")
            }
        }
        else -> releasedStatus()
    }
}

private fun MarketActionReference.listingLifecycleStatus(
    listingStates: Map<String, ListingLifecycleState>,
    ledger: List<ListingLifecycleLedgerEvent>,
): NewsEffectStatusUi {
    val stockId = stockId ?: return resolvedStatus()
    val sequence = listingLedgerSequence ?: return resolvedStatus()
    val transitionEvent = ledger.firstOrNull { event ->
        event.id == occurrenceId && event.stockId == stockId && event.sequence == sequence
    } ?: return resolvedStatus(summary = "상장 원장에서 이 단계의 기록을 찾을 수 없어요.")
    val current = listingStates[stockId] ?: return resolvedStatus()
    val superseded = current.ledgerSequence > sequence || current.status != transitionEvent.toStatus
    if (superseded) {
        return resolvedStatus(
            label = "이전 단계",
            summary = "이 상장 조치는 다음 원장 단계로 넘어가 과거 기록으로 남아 있어요.",
        )
    }
    return when (transitionEvent.kind) {
        ListingLifecycleEventKind.DEFICIENCY_CURED,
        ListingLifecycleEventKind.TRADING_RESUMED,
        -> releasedStatus("상장 관련 조치가 해제되어 현재 정상 상장 상태예요.")
        ListingLifecycleEventKind.DELISTED,
        ListingLifecycleEventKind.TERMINATED,
        -> resolvedStatus(
            label = transitionEvent.toStatus.displayName,
            summary = "${transitionEvent.toStatus.displayName} 절차가 끝나 최종 상태가 확정됐어요.",
        )
        else -> restrictionStatus(
            label = transitionEvent.toStatus.displayName,
            summary = "현재 ${transitionEvent.toStatus.displayName} 절차가 진행 중이에요.",
        )
    }
}

private fun InvestmentAlertLevel.priorLevel(): InvestmentAlertLevel? = when (this) {
    InvestmentAlertLevel.CAUTION -> null
    InvestmentAlertLevel.WARNING -> InvestmentAlertLevel.CAUTION
    InvestmentAlertLevel.DANGER -> InvestmentAlertLevel.WARNING
}

private fun InvestmentAlertLevel.label(): String = when (this) {
    InvestmentAlertLevel.CAUTION -> "투자주의"
    InvestmentAlertLevel.WARNING -> "투자경고"
    InvestmentAlertLevel.DANGER -> "투자위험"
}

private fun restrictionStatus(label: String, summary: String): NewsEffectStatusUi = NewsEffectStatusUi(
    state = NewsEffectState.RESTRICTION_ACTIVE,
    label = label,
    summary = summary,
)

private fun releasedStatus(summary: String = "이 조치는 해제됐고 과거 기록으로 남아 있어요."): NewsEffectStatusUi =
    NewsEffectStatusUi(
        state = NewsEffectState.RESOLVED,
        label = "해제됨",
        summary = summary,
    )

private fun resolvedStatus(
    label: String = "처리 완료",
    summary: String = "이 절차는 끝났고 과거 기록으로 남아 있어요.",
): NewsEffectStatusUi = NewsEffectStatusUi(
    state = NewsEffectState.RESOLVED,
    label = label,
    summary = summary,
)

/**
 * 작성자가 선언한 insight와 실제 causal graph trace만 경로 카드로 만든다. 스코프가 겹친다는
 * 이유만으로 합성 경로를 만들지 않으므로 scope fallback은 관련 종목에는 포함돼도 이 목록에는 없다.
 */
private fun GameEvent.impactPaths(
    stockById: Map<String, StockDefinition>,
    holdingIds: Set<String>,
    watchlistIds: Set<String>,
): List<NewsImpactPathUi> {
    val explicitPaths = impactInsights.mapIndexed { index, insight ->
        val targetStock = insight.stockId?.let(stockById::get)
        val relatedStockIds = stockById.values
            .filter { stock -> insight in resolvedImpactFor(stock).insights }
            .mapTo(linkedSetOf(), StockDefinition::id)
        NewsImpactPathUi(
            id = "$id:impact:$index",
            label = insight.targetLabel,
            categoryLabel = insight.targetKind.displayName,
            direction = targetStock?.let(::directionFor) ?: insight.direction,
            reason = targetStock?.let { stock ->
                stock.enrichImpactReason(
                    authoredNarrativeFor(stock, insight.rationale, insight.relativeSensitivity),
                )
            } ?: insight.rationale,
            sector = insight.sector ?: targetStock?.sector,
            industrySegment = insight.industrySegment,
            stockId = insight.stockId,
            held = relatedStockIds.any(holdingIds::contains),
            watched = relatedStockIds.any(watchlistIds::contains),
            horizonLabel = insight.horizon.displayName,
        )
    }

    val causalPaths = stockById.values.asSequence()
        .sortedBy(StockDefinition::id)
        .mapNotNull { stock ->
            val causal = resolvedImpactFor(stock).causalImpact ?: return@mapNotNull null
            val narrative = causalNarrativeFor(stock, causal) ?: return@mapNotNull null
            val trace = narrative.trace
            val terminal = trace.nodes.last()
            NewsImpactPathUi(
                id = "$id:causal:${stock.id}",
                label = stock.name,
                categoryLabel = "인과 전파",
                direction = directionFor(stock),
                reason = stock.enrichImpactReason(
                    base = narrative.text,
                    includeInverseStructure = !narrative.productDirectionInverted,
                ),
                sector = terminal.sector ?: stock.sector,
                industrySegment = terminal.industrySegment,
                stockId = stock.id,
                held = stock.id in holdingIds,
                watched = stock.id in watchlistIds,
                horizonLabel = "단기",
            ) to causal
        }
        .sortedWith(
            compareByDescending<Pair<NewsImpactPathUi, com.amond.kmpbook.domain.model.causal.CausalStockImpact>> {
                it.first.held
            }.thenByDescending { it.first.watched }
                .thenByDescending { it.second.specificity }
                .thenByDescending { it.second.relativeSensitivity }
                .thenBy { it.first.label }
                .thenBy { it.first.stockId },
        )
        .take(MAX_CAUSAL_IMPACT_PATHS)
        .map(Pair<NewsImpactPathUi, com.amond.kmpbook.domain.model.causal.CausalStockImpact>::first)
        .toList()

    return explicitPaths + causalPaths
}

private fun GameEvent.relatedStocks(
    stockById: Map<String, StockDefinition>,
    holdingIds: Set<String>,
    watchlistIds: Set<String>,
): List<NewsRelatedStockUi> {
    return stockById.values.asSequence()
        .sortedBy(StockDefinition::id)
        .mapNotNull { stock ->
            val coverage = impactCoverageFor(stock)
            if (!coverage.isAffected) return@mapNotNull null

            val resolvedImpact = resolvedImpactFor(stock)
            val resolvedInsights = resolvedImpact.insights
            val causalImpact = resolvedImpact.causalImpact
            val causalNarrative = causalImpact?.let { impact -> causalNarrativeFor(stock, impact) }
            val directInsightExposure = resolvedInsights.any { insight ->
                insight.targetKind == EventImpactTargetKind.STOCK && insight.stockId == stock.id
            }
            val underlyingInsightExposure = resolvedInsights.any { insight ->
                insight.targetKind == EventImpactTargetKind.STOCK && insight.stockId != stock.id
            }
            val directScopeExposure = coverage.usesScopeFallback &&
                scope == EventScope.STOCK && stock.id in affectedStockIds
            val underlyingScopeExposure = coverage.usesScopeFallback &&
                scope == EventScope.STOCK && !directScopeExposure &&
                stock.identityProfile?.underlyingInstrumentIds?.any(affectedStockIds::contains) == true
            val directTarget = directInsightExposure || directScopeExposure
            val specificity = causalImpact?.specificity ?: resolvedInsights.maxOfOrNull { it.specificity } ?: when (scope) {
                EventScope.STOCK -> EventImpactTargetKind.STOCK.specificityRank
                EventScope.SECTOR -> EventImpactTargetKind.INDUSTRY.specificityRank
                EventScope.GLOBAL,
                EventScope.COUNTRY,
                EventScope.MARKET,
                -> EventImpactTargetKind.MARKET.specificityRank
            }
            val relationKind = when {
                directTarget -> NewsStockRelationKind.DIRECT_TARGET
                underlyingInsightExposure || underlyingScopeExposure ->
                    NewsStockRelationKind.UNDERLYING_EXPOSURE
                resolvedImpact.source == EventImpactResolutionSource.CAUSAL_GRAPH ->
                    NewsStockRelationKind.CAUSAL_CHAIN
                resolvedInsights.any { it.targetKind == EventImpactTargetKind.INDUSTRY_SEGMENT } ->
                    NewsStockRelationKind.INDUSTRY_SEGMENT
                resolvedInsights.any { it.targetKind == EventImpactTargetKind.INDUSTRY } ||
                    coverage.usesScopeFallback && scope == EventScope.SECTOR ->
                    NewsStockRelationKind.INDUSTRY
                else -> NewsStockRelationKind.MARKET_CONTEXT
            }
            val authoredRationale = resolvedInsights
                .map { it.rationale }
                .distinct()
                .joinToString(" ")
            val analysisReason = causalNarrative?.text
                ?: causalImpact?.primaryTrace?.rationale
                ?: authoredRationale.takeIf(String::isNotBlank)?.let { rationale ->
                    authoredNarrativeFor(stock, rationale, resolvedImpact.relativeSensitivity)
                }
                ?: fallbackStockReason(stock)
            val relationAwareReason = if (
                relationKind == NewsStockRelationKind.UNDERLYING_EXPOSURE && resolvedInsights.isNotEmpty()
            ) {
                "$analysisReason 이 상품은 뉴스 대상 종목을 기초자산으로 연결해 영향을 받아요."
            } else {
                analysisReason
            }
            NewsRelatedStockUi(
                stockId = stock.id,
                name = stock.name,
                symbol = stock.symbol,
                direction = directionFor(stock),
                reason = stock.enrichImpactReason(
                    base = relationAwareReason,
                    includeInverseStructure = causalNarrative?.productDirectionInverted != true,
                ),
                held = stock.id in holdingIds,
                watched = stock.id in watchlistIds,
                directTarget = directTarget,
                specificity = specificity,
                relationKind = relationKind,
                causalTraceLabels = causalNarrative?.presentationTraceLabels(stock).orEmpty().let { labels ->
                    if (resolvedImpact.source != EventImpactResolutionSource.CAUSAL_GRAPH || labels.isEmpty()) {
                        emptyList()
                    } else {
                        labels
                    }
                },
                relativeSensitivity = resolvedImpact.relativeSensitivity,
                confidence = causalImpact?.confidence ?: when (resolvedImpact.source) {
                    EventImpactResolutionSource.EXPLICIT_PATH -> 1.0
                    EventImpactResolutionSource.SCOPE_FALLBACK -> 0.50
                    EventImpactResolutionSource.CAUSAL_GRAPH -> 0.0
                    EventImpactResolutionSource.NONE -> 0.0
                },
            )
        }
        .toList()
        .sortedWith(
            compareByDescending<NewsRelatedStockUi> { it.held }
                .thenByDescending { it.watched }
                .thenByDescending { it.directTarget }
                .thenByDescending { it.specificity }
                .thenBy { it.name }
                .thenBy { it.stockId },
        )
}

private fun GameEvent.fallbackStockReason(stock: StockDefinition): String = when (scope) {
    EventScope.STOCK -> if (stock.id in affectedStockIds) {
        "이 종목이 뉴스의 직접 대상이에요."
    } else {
        "이 상품이 보유한 기초자산이 뉴스의 직접 대상이라 구조를 통해 영향이 전달돼요."
    }
    EventScope.SECTOR -> {
        val matchedSectors = affectedSectors
            .filter(stock::hasNewsSectorExposure)
            .sortedBy(Sector::displayName)
            .joinToString("·", transform = Sector::displayName)
        "$matchedSectors 업황과 수급 변화에 노출돼 있어요."
    }
    EventScope.MARKET -> {
        val matchedMarkets = affectedMarkets
            .filter(stock::hasNewsMarketExposure)
            .sortedBy(Market::displayName)
            .joinToString("·", transform = Market::displayName)
        "$matchedMarkets 위험선호와 수급 변화가 상장시장 또는 기초자산 노출을 통해 전달돼요."
    }
    EventScope.COUNTRY -> {
        val countryNames = affectedMarkets
            .filter(stock::hasNewsMarketExposure)
            .map(Market::countryName)
            .distinct()
            .sorted()
            .joinToString("·")
        "$countryNames 시장의 위험선호와 수급 변화가 상장시장 또는 기초자산 노출을 통해 전달돼요."
    }
    EventScope.GLOBAL -> "글로벌 위험선호와 자금 흐름을 통해 영향을 받아요."
}

private fun StockCausalNarrative.presentationTraceLabels(stock: StockDefinition): List<String> {
    val labels = trace.labels
    if (!productDirectionInverted) {
        return if (labels.lastOrNull() == stock.name) labels else labels + stock.name
    }
    val underlyingPath = if (labels.lastOrNull() == stock.name) labels.dropLast(1) else labels
    return underlyingPath + "기초자산 가격" + "일일 인버스 구조" + stock.name
}

private fun StockDefinition.enrichImpactReason(
    base: String,
    includeInverseStructure: Boolean = true,
): String {
    val structureNotes = buildList {
        if (includeInverseStructure && behavior.strategy == InstrumentStrategy.DAILY_INVERSE) {
            add("인버스 상품이라 기초자산과 반대 방향으로 반응하고, 일일 재조정 때문에 장기 성과는 단순 역수익률과 달라질 수 있어요.")
        }
        val hasCoveredCallOverlay = behavior.strategy == InstrumentStrategy.COVERED_CALL ||
            identityProfile?.eventRiskTags?.contains("commodity_option") == true
        if (hasCoveredCallOverlay) {
            add("커버드콜 구조라 옵션 프리미엄을 받는 대신 강한 상승에서는 참여 폭이 제한될 수 있어요.")
        }
        if (etfProfile?.fxProfile?.isFullyHedged == true) {
            add("환헤지 상품이라 상장통화와 기초통화 사이의 환율 영향은 대부분 줄여서 반영해요.")
        } else if (etfProfile?.fxProfile?.legs?.any { leg ->
                leg.currency != ReferenceCurrency.KRW && leg.netNotional > MATERIAL_FX_EXPOSURE
            } == true
        ) {
            add("환노출 상품이라 기초자산 변화와 통화 가치 변화가 함께 성과에 반영될 수 있어요.")
        }
    }
    return (listOf(base) + structureNotes).distinct().joinToString(" ")
}

private fun StockDefinition.hasNewsSectorExposure(target: Sector): Boolean {
    val explicitExposure = identityProfile?.exposedSectors.orEmpty()
    return when {
        explicitExposure.isNotEmpty() -> target in explicitExposure
        !isFundLike -> sector == target
        etfProfile?.assetClass == EtfAssetClass.SECTOR_EQUITY -> sector == target
        else -> false
    }
}

private fun StockDefinition.hasNewsMarketExposure(target: Market): Boolean =
    market == target || etfProfile?.isExposedTo(target) == true

private fun NewsStoryUi.targetsIndustry(industry: NewsIndustryKey): Boolean = impactPaths.any { path ->
    if (industry.segment == null) {
        path.sector == industry.sector
    } else {
        path.industrySegment == industry.segment
    }
}

private fun NewsStoryUi.hasPersonalIndustryPath(industry: NewsIndustryKey): Boolean = impactPaths.any { path ->
    val matches = if (industry.segment == null) {
        path.sector == industry.sector
    } else {
        path.industrySegment == industry.segment
    }
    matches && (path.held || path.watched)
}

private fun List<ImpactDirection>.combinedDirection(): ImpactDirection {
    if (isEmpty()) return ImpactDirection.NEUTRAL
    if (ImpactDirection.MIXED in this) return ImpactDirection.MIXED
    val directional = filter { it != ImpactDirection.NEUTRAL }.distinct()
    return when (directional.size) {
        0 -> ImpactDirection.NEUTRAL
        1 -> directional.single()
        else -> ImpactDirection.MIXED
    }
}

internal val NewsEffectState.activityPriority: Int
    get() = when (this) {
        NewsEffectState.MARKET_ACTIVE -> 3
        NewsEffectState.RESTRICTION_ACTIVE -> 2
        NewsEffectState.PROCESS_ACTIVE -> 1
        else -> 0
    }

private const val HOME_STORY_LIMIT = 4
private const val MAX_CAUSAL_IMPACT_PATHS = 12
private const val MATERIAL_FX_EXPOSURE = 0.05
private val OPERATIONAL_RECORD_KINDS = setOf(
    EventRecordKind.MARKET_ACTION,
    EventRecordKind.CORPORATE_ACTION,
    EventRecordKind.INSTRUMENT_LIFECYCLE,
)

private val EventImpactTargetKind.specificityRank: Int
    get() = when (this) {
        EventImpactTargetKind.MARKET -> 1
        EventImpactTargetKind.INDUSTRY -> 2
        EventImpactTargetKind.INDUSTRY_SEGMENT -> 3
        EventImpactTargetKind.STOCK -> 4
    }
