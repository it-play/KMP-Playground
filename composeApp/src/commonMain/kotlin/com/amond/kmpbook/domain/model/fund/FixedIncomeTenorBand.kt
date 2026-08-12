package com.amond.kmpbook.domain.model.fund

/** 기초 채무의 대표 만기 구간이다. 유효 듀레이션과는 별도 축이다. */
enum class FixedIncomeTenorBand {
    OVERNIGHT,
    ULTRA_SHORT,
    SHORT,
    INTERMEDIATE,
    LONG,
    BROAD,
    VARIABLE,
}
