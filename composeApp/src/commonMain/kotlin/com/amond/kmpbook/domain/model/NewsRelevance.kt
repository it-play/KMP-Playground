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
