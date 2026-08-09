package com.amond.kmpbook.domain.model.event


/**
 * 분석 경로가 없는 종목에 스코프의 기본 충격을 적용할지를 결정한다.
 *
 * [SCOPE_FALLBACK_WITH_OVERRIDES]는 기본 스코프를 보존하고 더 구체적인 분석 경로로 방향과
 * 민감도를 덮어쓴다. [EXPLICIT_PATHS_ONLY]는 명시적으로 일치하는 분석 경로만 영향받는다.
 */
enum class EventImpactCoveragePolicy {
    SCOPE_FALLBACK_WITH_OVERRIDES,
    EXPLICIT_PATHS_ONLY,
}
