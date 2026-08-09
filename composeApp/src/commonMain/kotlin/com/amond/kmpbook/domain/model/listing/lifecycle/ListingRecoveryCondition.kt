package com.amond.kmpbook.domain.model.listing.lifecycle

import com.amond.kmpbook.domain.model.listing.lifecycle.ListingRecoveryCondition

/** 정량 가격만으로 확인할 수 없는 개선 사실을 게임 이벤트가 명시한다. */
enum class ListingRecoveryCondition {
    BID_PRICE_RESTORED,
    MARKET_CAPITALIZATION_RESTORED,
    LIQUIDITY_RESTORED,
    FINANCIAL_DEFICIENCY_RESOLVED,
    AUDIT_OR_DISCLOSURE_CURED,
    REGULATORY_CLEARANCE,
    BUSINESS_RESUMED,
    ISSUER_ELIGIBILITY_RESTORED,
    UNDERLYING_INDEX_RESTORED,
    LIQUIDITY_PROVIDER_REPLACED,
}
