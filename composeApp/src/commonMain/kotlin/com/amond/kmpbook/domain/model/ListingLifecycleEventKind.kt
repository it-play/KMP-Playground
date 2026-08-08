package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate

/** 뉴스와 종목 상세 화면이 공통으로 소비하는 상장 상태 원장 이벤트. */
enum class ListingLifecycleEventKind {
    DEFICIENCY_DESIGNATED,
    DEFICIENCY_REDESIGNATED,
    REVIEW_STARTED,
    TRADING_SUSPENDED,
    DEFICIENCY_CURED,
    TRADING_RESUMED,
    DELISTING_SCHEDULED,
    LIQUIDATION_STARTED,
    DELISTED,
    TERMINATED,
}
