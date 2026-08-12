package com.amond.kmpbook.domain.model.reference

/** Source frame and cash rate for one alternative-risk-premia interval. */
data class AlternativeRiskPremiaAdvanceInput(
    val sourceFrame: ReferenceSourceReturnFrame,
    val annualRiskFreeRate: Double,
) {
    init {
        require(annualRiskFreeRate.isFinite() && annualRiskFreeRate in -0.25..1.0)
    }
}
