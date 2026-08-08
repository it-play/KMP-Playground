package com.amond.kmpbook.domain.model

/** 뉴스가 플레이어의 보유·관심 자산에 어떤 경로로 연결됐는지를 표시한다. */
data class NewsRelevance(
    val heldStockIds: Set<String> = emptySet(),
    val watchedStockIds: Set<String> = emptySet(),
    val relatedSectors: Set<Sector> = emptySet(),
) {
    val isPersonal: Boolean get() = heldStockIds.isNotEmpty() || watchedStockIds.isNotEmpty()
    val isHoldingRelated: Boolean get() = heldStockIds.isNotEmpty()
    val isWatchlistRelated: Boolean get() = watchedStockIds.isNotEmpty()
    val isSectorRelated: Boolean get() = relatedSectors.isNotEmpty()
}

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

/** 한 종목에 적용되는 분석 경로 중 가장 구체적인 단계만 가격 엔진과 UI가 공유한다. */
data class ResolvedEventImpact(
    val direction: ImpactDirection,
    val relativeSensitivity: Double,
    val insights: List<EventImpactInsight>,
    val source: EventImpactResolutionSource,
    val causalImpact: CausalStockImpact? = null,
)

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

/** 종목 자체의 운용·발행사 뉴스는 기초자산 배율과 분리해 한 번만 반영한다. */
internal fun GameEvent.isDirectProductImpactFor(stock: StockDefinition): Boolean =
    (
        impactCoveragePolicy == EventImpactCoveragePolicy.SCOPE_FALLBACK_WITH_OVERRIDES &&
            scope == EventScope.STOCK && stock.id in affectedStockIds
    ) ||
        impactInsights.any { insight ->
            insight.targetKind == EventImpactTargetKind.STOCK && insight.stockId == stock.id
        }
