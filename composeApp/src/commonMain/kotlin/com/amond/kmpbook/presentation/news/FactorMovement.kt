package com.amond.kmpbook.presentation.news

/** 서술기 밖으로 노출되지 않는 두 형태의 문장 조각을 같은 렌더링 규칙 옆에 둔다. */
internal data class FactorMovement(
    val connective: String,
    val final: String,
)
