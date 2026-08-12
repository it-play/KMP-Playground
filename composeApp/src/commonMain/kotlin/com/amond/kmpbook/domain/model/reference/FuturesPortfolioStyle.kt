package com.amond.kmpbook.domain.model.reference

/** Portfolio construction is explicit; a dynamic basket is not disguised as a front-month future. */
enum class FuturesPortfolioStyle {
    SINGLE_COMMODITY,
    STATIC_COMMODITY_BASKET,
    EXTERNAL_DYNAMIC_COMMODITY_BASKET,
    CRYPTO_FUTURES,
    EXTERNAL_REAL_ASSET_BASKET,
}
