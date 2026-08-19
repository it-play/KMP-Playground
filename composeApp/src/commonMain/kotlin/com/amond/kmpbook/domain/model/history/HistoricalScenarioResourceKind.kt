package com.amond.kmpbook.domain.model.history

/** 번들 역사 시나리오를 독립적으로 교체·검증할 수 있는 콘텐츠 조각의 종류다. */
enum class HistoricalScenarioResourceKind {
    SOURCES,
    DAILY_BARS,
    EVENTS,
    CORPORATE_ACTIONS,
}
