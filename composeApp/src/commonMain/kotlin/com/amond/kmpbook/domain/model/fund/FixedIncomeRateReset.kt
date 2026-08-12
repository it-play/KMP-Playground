package com.amond.kmpbook.domain.model.fund

/** 쿠폰 또는 기준금리 노출을 다시 고정하는 대표 주기다. */
enum class FixedIncomeRateReset {
    NOT_FLOATING,
    OVERNIGHT,
    MONTHLY,
    QUARTERLY,
    VARIABLE,
}
