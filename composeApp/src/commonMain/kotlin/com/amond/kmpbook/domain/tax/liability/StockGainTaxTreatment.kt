package com.amond.kmpbook.domain.tax.liability


enum class StockGainTaxTreatment(val displayName: String) {
    DOMESTIC_EXEMPT_SMALL_ON_EXCHANGE("국내 장내 소액주주 비과세"),
    DOMESTIC_ETF_HOLDING_PERIOD_WITHHELD("국내상장 기타 ETF 보유기간 과세"),
    DOMESTIC_MAJOR_GENERAL("국내 대주주 일반세율"),
    DOMESTIC_MAJOR_NON_SME_SHORT_TERM("국내 비중소기업 대주주 1년 미만"),
    FOREIGN_STANDARD("국외주식 일반"),
}
