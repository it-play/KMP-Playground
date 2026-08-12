package com.amond.kmpbook.domain.model.fund

/** Target-weight construction after eligible underlying funds have been selected. */
enum class FundOfFundsWeightingModel {
    EQUAL_WEIGHT,
    SCORE_WEIGHTED,
    DISTRIBUTION_WEIGHTED,
    MODIFIED_NET_ASSET_VALUE,
}
