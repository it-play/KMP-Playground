package com.amond.kmpbook.domain.model

/** 어떤 우선순위 단계에서 종목 영향을 확정했는지 표시한다. */
enum class EventImpactResolutionSource {
    EXPLICIT_PATH,
    CAUSAL_GRAPH,
    SCOPE_FALLBACK,
    NONE,
}
