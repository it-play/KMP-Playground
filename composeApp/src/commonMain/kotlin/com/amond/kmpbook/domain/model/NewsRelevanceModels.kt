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
        addAll(affectedSectors)
        affectedStockIds.mapNotNull(byId::get).forEach { stock ->
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

/** 인버스 상품의 시장·섹터 노출은 기초자산 뉴스 방향과 반대로 표시한다. */
fun GameEvent.directionFor(stock: StockDefinition): ImpactDirection {
    val isDirectProductEvent = scope == EventScope.STOCK && stock.id in affectedStockIds
    val shouldInvert = stock.etfProfile?.leverage?.let { it < 0.0 } == true && !isDirectProductEvent
    if (!shouldInvert) return impact.direction
    return when (impact.direction) {
        ImpactDirection.POSITIVE -> ImpactDirection.NEGATIVE
        ImpactDirection.NEGATIVE -> ImpactDirection.POSITIVE
        ImpactDirection.MIXED -> ImpactDirection.MIXED
        ImpactDirection.NEUTRAL -> ImpactDirection.NEUTRAL
    }
}
