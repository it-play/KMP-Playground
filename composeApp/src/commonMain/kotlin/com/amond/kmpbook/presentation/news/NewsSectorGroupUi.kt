package com.amond.kmpbook.presentation.news

import com.amond.kmpbook.domain.model.market.IndustrySegment
import com.amond.kmpbook.domain.model.market.Sector

data class NewsSectorGroupUi(
    val key: String,
    val sector: Sector,
    val industrySegment: IndustrySegment? = null,
    val label: String,
    val count: Int,
    val personalCount: Int,
)
