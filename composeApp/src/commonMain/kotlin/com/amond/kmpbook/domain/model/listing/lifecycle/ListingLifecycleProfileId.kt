package com.amond.kmpbook.domain.model.listing.lifecycle

import com.amond.kmpbook.domain.model.listing.lifecycle.ListingLifecycleProfileId

/** 종목·시장별 정책팩 ID. 저장 파일에는 정책 객체 대신 이 안정적인 ID를 기록한다. */
enum class ListingLifecycleProfileId {
    KRX_EQUITY_GAME_APPROXIMATION,
    KRX_ETF_GAME_APPROXIMATION,
    KRX_ETN_GAME_APPROXIMATION,
    NASDAQ_EQUITY_PUBLIC_RULE_WITH_GAME_APPROXIMATION,
    US_EQUITY_GAME_APPROXIMATION,
    US_FUND_GAME_APPROXIMATION,
    US_ETN_GAME_APPROXIMATION,
}
