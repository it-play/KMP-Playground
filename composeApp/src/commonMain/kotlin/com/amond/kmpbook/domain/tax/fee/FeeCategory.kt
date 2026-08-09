package com.amond.kmpbook.domain.tax.fee

enum class FeeCategory(val displayName: String) {
    BROKER_COMMISSION("매매수수료"),
    FX_SPREAD("환전 스프레드"),
    SEC_SECTION_31("SEC Section 31 fee"),
    FINRA_TAF("FINRA Trading Activity Fee"),
}
