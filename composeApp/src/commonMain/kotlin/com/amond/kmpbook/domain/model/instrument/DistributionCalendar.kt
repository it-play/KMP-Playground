package com.amond.kmpbook.domain.model.instrument

/** 현금분배 빈도를 실제 기준일로 투영하는 상품별 달력이다. */
enum class DistributionCalendar {
    /** 월중 15일을 기준으로 쓰는 기존 동결 시나리오 달력. */
    FIXED_DAY_15,

    /** 해당 빈도의 대상 월 마지막 KRX 영업일을 기준일로 쓴다. */
    KRX_MONTH_END,
}
