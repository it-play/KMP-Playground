package com.amond.kmpbook.domain.model.reference

/** Spot-price and carry decomposition; both components sum exactly to the gross log return. */
data class CommoditySpotReferenceAdvance(
    val state: CommoditySpotReferenceState,
    val grossReferenceLogReturn: Double,
    val priceOnlyLogReturn: Double,
    val netCarryLogReturn: Double,
) {
    init {
        require(grossReferenceLogReturn.isFinite())
        require(priceOnlyLogReturn.isFinite())
        require(netCarryLogReturn.isFinite())
        require(kotlin.math.abs(grossReferenceLogReturn - priceOnlyLogReturn - netCarryLogReturn) <= 1e-10)
    }
}
