package com.amond.kmpbook.domain.model

/** 한 종목에 적용되는 분석 경로 중 가장 구체적인 단계만 가격 엔진과 UI가 공유한다. */
data class ResolvedEventImpact(
    val direction: ImpactDirection,
    val relativeSensitivity: Double,
    val insights: List<EventImpactInsight>,
    val source: EventImpactResolutionSource,
    val causalImpact: CausalStockImpact? = null,
)
