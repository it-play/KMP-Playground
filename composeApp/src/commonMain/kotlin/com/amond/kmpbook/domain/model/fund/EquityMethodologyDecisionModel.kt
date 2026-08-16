package com.amond.kmpbook.domain.model.fund

/** 공개 방법론의 종목선정 결정을 엔진이 재현할 수 있는 수준이다. */
enum class EquityMethodologyDecisionModel {
    /** 공개된 기계적 규칙만으로 같은 입력에서 같은 결정을 계산한다. */
    RULE_BASED,

    /** 공개 자격 규칙은 실행하지만 관리자 재량은 버전된 결정론적 대리 모델로 보완한다. */
    DISCRETIONARY_PROXY,
}
