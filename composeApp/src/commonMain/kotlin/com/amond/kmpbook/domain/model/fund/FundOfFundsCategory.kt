package com.amond.kmpbook.domain.model.fund

/** Economic sleeve of a simulated underlying fund, independent of its listed wrapper. */
enum class FundOfFundsCategory {
    TAXABLE_INVESTMENT_GRADE,
    MUNICIPAL_FIXED_INCOME,
    HIGH_YIELD_CREDIT,
    EQUITY_OPTION_INCOME,
    MULTI_ASSET_INCOME,
    SINGLE_SECURITY_OPTION_INCOME,
}
