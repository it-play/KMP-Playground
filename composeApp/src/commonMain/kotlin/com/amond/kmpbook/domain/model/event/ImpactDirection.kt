package com.amond.kmpbook.domain.model.event

/**
 * 사건이 선택한 영향 경로의 가격 압력 방향이다. 시장·산업·거시·환율·개별 요인까지 합성한
 * 실현 수익률의 부호를 보장하지 않는다.
 */
enum class ImpactDirection(val displayName: String) {
    POSITIVE("호재"),
    NEGATIVE("악재"),
    MIXED("혼조"),
    NEUTRAL("중립"),
}
