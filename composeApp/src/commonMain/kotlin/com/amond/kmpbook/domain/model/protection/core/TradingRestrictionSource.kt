package com.amond.kmpbook.domain.model.protection.core

import com.amond.kmpbook.domain.model.protection.core.TradingRestrictionSource

enum class TradingRestrictionSource {
    KRX_MARKET_CIRCUIT_BREAKER,
    US_MARKET_WIDE_CIRCUIT_BREAKER,
    INSTRUMENT_TRADING_HALT,
    KRX_VOLATILITY_INTERRUPTION,
    US_LIMIT_UP_LIMIT_DOWN,
    KRX_SIDECAR,
}
