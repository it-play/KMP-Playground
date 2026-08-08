package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate

/** 거래소 상장 유지 심사부터 최종 처분까지 저장되는 종목 상태. */
enum class ListingLifecycleStatus(val displayName: String) {
    LISTED("정상 상장"),
    DEFICIENCY_NOTICE("상장 유지 요건 미달"),
    UNDER_REVIEW("상장 적격성 심사"),
    TRADING_SUSPENDED("거래정지"),
    DELISTING_SCHEDULED("상장폐지 예정"),
    LIQUIDATION_PENDING("청산금 지급 대기"),
    DELISTED("상장폐지"),
    TERMINATED("상품 종료"),
}
