package com.amond.kmpbook.domain.model.fund

/** 상품이 궁극적으로 노출되는 경제적 자산 축이다. */
enum class FundReferenceExposure {
    EQUITY,
    FIXED_INCOME,
    CASH,
    COMMODITY,
    CRYPTO,
    MULTI_ASSET,
    REAL_ESTATE,
    ALTERNATIVE,
}
