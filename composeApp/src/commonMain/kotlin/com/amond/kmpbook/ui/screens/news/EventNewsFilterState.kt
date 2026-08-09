package com.amond.kmpbook.ui.screens.news

internal data class EventNewsFilterState(
    val tab: NewsBrowseTab = NewsBrowseTab.BRIEFING,
    val groupKey: String? = null,
    val selectedEventId: String? = null,
)
