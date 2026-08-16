package com.amond.kmpbook.domain.model.fund

/** 출처와 게임 구현이 보장하는 벤치마크 정확도의 상한이다. */
enum class BenchmarkSupportLevel {
    /** 공식 방법론 경계를 검증했고, 관리자 재량 대리 모델은 별도 표기하며 엔진이 실행한다. */
    VERIFIED_RULES,

    /** 공식 기준지수 연결은 검증했지만 상세 규칙 엔진은 아직 없다. */
    VERIFIED_REFERENCE,

    /** 상품 메타데이터로 임시 분리한 프록시이며 실제 지수라고 단정하지 않는다. */
    PROVISIONAL_PROXY,
}
