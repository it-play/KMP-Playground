package com.amond.kmpbook.domain.model.event

import com.amond.kmpbook.domain.model.causal.CausalStockImpact

/** [GameEvent.affects]와 가격 해석이 같은 커버리지 판정을 공유한다. */
internal data class EventImpactCoverageMatch(
    val applicableInsights: List<EventImpactInsight>,
    val causalImpact: CausalStockImpact?,
    val usesScopeFallback: Boolean,
) {
    val isAffected: Boolean
        get() = applicableInsights.isNotEmpty() || causalImpact != null || usesScopeFallback
}
