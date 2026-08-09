package com.amond.kmpbook.domain.model.schedule

/** 실제로 발표·보도된 수치만 뉴스에 싣기 위한 표시용 사실 데이터다. */
data class ReportedFact(
    val label: String,
    val actual: String,
    val comparison: String? = null,
) {
    init {
        require(label.isNotBlank()) { "보도 수치 이름은 비어 있을 수 없습니다." }
        require(actual.isNotBlank()) { "보도 수치의 실제 값은 비어 있을 수 없습니다." }
        require(comparison == null || comparison.isNotBlank()) { "비교 값은 빈 문자열일 수 없습니다." }
    }
}
