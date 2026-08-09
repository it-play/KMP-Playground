package com.amond.kmpbook.domain.simulation.event

import com.amond.kmpbook.domain.model.event.GameEvent
import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.simulation.market.ExternalMarketForces
import com.amond.kmpbook.domain.simulation.market.MacroEnvironment
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
