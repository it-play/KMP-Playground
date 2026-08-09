package com.amond.kmpbook.domain.tax.core

enum class TaxCategory(val displayName: String) {
    SECURITIES_TRANSACTION("증권거래세"),
    SPECIAL_RURAL("농어촌특별세"),
    CAPITAL_GAINS("양도소득세"),
    LOCAL_INCOME("지방소득세"),
    DIVIDEND_WITHHOLDING("배당 원천징수"),
    HIGH_DIVIDEND_SEPARATE("고배당 분리과세"),
}
