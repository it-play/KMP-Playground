package com.amond.kmpbook.domain.simulation.event


enum class EventCondition {
    ALWAYS,
    POLICY_RATE_HIGH,
    POLICY_RATE_LOW,
    POLICY_RATE_RISING,
    POLICY_RATE_FALLING,
    INFLATION_HIGH,
    INFLATION_COOLING,
    GROWTH_NEGATIVE,
    GROWTH_STRONG,
    KRW_WEAK,
    KRW_STRONG,
    RISK_OFF,
    RISK_ON,
    HIGH_VOLATILITY,
    MARKET_DRAWDOWN,
    MARKET_RALLY,
}
