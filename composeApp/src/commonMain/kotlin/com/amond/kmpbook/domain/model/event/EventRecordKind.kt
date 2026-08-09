package com.amond.kmpbook.domain.model.event


/**
 * 뉴스 항목이 생성된 업무 흐름을 ID 규칙과 분리해 저장한다.
 *
 * [EventType]이 내용의 주제라면 이 값은 예정 발표·거래소 조치·기업 행위 같은
 * 기록의 출처다. UI와 저장 검증은 이벤트 ID 접두사를 해석하지 않는다.
 */
enum class EventRecordKind(val displayName: String) {
    NEWS("뉴스"),
    SCHEDULED_RELEASE("예정 발표"),
    MARKET_ACTION("거래소 조치"),
    CORPORATE_ACTION("기업 행위"),
    INSTRUMENT_LIFECYCLE("상품 생애주기"),
}
