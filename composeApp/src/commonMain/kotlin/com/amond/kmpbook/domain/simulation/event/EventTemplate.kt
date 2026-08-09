package com.amond.kmpbook.domain.simulation.event

import com.amond.kmpbook.domain.model.causal.CausalSignalSeed
import com.amond.kmpbook.domain.model.causal.MIN_CAUSAL_SIGNAL_STRENGTH
import com.amond.kmpbook.domain.model.event.EventImpactCoveragePolicy
import com.amond.kmpbook.domain.model.event.EventImpactInsight
import com.amond.kmpbook.domain.model.event.EventRecordKind
import com.amond.kmpbook.domain.model.event.EventScope
import com.amond.kmpbook.domain.model.event.EventSeverity
import com.amond.kmpbook.domain.model.event.EventTradingHaltDirective
import com.amond.kmpbook.domain.model.event.EventType
import com.amond.kmpbook.domain.model.event.ImpactDirection
import com.amond.kmpbook.domain.model.instrument.InstrumentStrategy
import com.amond.kmpbook.domain.model.instrument.InstrumentType
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingFinalDispositionType
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingRecoveryCondition
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingRiskTag
import com.amond.kmpbook.domain.model.listing.termination.InstrumentTerminationKind
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.market.Sector
import com.amond.kmpbook.domain.model.marketaction.MarketActionReference

