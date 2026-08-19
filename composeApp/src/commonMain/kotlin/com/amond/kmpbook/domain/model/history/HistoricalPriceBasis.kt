package com.amond.kmpbook.domain.model.history

/** 일봉 가격 열이 기업행동을 어떻게 반영했는지 명시한다. */
enum class HistoricalPriceBasis {
    /** 거래 당일 실제 체결 가격. 기업행동은 별도 원장에서 적용한다. */
    RAW,

    /** 분할만 과거 가격에 소급 반영한 가격. */
    SPLIT_ADJUSTED,
}
