package com.amond.kmpbook.domain.model.fundstructure

/** 법적 상품 조건이 어디까지 1차 문서로 확인됐는지 명시한다. */
enum class FundStructureTermsProvenance {
    VERIFIED_PRODUCT_TERMS,
    PARTIALLY_VERIFIED,
    MODEL_ASSUMPTION,
}
