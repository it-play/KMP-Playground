package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate

/** 상장 유지 조치의 직접 원인. */
enum class ListingLifecycleReason(val displayName: String) {
    KRX_LISTING_MAINTENANCE("KRX 상장 유지 요건"),
    KRX_ADMINISTRATIVE_ISSUE("KRX 관리종목 사유"),
    US_LISTING_MAINTENANCE("미국 거래소 상장 유지 요건"),
    US_MINIMUM_BID_PRICE("미국 최저 호가 요건"),
    US_MARKET_CAPITALIZATION("미국 시가총액 요건"),
    LOW_TRADING_LIQUIDITY("거래 유동성 부족"),
    AUDIT_OR_DISCLOSURE_FAILURE("감사·공시 의무 위반"),
    SERIOUS_COMPLIANCE_EVENT("중대한 규정 준수 사건"),
    BANKRUPTCY_OR_INSOLVENCY("파산·지급불능"),
    CORE_BUSINESS_SUSPENSION("주요 영업 정지"),
    ETF_VOLUNTARY_LIQUIDATION("ETF·펀드 자진 청산"),
    ETN_MATURITY_OR_EARLY_REDEMPTION("ETN 만기·조기상환"),
    ISSUER_ELIGIBILITY_FAILURE("발행사 자격·신용 문제"),
    UNDERLYING_INDEX_UNAVAILABLE("기초지수 산출 중단"),
    LIQUIDITY_PROVIDER_FAILURE("유동성공급자 부재"),
}
