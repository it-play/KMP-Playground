package com.amond.kmpbook.domain.model.reference

/** 채권 기준 포트폴리오 한 구간의 순수 계산 결과다. */
data class FixedIncomeReferenceAdvance(
    val state: FixedIncomeReferenceState,
    val grossReferenceLogReturn: Double,
    val annualIncomeYield: Double,
    val assetLogReturns: Map<String, Double>,
) {
    init {
        require(grossReferenceLogReturn.isFinite())
        require(annualIncomeYield.isFinite() && annualIncomeYield in 0.0..1.0)
        require(assetLogReturns.keys == state.positions.mapTo(linkedSetOf(), FixedIncomeReferencePosition::assetId))
        require(assetLogReturns.values.all(Double::isFinite))
    }
}
