package com.amond.kmpbook.presentation.news

/** 뉴스가 지금 사용자에게 어떤 상태로 보여야 하는지 나타내는 UI 전용 상태다. */
enum class NewsEffectState {
    UPCOMING,
    WAITING_FOR_MARKET,
    PROCESS_ACTIVE,
    MARKET_ACTIVE,
    MARKET_ENDED,
    RESTRICTION_ACTIVE,
    RESOLVED,
    INFORMATION,
}
