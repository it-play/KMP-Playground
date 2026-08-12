package com.amond.kmpbook.domain.model.reference

/** 같은 듀레이션만으로 합칠 수 없는 채권 현금흐름 구조다. */
enum class FixedIncomeInstrumentKind {
    CASH_EQUIVALENT,
    TREASURY,
    GOVERNMENT_AGENCY,
    MUNICIPAL,
    CORPORATE,
    INFLATION_LINKED,
    FLOATING_RATE,
    MORTGAGE_BACKED,
    SECURITIZED_CREDIT,
    PREFERRED,
    CLO_TRANCHE,
}
