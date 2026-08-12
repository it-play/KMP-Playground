package com.amond.kmpbook.domain.model.fund

/** 상장 상품이 기준 노출을 구현하는 운용 방식이다. */
enum class FundReplicationMode {
    PHYSICAL_FULL_REPLICATION,
    PHYSICAL_SAMPLING,
    DERIVATIVE_SYNTHETIC,
    HYBRID,
    ACTIVE_MANAGEMENT,
    SYNTHETIC_NOTE,
    /** 공식 상품 자료로 복제 방식을 확인하기 전의 보수적 상태다. */
    UNVERIFIED,
}
