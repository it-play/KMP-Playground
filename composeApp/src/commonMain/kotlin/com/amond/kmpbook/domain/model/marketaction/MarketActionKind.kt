package com.amond.kmpbook.domain.model.marketaction


/** 거래소·상장 조치 뉴스를 현재 원장 상태와 연결하는 구조화된 종류다. */
enum class MarketActionKind {
    KRX_CIRCUIT_BREAKER,
    KRX_SIDECAR,
    KRX_VOLATILITY_INTERRUPTION,
    US_MARKET_WIDE_CIRCUIT_BREAKER,
    US_LIMIT_UP_LIMIT_DOWN,
    INSTRUMENT_TRADING_HALT,
    INVESTMENT_ALERT,
    LISTING_LIFECYCLE,
    LISTING_REMEDIATION,
}
