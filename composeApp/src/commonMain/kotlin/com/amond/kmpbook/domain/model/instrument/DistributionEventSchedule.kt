package com.amond.kmpbook.domain.model.instrument

import kotlinx.datetime.LocalDate

/** 한 분배 결정의 분배락·권리확정·현금지급 날짜와 금액 결정 입력이다. */
data class DistributionEventSchedule(
    val exDate: LocalDate,
    val recordDate: LocalDate,
    val payDate: LocalDate,
    val declaredGrossPerUnit: Double?,
    val skip: Boolean,
    val isAnnounced: Boolean,
) {
    init {
        require(exDate <= recordDate && recordDate <= payDate)
        require(
            declaredGrossPerUnit == null ||
                declaredGrossPerUnit.isFinite() &&
                declaredGrossPerUnit in 0.0..MAX_FUND_REFERENCE_VALUE &&
                declaredGrossPerUnit != 0.0,
        )
        require(!skip || declaredGrossPerUnit == null)
    }
}
