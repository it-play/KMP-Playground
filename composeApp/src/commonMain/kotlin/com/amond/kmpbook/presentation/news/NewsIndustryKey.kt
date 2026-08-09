package com.amond.kmpbook.presentation.news

import com.amond.kmpbook.domain.model.market.IndustrySegment
import com.amond.kmpbook.domain.model.market.Sector

internal data class NewsIndustryKey(
    val sector: Sector,
    val segment: IndustrySegment?,
) {
    val key: String
        get() = segment?.let { "segment:${it.name}" } ?: "sector:${sector.name}"
}
