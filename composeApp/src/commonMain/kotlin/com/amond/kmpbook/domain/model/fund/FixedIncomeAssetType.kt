package com.amond.kmpbook.domain.model.fund

/** 고정수익 기준 노출의 이자·신용 구조다. */
enum class FixedIncomeAssetType {
    NOMINAL_GOVERNMENT,
    INFLATION_LINKED,
    AGENCY_MBS,
    SECURITIZED_CREDIT,
    MUNICIPAL,
    PREFERRED_HYBRID,
    INVESTMENT_GRADE,
    HIGH_YIELD,
    FLOATING_RATE,
    CLO,
    MONEY_MARKET,
    MULTI_SECTOR_CREDIT,
}
