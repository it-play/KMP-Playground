package com.amond.kmpbook.domain.model.instrument

import kotlinx.datetime.LocalDate

/** 운용사가 확정한 날짜와, 확정된 경우의 좌당 분배금 또는 지급 생략 결정이다. */
data class DistributionAnnouncement(
    val exDate: LocalDate,
    val recordDate: LocalDate,
    val payDate: LocalDate,
    /** null이면 카탈로그·상품·ex-date만으로 재생되는 비공식 manager-decision projection을 사용한다. */
    val declaredGrossPerUnit: Double?,
    /** 운용사가 무분배를 결정한 경우 true다. */
    val skip: Boolean,
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
