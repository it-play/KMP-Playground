package com.amond.kmpbook.domain.simulation.protection


enum class TradingStabilizer {
    NONE,
    MARKET_CLOSED,
    KRX_UPPER_LIMIT,
    KRX_LOWER_LIMIT,
    US_LEVEL_1_REOPENED,
    US_LEVEL_2_REOPENED,
    US_LEVEL_3_HALTED,
    US_VOLATILITY_PAUSE,
}
