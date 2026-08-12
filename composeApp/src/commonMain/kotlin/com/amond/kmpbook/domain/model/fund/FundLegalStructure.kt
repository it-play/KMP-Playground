package com.amond.kmpbook.domain.model.fund

/** 같은 거래소 상장 상품이라도 현금흐름과 법적 책임이 다른 구조를 분리한다. */
enum class FundLegalStructure {
    OPEN_END_ETF,
    EXCHANGE_TRADED_NOTE,
    CLOSED_END_FUND,
}
