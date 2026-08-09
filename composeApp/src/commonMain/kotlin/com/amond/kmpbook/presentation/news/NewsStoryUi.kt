package com.amond.kmpbook.presentation.news

import com.amond.kmpbook.domain.model.event.GameEvent
import com.amond.kmpbook.domain.model.event.ImpactDirection
import com.amond.kmpbook.domain.model.news.NewsRelevance

data class NewsStoryUi(
    val event: GameEvent,
    val status: NewsEffectStatusUi,
    val marketEffectStatus: NewsEffectStatusUi?,
    val operationalStatus: NewsEffectStatusUi?,
    val relevance: NewsRelevance,
    val personalDirection: ImpactDirection,
    val impactPaths: List<NewsImpactPathUi>,
    val relatedStocks: List<NewsRelatedStockUi>,
    val instrumentTermination: NewsInstrumentTerminationUi?,
    val isUnread: Boolean,
    val isMarketAction: Boolean,
    val isScheduled: Boolean,
    val isOperational: Boolean,
) {
    val secondaryStatus: NewsEffectStatusUi?
        get() = listOfNotNull(operationalStatus, marketEffectStatus)
            .distinct()
            .firstOrNull { it != status }

    val activityPriority: Int
        get() = listOfNotNull(operationalStatus, marketEffectStatus)
            .maxOfOrNull { it.state.activityPriority }
            ?: status.state.activityPriority
}
