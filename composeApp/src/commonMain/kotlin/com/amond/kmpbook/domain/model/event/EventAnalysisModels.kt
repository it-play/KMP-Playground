package com.amond.kmpbook.domain.model.event

import com.amond.kmpbook.domain.model.instrument.EtfAssetClass
import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.market.Sector
import com.amond.kmpbook.domain.simulation.causal.CausalMarketEngine
import com.amond.kmpbook.domain.simulation.causal.MarketContagionEngine

internal fun GameEvent.impactCoverageFor(stock: StockDefinition): EventImpactCoverageMatch {
    val applicableInsights = impactInsights.filter { it.appliesTo(stock) }
    val causalImpact = causalSignals
        .takeIf { applicableInsights.isEmpty() && it.isNotEmpty() }
        ?.let { signals ->
            when (scope) {
                EventScope.GLOBAL,
                EventScope.SECTOR,
                -> CausalMarketEngine.impactFor(signals, stock)
                EventScope.COUNTRY,
                EventScope.MARKET,
                -> MarketContagionEngine.impactFor(
                    seeds = signals,
                    sourceMarkets = affectedMarkets,
                    stock = stock,
                    regimeSnapshot = marketRegimeSnapshot,
                )
                EventScope.STOCK -> signals
                    .takeIf { affectsByScope(stock) }
                    ?.let { directSignals -> CausalMarketEngine.impactFor(directSignals, stock) }
            }
        }
    val usesScopeFallback = applicableInsights.isEmpty() && causalImpact == null && causalSignals.isEmpty() &&
        impactCoveragePolicy == EventImpactCoveragePolicy.SCOPE_FALLBACK_WITH_OVERRIDES &&
        affectsByScope(stock)
    return EventImpactCoverageMatch(
        applicableInsights = applicableInsights,
        causalImpact = causalImpact,
        usesScopeFallback = usesScopeFallback,
    )
}

/** ETF의 명시적 산업 바스켓을 우선하며, 일반 종목에는 대표 산업을 사용한다. */
internal fun StockDefinition.isExposedToSector(target: Sector): Boolean {
    val explicitExposure = identityProfile?.exposedSectors.orEmpty()
    return when {
        explicitExposure.isNotEmpty() -> target in explicitExposure
        !isFundLike -> sector == target
        etfProfile?.assetClass == EtfAssetClass.SECTOR_EQUITY -> sector == target
        else -> false
    }
}
