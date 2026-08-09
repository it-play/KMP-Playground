package com.amond.kmpbook.domain.model.schedule

import com.amond.kmpbook.domain.model.event.EventType

enum class ScheduledEventKind(
    val displayName: String,
    val eventType: EventType,
) {
    US_EMPLOYMENT("미국 고용보고서", EventType.ECONOMIC_INDICATOR),
    US_CPI("미국 소비자물가", EventType.ECONOMIC_INDICATOR),
    US_PCE("미국 PCE 물가", EventType.ECONOMIC_INDICATOR),
    US_GDP("미국 GDP", EventType.ECONOMIC_INDICATOR),
    US_FOMC("미국 FOMC", EventType.CENTRAL_BANK),
    US_RETAIL_SALES("미국 소매판매", EventType.ECONOMIC_INDICATOR),
    US_WEEKLY_CLAIMS("미국 주간 신규 실업수당 청구", EventType.ECONOMIC_INDICATOR),
    KR_CPI("한국 소비자물가", EventType.ECONOMIC_INDICATOR),
    KR_EMPLOYMENT("한국 고용동향", EventType.ECONOMIC_INDICATOR),
    KR_BOK("한국은행 금융통화위원회", EventType.CENTRAL_BANK),
    KR_GDP("한국 GDP", EventType.ECONOMIC_INDICATOR),
    EARNINGS("분기 실적", EventType.EARNINGS),
}
