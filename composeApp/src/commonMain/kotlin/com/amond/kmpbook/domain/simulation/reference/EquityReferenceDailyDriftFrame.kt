package com.amond.kmpbook.domain.simulation.reference

import com.amond.kmpbook.domain.model.fund.MethodologyEquitySector

/** One shared close-to-close cross-sectional factor draw reused by every benchmark. */
internal data class EquityReferenceDailyDriftFrame(
    val countryLogReturns: Map<String, Double>,
    val sectorLogReturns: Map<MethodologyEquitySector, Double>,
    val valueStandardNormal: Double,
    val growthStandardNormal: Double,
    val qualityStandardNormal: Double,
    val assetIdiosyncraticStandardNormals: Map<String, Double>,
)
