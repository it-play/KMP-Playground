package com.amond.kmpbook.domain.model.instrument

/** 현금분배 빈도를 실제 기준일로 투영하는 상품별 달력이다. */
enum class DistributionCalendar {
    /** 월중 15일을 기준으로 쓰는 기존 동결 시나리오 달력. */
    FIXED_DAY_15,

    /** 해당 빈도의 대상 월 마지막 KRX 영업일을 기준일로 쓴다. */
    KRX_MONTH_END,

    /** Vanguard가 공표한 VOO 기준일과 그 이후의 동결 시나리오 투영 규칙을 쓴다. */
    VANGUARD_VOO_EX_DATE,

    /** Vanguard가 공표한 VTV 기준일과 그 이후의 동결 시나리오 투영 규칙을 쓴다. */
    VANGUARD_VTV_EX_DATE,

    /** Schwab가 공표한 연간 equity ETF 일정과 이후 동결 시나리오 투영을 쓴다. */
    SCHWAB_EQUITY_ETF_EX_DATE,

    /** 대상 월 15일이 KRX 비영업일이면 직전 KRX 영업일을 지급기준일로 쓴다. */
    KRX_PRECEDING_BUSINESS_DAY_15,

    /** 매년 11월 마지막 KRX 영업일을 지급기준일로 쓴다. */
    KRX_ANNUAL_NOVEMBER_MONTH_END,
}
