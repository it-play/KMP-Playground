package com.amond.kmpbook.domain.model.index


/** 대표 미국 주식시장 지수. 코드는 시세창에 노출할 관행 티커다. */
enum class MarketIndexId(val code: String, val displayName: String) {
    SP_500("SPX", "S&P 500"),
    NASDAQ_COMPOSITE("COMP", "Nasdaq Composite"),
    DOW_JONES_INDUSTRIAL_AVERAGE("DJIA", "Dow Jones Industrial Average"),
    VIX("VIX", "Cboe Volatility Index"),
}
