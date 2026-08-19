package com.amond.kmpbook.domain.model.history

/** 역사 사건을 가격 기준 경로와 합성할 때 이중 반영을 막는 정책이다. */
enum class HistoricalPriceEffectPolicy {
    /** 일봉이 있는 종목에서는 관측 가격에 이미 반영됐고, 미관측 종목만 인과 모델로 보완한다. */
    EMBEDDED_WHERE_ANCHORED,

    /** 뉴스·사실 기록만 제공하며 가격 충격을 만들지 않는다. */
    INFORMATION_ONLY,
}
