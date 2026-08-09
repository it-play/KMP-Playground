package com.amond.kmpbook.domain.simulation.causal

import com.amond.kmpbook.domain.model.market.Market

internal data class TransmissionReach(
    val reach: Double,
    val path: List<Market>,
    val dominantPathContribution: Double,
    val directExposure: Boolean,
)
