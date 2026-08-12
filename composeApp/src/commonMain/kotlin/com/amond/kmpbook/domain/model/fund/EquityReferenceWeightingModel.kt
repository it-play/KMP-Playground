package com.amond.kmpbook.domain.model.fund

/** Reference-index weighting rule when it can be identified without guessing exact holdings. */
enum class EquityReferenceWeightingModel {
    FLOAT_ADJUSTED_MARKET_CAP,
    MARKET_CAP,
    MODIFIED_MARKET_CAP,
    EQUAL_WEIGHT,
    PRICE_WEIGHTED,
    FUNDAMENTAL,
    REVENUE_WEIGHTED,
    DIVIDEND_WEIGHTED,
    FACTOR_SCORE,
    ACTIVE_DISCRETIONARY,
    UNVERIFIED,
}
