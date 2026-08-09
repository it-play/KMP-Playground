package com.amond.kmpbook.domain.model

/** 표시 문구와 무관하게 정기 발표 수치를 도메인 상태에 연결하는 안정적인 종류다. */
enum class ScheduledEventMetricKind {
    GENERIC,
    EARNINGS_DILUTED_EPS,
    EARNINGS_REVENUE,
}
