package com.amond.kmpbook.presentation.news

import com.amond.kmpbook.domain.model.event.ImpactDirection
import com.amond.kmpbook.domain.model.market.IndustrySegment
import com.amond.kmpbook.domain.model.market.Sector

/** 예상 등락률 대신 대상, 방향, 근거와 분석 시간축만 전달하는 뉴스 영향 경로다. */
data class NewsImpactPathUi(
    val id: String,
    val label: String,
    val categoryLabel: String,
    val direction: ImpactDirection,
    val reason: String,
    val sector: Sector? = null,
    val industrySegment: IndustrySegment? = null,
    val stockId: String? = null,
    val held: Boolean = false,
    val watched: Boolean = false,
    val horizonLabel: String,
)
