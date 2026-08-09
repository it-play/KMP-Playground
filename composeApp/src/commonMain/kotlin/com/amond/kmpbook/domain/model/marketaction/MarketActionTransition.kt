package com.amond.kmpbook.domain.model.marketaction


/** 제목이나 이벤트 ID를 해석하지 않고 조치의 발표·효력·해제 단계를 판별하기 위한 전이 정보다. */
enum class MarketActionTransition {
    HALT_SCHEDULED,
    HALT_STARTED,
    PROGRAM_FLOW_SUSPENDED,
    CALL_AUCTION_STARTED,
    CLOSING_AUCTION_STARTED,
    MARKET_CLOSED_FOR_DAY,
    DESIGNATION_NOTICE,
    DESIGNATED,
    RELEASE_ANNOUNCED,
    RELEASED,
    REOPENED,
    LIFECYCLE_CHANGED,
    REMEDIATION_RECORDED,
}
