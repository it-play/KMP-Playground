package com.amond.kmpbook.ui.screens.news

import com.amond.kmpbook.presentation.news.NewsStoryUi

internal data class NewsGroupItem(
    val key: String,
    val label: String,
    val detail: String?,
    val count: Int,
    val matches: (NewsStoryUi) -> Boolean = { true },
)
