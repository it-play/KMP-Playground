package com.amond.kmpbook.domain.model.reference

/** Exogenous rates plus already-generated source returns for one pure composite advance. */
data class CompositeReferenceAdvanceInput(
    val sourceFrame: ReferenceSourceReturnFrame,
    val annualRiskFreeRate: Double,
    val mortgageRateAnnual: Double,
) {
    init {
        require(annualRiskFreeRate.isFinite() && annualRiskFreeRate in -0.25..1.0)
        require(mortgageRateAnnual.isFinite() && mortgageRateAnnual in 0.0..1.0)
    }
}
