package com.amond.kmpbook.domain.model.instrument

/** 공표되지 않은 미래 분배를 투영하는 상품별 지급 지연과 공표 이력이다. */
data class DistributionPolicy(
    /** 공식 날짜가 없는 미래 건의 record/reference date 이후 시장 영업일 수다. */
    val projectedPaymentLagBusinessDays: Int,
    /** 저장·카탈로그에서 미래 날짜와 금액이 가정임을 명시하는 버전된 설명 ID다. */
    val projectionAssumption: String,
    /** 미공표 좌당 금액의 장기 명목 성장 가정이다. 공식 공시나 수익률 예측이 아니다. */
    val projectedAnnualNominalGrowthRate: Double,
    /** assumption ID·상품·ex-date hash로 적용하는 좌우 대칭 bounded 변동 폭이다. */
    val projectedAmountVariationRate: Double,
    val announcedDistributions: List<DistributionAnnouncement>,
) {
    init {
        require(projectedPaymentLagBusinessDays in 0..10)
        require(projectionAssumption.isNotBlank() && projectionAssumption.length <= 120)
        require(projectedAnnualNominalGrowthRate.isFinite() && projectedAnnualNominalGrowthRate in -0.20..0.20)
        require(projectedAmountVariationRate.isFinite() && projectedAmountVariationRate in 0.0..0.50)
        require(announcedDistributions.size <= 128)
        require(announcedDistributions.zipWithNext().all { (before, after) -> before.exDate < after.exDate })
    }

    companion object {
        val DEFAULT: DistributionPolicy = DistributionPolicy(
            projectedPaymentLagBusinessDays = 0,
            projectionAssumption = "legacy-same-day-v1",
            projectedAnnualNominalGrowthRate = 0.0,
            projectedAmountVariationRate = 0.0,
            announcedDistributions = emptyList(),
        )
    }
}
