package com.amond.kmpbook.domain.simulation.causal

import com.amond.kmpbook.domain.model.market.Market

internal data class DirectExposure(
    val market: Market,
    val reach: Double,
    val preferOverSpatial: Boolean,
) {
    fun asReach(): TransmissionReach = TransmissionReach(
        reach = reach,
        path = listOf(market),
        dominantPathContribution = reach,
        directExposure = true,
    )
}
