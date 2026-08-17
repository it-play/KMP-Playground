package com.amond.kmpbook.domain.model.fund

/** 액티브 운용으로 발생할 수 있는 벤치마크 대비 수익을 엔진이 다루는 수준이다. */
enum class ActiveReturnModelSupport {
    NOT_APPLICABLE,

    /** 공식 숫자 근거가 없는 alpha를 합성하지 않고 벤치마크 수익만 실행한다. */
    UNMODELED,

    /** 카탈로그에 명시한 버전 고정 가정으로 alpha·스왑 funding·신용 오버레이를 실행한다. */
    DETERMINISTIC_ASSUMPTION,
}
