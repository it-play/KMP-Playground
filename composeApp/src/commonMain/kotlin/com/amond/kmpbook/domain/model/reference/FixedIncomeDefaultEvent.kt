package com.amond.kmpbook.domain.model.reference

/** 이번 구간에 확정된 원금 손실이다. 확률 추출은 이 계산 모델 바깥에서 수행한다. */
data class FixedIncomeDefaultEvent(
    val assetId: String,
    val defaultedPrincipalFraction: Double,
    val recoveryRate: Double,
) {
    init {
        require(assetId.isNotBlank())
        require(defaultedPrincipalFraction.isFinite() && defaultedPrincipalFraction in 0.0..1.0)
        require(recoveryRate.isFinite() && recoveryRate in 0.0..1.0)
    }

    val lossFraction: Double get() = defaultedPrincipalFraction * (1.0 - recoveryRate)
}
