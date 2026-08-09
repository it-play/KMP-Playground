package com.amond.kmpbook.ui.screens.news

import com.amond.kmpbook.domain.model.event.EventType
import com.amond.kmpbook.presentation.news.NewsStoryUi

internal enum class NewsBrowseTab(val displayName: String) {
    BRIEFING("브리핑"),
    STOCKS("종목"),
    INDUSTRIES("산업"),
    SCHEDULES("일정·공시"),
    ;

    fun matches(story: NewsStoryUi): Boolean = when (this) {
        BRIEFING -> true
        STOCKS -> story.relatedStocks.isNotEmpty()
        INDUSTRIES -> story.hasSectorTargets
        SCHEDULES -> story.isScheduled || story.isOperational || story.event.type == EventType.CORPORATE_ACTION
    }
}
