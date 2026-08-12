package com.amond.kmpbook.domain.model.fund

/** 기준 포트폴리오의 대표 신용 품질 구간이다. */
enum class FixedIncomeCreditBucket {
    GOVERNMENT_BACKED,
    AAA,
    INVESTMENT_GRADE,
    HIGH_YIELD,
    MIXED,
    UNVERIFIED,
}
