package com.amond.kmpbook.domain.simulation

import com.amond.kmpbook.domain.model.EventScope
import com.amond.kmpbook.domain.model.EventSeverity
import com.amond.kmpbook.domain.model.EventType
import com.amond.kmpbook.domain.model.CausalMarketRegimeSnapshot
import com.amond.kmpbook.domain.model.CausalSignalSeed
import com.amond.kmpbook.domain.model.EventImpactInsight
import com.amond.kmpbook.domain.model.EventImpactCoveragePolicy
import com.amond.kmpbook.domain.model.EventRecordKind
import com.amond.kmpbook.domain.model.EventTradingHaltDirective
import com.amond.kmpbook.domain.model.GameEvent
import com.amond.kmpbook.domain.model.GameEventImpact
import com.amond.kmpbook.domain.model.ImpactDirection
import com.amond.kmpbook.domain.model.InstrumentTerminationKind
import com.amond.kmpbook.domain.model.InstrumentTerminationTerms
import com.amond.kmpbook.domain.model.InstrumentTerminationValuationMethod
import com.amond.kmpbook.domain.model.InstrumentStrategy
import com.amond.kmpbook.domain.model.InstrumentType
import com.amond.kmpbook.domain.model.ListingFinalDispositionType
import com.amond.kmpbook.domain.model.ListingRecoveryCondition
import com.amond.kmpbook.domain.model.ListingRiskTag
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.MIN_CAUSAL_SIGNAL_STRENGTH
import com.amond.kmpbook.domain.model.Sector
import com.amond.kmpbook.domain.model.StockDefinition
import com.amond.kmpbook.domain.model.isDirectProductImpactFor
import com.amond.kmpbook.domain.model.resolvedImpactFor
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

data class EventGenerationContext(
    val timestamp: Instant,
    val stocks: List<StockDefinition>,
    val macro: MacroEnvironment = MacroEnvironment(),
    /** 설정 원시값이 아니라 동역학 엔진이 평활화한 현재 실효 환경이다. */
    val externalForces: ExternalMarketForces = ExternalMarketForces(),
    /** 모든 템플릿이 경쟁하는 총 hazard의 bounded Hawkes 배율이다. */
    val newsHazardMultiplier: Double = 1.0,
    val elapsedHours: Int = 1,
    /** Events restored or owned by an outer game state are also deduplicated. */
    val existingEvents: List<GameEvent> = emptyList(),
    /** 같은 정기 경제발표를 다른 확률 뉴스로 즉시 재서술하지 않기 위한 원인군 억제. */
    val suppressedTemplateIds: Set<String> = emptySet(),
    val maxNewEvents: Int = 3,
) {
    init {
        require(elapsedHours > 0)
        require(maxNewEvents >= 0)
        require(newsHazardMultiplier.isFinite() && newsHazardMultiplier in 0.25..3.5)
        require(stocks.map(StockDefinition::id).distinct().size == stocks.size) {
            "Event candidates must have unique stock ids"
        }
        require(suppressedTemplateIds.none(String::isBlank))
    }
}
