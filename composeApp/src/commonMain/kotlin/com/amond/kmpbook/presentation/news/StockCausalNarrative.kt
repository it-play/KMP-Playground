package com.amond.kmpbook.presentation.news

import com.amond.kmpbook.domain.model.causal.CausalImpactTrace

/** 실제 인과 trace와 함께 생성된 종목별 설명이다. 합성 경로나 제목 기반 추론은 만들지 않는다. */
internal data class StockCausalNarrative(
    val trace: CausalImpactTrace,
    val text: String,
    val productDirectionInverted: Boolean,
)
