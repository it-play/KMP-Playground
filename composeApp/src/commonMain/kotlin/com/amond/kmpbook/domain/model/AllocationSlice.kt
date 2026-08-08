package com.amond.kmpbook.domain.model

import kotlin.time.Instant

/** 원형 차트, 섹터/시장 비중 차트 등에 공통으로 쓰는 한 조각. */
data class AllocationSlice(
    val key: String,
    val label: String,
    val valueKrw: Double,
    val weight: Double,
) {
    init {
        require(key.isNotBlank() && label.isNotBlank()) { "비중 항목의 키와 이름은 비어 있을 수 없습니다." }
        require(valueKrw >= 0.0) { "평가액은 음수일 수 없습니다." }
        require(weight in 0.0..1.0) { "비중은 0과 1 사이여야 합니다." }
    }
}
