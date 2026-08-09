package com.amond.kmpbook.domain.model.news

import com.amond.kmpbook.domain.model.causal.CausalTransmissionProfile
import com.amond.kmpbook.domain.model.event.EventImpactCoveragePolicy
import com.amond.kmpbook.domain.model.event.EventImpactInsight
import com.amond.kmpbook.domain.model.event.EventImpactResolutionSource
import com.amond.kmpbook.domain.model.event.EventImpactTargetKind
import com.amond.kmpbook.domain.model.event.EventScope
import com.amond.kmpbook.domain.model.event.GameEvent
import com.amond.kmpbook.domain.model.event.ImpactDirection
import com.amond.kmpbook.domain.model.event.ResolvedEventImpact
import com.amond.kmpbook.domain.model.event.impactCoverageFor
import com.amond.kmpbook.domain.model.instrument.StockDefinition

fun GameEvent.relevanceTo(
    stocks: List<StockDefinition>,
    holdingIds: Set<String>,
    watchlistIds: Set<String>,
): NewsRelevance {
    val byId = stocks.associateBy(StockDefinition::id)
    fun matching(ids: Set<String>): Set<String> = ids.filterTo(linkedSetOf()) { id ->
        byId[id]?.let(::affects) == true
    }
    val relatedSectors = buildSet {
        val includesScopeFallback =
            impactCoveragePolicy == EventImpactCoveragePolicy.SCOPE_FALLBACK_WITH_OVERRIDES
        if (includesScopeFallback) addAll(affectedSectors)
        addAll(impactInsights.mapNotNull(EventImpactInsight::sector))
        stocks.filter { stock -> impactCoverageFor(stock).causalImpact != null }.forEach { stock ->
            val exposure = stock.identityProfile?.exposedSectors.orEmpty()
            if (exposure.isEmpty()) add(stock.sector) else addAll(exposure)
        }
        val scopeStockIds = if (includesScopeFallback) affectedStockIds else emptySet()
        val directlyNamedStockIds = scopeStockIds + impactInsights.mapNotNull(EventImpactInsight::stockId)
        directlyNamedStockIds.mapNotNull(byId::get).forEach { stock ->
            val exposure = stock.identityProfile?.exposedSectors.orEmpty()
            if (exposure.isEmpty()) add(stock.sector) else addAll(exposure)
        }
    }
    return NewsRelevance(
        heldStockIds = matching(holdingIds),
        watchedStockIds = matching(watchlistIds),
        relatedSectors = relatedSectors,
    )
}

fun GameEvent.resolvedImpactFor(stock: StockDefinition): ResolvedEventImpact {
    val coverage = impactCoverageFor(stock)
    val applicable = coverage.applicableInsights
    if (!coverage.isAffected) {
        return ResolvedEventImpact(
            direction = ImpactDirection.NEUTRAL,
            relativeSensitivity = 0.0,
            insights = emptyList(),
            source = EventImpactResolutionSource.NONE,
        )
    }
    coverage.causalImpact?.let { causal ->
        return ResolvedEventImpact(
            direction = causal.direction,
            relativeSensitivity = causal.relativeSensitivity,
            insights = emptyList(),
            source = EventImpactResolutionSource.CAUSAL_GRAPH,
            causalImpact = causal,
        )
    }
    if (coverage.usesScopeFallback) {
        return ResolvedEventImpact(
            direction = impact.direction,
            relativeSensitivity = 1.0,
            insights = emptyList(),
            source = EventImpactResolutionSource.SCOPE_FALLBACK,
        )
    }
    val maximumSpecificity = applicable.maxOf(EventImpactInsight::specificity)
    val mostSpecific = applicable.filter { it.specificity == maximumSpecificity }
    val directionalInsights = mostSpecific.filter {
        it.direction == ImpactDirection.POSITIVE || it.direction == ImpactDirection.NEGATIVE
    }
    val signedSensitivity = directionalInsights.takeIf(List<EventImpactInsight>::isNotEmpty)
        ?.map { insight ->
            if (insight.direction == ImpactDirection.POSITIVE) {
                insight.relativeSensitivity
            } else {
                -insight.relativeSensitivity
            }
        }
        ?.average()
    val resolvedDirection = when {
        signedSensitivity != null && signedSensitivity > 1e-9 -> ImpactDirection.POSITIVE
        signedSensitivity != null && signedSensitivity < -1e-9 -> ImpactDirection.NEGATIVE
        signedSensitivity != null -> ImpactDirection.NEUTRAL
        mostSpecific.any { it.direction == ImpactDirection.MIXED } -> ImpactDirection.MIXED
        else -> ImpactDirection.NEUTRAL
    }
    return ResolvedEventImpact(
        direction = resolvedDirection,
        relativeSensitivity = signedSensitivity?.let { kotlin.math.abs(it) }
            ?: mostSpecific.map(EventImpactInsight::relativeSensitivity).average(),
        insights = mostSpecific,
        source = EventImpactResolutionSource.EXPLICIT_PATH,
    )
}

/** 인버스 상품의 시장·섹터 노출은 기초자산 뉴스 방향과 반대로 표시한다. */
fun GameEvent.directionFor(stock: StockDefinition): ImpactDirection {
    val resolvedDirection = resolvedImpactFor(stock).direction
    val isDirectProductEvent = isDirectProductImpactFor(stock)
    val shouldInvert = stock.etfProfile?.leverage?.let { it < 0.0 } == true && !isDirectProductEvent
    if (!shouldInvert) return resolvedDirection
    return when (resolvedDirection) {
        ImpactDirection.POSITIVE -> ImpactDirection.NEGATIVE
        ImpactDirection.NEGATIVE -> ImpactDirection.POSITIVE
        ImpactDirection.MIXED -> ImpactDirection.MIXED
        ImpactDirection.NEUTRAL -> ImpactDirection.NEUTRAL
    }
}

/** 종목 고유 사건과 ETF 상장 미시구조 충격은 기초자산 배율과 분리해 한 번만 반영한다. */
internal fun GameEvent.isDirectProductImpactFor(stock: StockDefinition): Boolean {
    val isExplicitProductEvent = (
        impactCoveragePolicy == EventImpactCoveragePolicy.SCOPE_FALLBACK_WITH_OVERRIDES &&
            scope == EventScope.STOCK && stock.id in affectedStockIds
    ) ||
        impactInsights.any { insight ->
            insight.targetKind == EventImpactTargetKind.STOCK && insight.stockId == stock.id
        }
    if (isExplicitProductEvent) return true
    if (!stock.isFundLike || scope != EventScope.MARKET) return false

    val coverage = impactCoverageFor(stock)
    val isStructuredListingDislocation = causalSignals.any { signal ->
        signal.transmissionProfile == CausalTransmissionProfile.LOCAL_MICROSTRUCTURE
    } && coverage.causalImpact != null
    val isUnseededListingFallback = causalSignals.isEmpty() &&
        stock.market in affectedMarkets && coverage.usesScopeFallback
    return isStructuredListingDislocation || isUnseededListingFallback
}
