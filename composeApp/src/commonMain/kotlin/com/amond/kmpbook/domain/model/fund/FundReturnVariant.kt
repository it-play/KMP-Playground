package com.amond.kmpbook.domain.model.fund

/** 상품이 참조하는 지수 수익률의 배당 처리 변형이다. */
enum class FundReturnVariant {
    PRICE_RETURN,
    TOTAL_RETURN,
    NET_TOTAL_RETURN,
    UNVERIFIED,
}
