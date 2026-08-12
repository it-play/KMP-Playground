package com.amond.kmpbook.domain.model.fund

/** 벤치마크 수익률을 게임 안에서 만드는 실행 경로다. */
enum class BenchmarkEngineKind {
    /** 공식 구성·선정 규칙으로 기준 포트폴리오를 직접 운용한다. */
    EQUITY_METHODOLOGY,

    /** 전체 holdings 규칙 전에는 지역·유니버스·스타일 정책으로 주식 기준수익률을 만든다. */
    EQUITY_REFERENCE,

    /** 듀레이션·금리곡선·신용스프레드로 고정수익 기준수익률을 만든다. */
    FIXED_INCOME_CURVE,

    /** 현물 가격과 명시적 보관·담보 carry로 원자재 기준수익률을 만든다. */
    COMMODITY_SPOT,

    /** 만기곡선·롤·담보수익으로 선물 기준수익률을 만든다. */
    FUTURES_CURVE,

    /** 상장 펀드 후보군을 선정·재조정해 펀드오브펀드 기준 포트폴리오를 운용한다. */
    FUND_OF_FUNDS_METHODOLOGY,

    /** 서명된 다중 기준·종목 sleeve를 목표, band, risk 또는 duration 규칙으로 운용한다. */
    COMPOSITE_REFERENCE,

    /** 명시적 long/short driver와 비용·신호 calibration으로 대안 위험프리미엄을 만든다. */
    ALTERNATIVE_RISK_PREMIA,

    /** 상세 규칙을 구현하기 전까지 기존 시장 요인 기반 수익률을 사용한다. */
    COARSE_FACTOR_PROXY,
}
