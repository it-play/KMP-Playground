package com.amond.kmpbook.domain.model.instrument

enum class InstrumentType(val displayName: String) {
    STOCK("주식"),
    ETF("ETF"),
    CLOSED_END_FUND("폐쇄형 펀드"),
    ETN("ETN"),
    REIT("REIT"),
    ADR("ADR"),
}
