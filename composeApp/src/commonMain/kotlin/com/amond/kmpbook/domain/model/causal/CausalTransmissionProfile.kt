package com.amond.kmpbook.domain.model.causal

/** 같은 경제 요인이라도 사건의 성격에 따라 공간적으로 이동하는 속도를 구분한다. */
enum class CausalTransmissionProfile(val displayName: String) {
    /** 거래소 호가 공백·기술적 주문 불균형. 같은 국가의 인접 시장에만 매우 약하게 번진다. */
    LOCAL_MICROSTRUCTURE("현지 미시구조"),
    /** 마진콜·위험 축소·글로벌 포트폴리오 재조정. */
    PORTFOLIO_DELEVERAGING("포트폴리오 디레버리징"),
    /** 달러·회사채·은행 조달 여건처럼 국가 간 금융 연결을 타는 신호. */
    FUNDING_STRESS("자금조달 경색"),
    /** 소비·투자·산업 수요처럼 무역과 실적을 통해 느리게 이동하는 신호. */
    GLOBAL_REAL_ECONOMY("글로벌 실물경제"),
    /** 원유·운임처럼 여러 시장이 같은 기준가격을 참조하는 신호. */
    GLOBAL_REFERENCE_PRICE("글로벌 기준가격"),
}