/** Declarative event rule. No random or clock state is hidden in a template. */
data class EventTemplate(
    val id: String,
    val titleTemplate: String,
    val descriptionTemplate: String,
    val scope: EventScope,
    val type: EventType,
    val severity: EventSeverity,
    val direction: ImpactDirection,
    /** Probability of at least one occurrence during a 24-hour game interval. */
    val probabilityPerDay: Double,
    val cooldownHours: Int,
    val durationHours: IntRange,
    val shockReturn: ClosedFloatingPointRange<Double>,
    val hourlyDrift: ClosedFloatingPointRange<Double> = 0.0..0.0,
    val volatilityMultiplier: ClosedFloatingPointRange<Double> = 1.0..1.0,
    val volumeMultiplier: ClosedFloatingPointRange<Double> = 1.0..1.0,
    val liquidityMultiplier: ClosedFloatingPointRange<Double> = 1.0..1.0,
    val sentiment: ClosedFloatingPointRange<Double> = 0.0..0.0,
    val condition: EventCondition = EventCondition.ALWAYS,
    val eligibleMarkets: Set<Market> = emptySet(),
    val eligibleSectors: Set<Sector> = emptySet(),
    val eligibleInstrumentTypes: Set<InstrumentType> = emptySet(),
    /** 비어 있으면 모든 전략, 값이 있으면 실제 수익 구조가 일치하는 상품만 후보가 된다. */
    val eligibleStrategies: Set<InstrumentStrategy> = emptySet(),
    /** 구조적 제도·유통 변경처럼 캠페인에서 한 번만 발생해야 하는 사건이다. */
    val oneShot: Boolean = false,
    /** ID 규칙이 아닌 템플릿 자체가 선언하는 기록 출처다. */
    val recordKind: EventRecordKind = EventRecordKind.NEWS,
    /** 같은 사건이 서로 다른 시장·산업·종목에 전달되는 구조화된 분석 경로다. */
    val impactInsights: List<EventImpactInsight> = emptyList(),
    /** 경제 요인 그래프에 넣을 저장 가능한 시작 신호. 생성 이벤트에 그대로 복사한다. */
    val causalSignals: List<CausalSignalSeed> = emptyList(),
    /** 분석 경로에 없는 스코프 대상으로 기본 영향을 확장할지 결정한다. */
    val impactCoveragePolicy: EventImpactCoveragePolicy =
        EventImpactCoveragePolicy.SCOPE_FALLBACK_WITH_OVERRIDES,
    /** 가격 효과와 독립적으로 상장 생애주기 엔진에 전달되는 거래소 감시 신호. */
    val listingRiskTags: Set<ListingRiskTag> = emptySet(),
    val listingRecoveryConditions: Set<ListingRecoveryCondition> = emptySet(),
    val listingFinalDispositionHint: ListingFinalDispositionType? = null,
    /** 상품 종료 공시는 생성 시 실제 효력 시각·현금평가 조건으로 고정된다. */
    val terminationTemplate: EventTerminationTemplate? = null,
    /** 공시가 유발하는 거래정지 조건은 생성된 이벤트에 그대로 복사한다. */
    val tradingHaltDirective: EventTradingHaltDirective? = null,
    val sourceLabel: String = "시뮬레이션 뉴스",
) {
    init {
        require(id.isNotBlank())
        require(titleTemplate.isNotBlank() && descriptionTemplate.isNotBlank())
        require(probabilityPerDay in 0.0..1.0)
        require(cooldownHours >= 0)
        require(!durationHours.isEmpty() && durationHours.first > 0)
        require(shockReturn.isFiniteOrdered() && shockReturn.start > -1.0)
        require(hourlyDrift.isFiniteOrdered())
        require(volatilityMultiplier.isFiniteOrdered() && volatilityMultiplier.start >= 0.0)
        require(volumeMultiplier.isFiniteOrdered() && volumeMultiplier.start >= 0.0)
        require(liquidityMultiplier.isFiniteOrdered() && liquidityMultiplier.start >= 0.0)
        require(
            sentiment.isFiniteOrdered() &&
                sentiment.start >= -1.0 && sentiment.endInclusive <= 1.0,
        )
        require(recordKind != EventRecordKind.MARKET_ACTION) {
            "Market-action records require a runtime MarketActionReference and cannot be stochastic templates"
        }
        require(
            impactCoveragePolicy != EventImpactCoveragePolicy.EXPLICIT_PATHS_ONLY ||
                impactInsights.isNotEmpty() || causalSignals.isNotEmpty(),
        ) { "Explicit-path event templates require an impact insight or causal signal" }
        require(causalSignals.map(CausalSignalSeed::factor).distinct().size == causalSignals.size) {
            "Event templates cannot declare the same causal factor twice"
        }
        require(causalSignals.all { it.strength >= MIN_CAUSAL_SIGNAL_STRENGTH }) {
            "Event template causal strengths must be at least $MIN_CAUSAL_SIGNAL_STRENGTH"
        }
        require(
            scope !in setOf(EventScope.COUNTRY, EventScope.MARKET) ||
                causalSignals.map(CausalSignalSeed::transmissionProfile).distinct().size <= 1,
        ) {
            "Country and market event templates must use one transmission profile per event"
        }
        terminationTemplate?.let { terms ->
            require(scope == EventScope.STOCK && recordKind == EventRecordKind.INSTRUMENT_LIFECYCLE) {
                "Instrument termination templates must be stock lifecycle records"
            }
            require(listingFinalDispositionHint == null && terms.kind.terminationRiskTag !in listingRiskTags) {
                "Instrument termination templates derive disposition and risk tags from their terms"
            }
        }
        tradingHaltDirective?.let { directive ->
            require(scope == EventScope.STOCK) {
                "Event-driven trading halts must target a stock event"
            }
            require(directive.semanticInvariantViolation() == null) {
                "Event-driven trading halt directive is invalid"
            }
        }
    }
}

private fun ClosedFloatingPointRange<Double>.isFiniteOrdered(): Boolean =
    start.isFinite() && endInclusive.isFinite() && start <= endInclusive

private val InstrumentTerminationKind.terminationRiskTag: ListingRiskTag
    get() = when (this) {
        InstrumentTerminationKind.FUND_LIQUIDATION -> ListingRiskTag.ETF_LIQUIDATION_APPROVED
        else -> ListingRiskTag.ETN_MATURITY_OR_EARLY_REDEMPTION
    }
