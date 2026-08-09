package com.amond.kmpbook.domain.model.index


/** 게임 지수가 실제 지수의 어떤 가중 원리를 대표하는지 명시한다. */
enum class MarketIndexFormulaKind(val displayName: String) {
    FLOAT_ADJUSTED_MARKET_CAP_PROXY("유동시가총액 가중 프록시"),
    TOTAL_MARKET_CAP_WEIGHTED("총시가총액 가중"),
    PRICE_WEIGHTED("주가 가중"),
    THIRTY_DAY_EXPECTED_VOLATILITY_PROXY("30일 기대변동성 프록시"),
}
