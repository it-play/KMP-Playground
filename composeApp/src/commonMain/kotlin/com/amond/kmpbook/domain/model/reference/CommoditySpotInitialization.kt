package com.amond.kmpbook.domain.model.reference

/** Bootstrap inputs for a shared spot reference. */
data class CommoditySpotInitialization(
    val terms: CommoditySpotReferenceTerms,
    val spotLevel: Double,
    val referenceLevel: Double,
    val cashRateAnnual: Double,
) {
    init {
        require(spotLevel.isFinite() && spotLevel > 0.0)
        require(referenceLevel.isFinite() && referenceLevel > 0.0)
        require(cashRateAnnual.isFinite() && cashRateAnnual in -0.10..1.0)
    }
}
