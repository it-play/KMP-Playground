package com.amond.kmpbook.domain.model.fund

/** 합성 ETF가 사용하는 스왑의 담보·자금 제공 구조다. */
enum class SyntheticSwapFunding {
    FULLY_FUNDED,
    UNFUNDED,
    /** 상품 설명서가 자금공여형 또는 비자금공여형 스왑을 선택해 쓸 수 있게 허용한다. */
    FLEXIBLE_FUNDED_OR_UNFUNDED,
}
