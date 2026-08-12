package com.amond.kmpbook.domain.simulation.reference

import com.amond.kmpbook.domain.model.fund.EquityReferenceRegion
import com.amond.kmpbook.domain.model.fund.MethodologyEquitySector

/** Non-tradable stable anchor from which annual reference snapshots are derived. */
internal data class EquityReferenceCandidate(
    val assetId: String,
    val region: EquityReferenceRegion,
    val countryCode: String,
    val sector: MethodologyEquitySector,
    val baseMarketCap: Double,
    val baseFloatFactor: Double,
    val basePrice: Double,
    val baseRevenueToMarketCap: Double,
    val baseDividendYield: Double,
    val baseValue: Double,
    val baseGrowth: Double,
    val baseQuality: Double,
    val baseMomentum: Double,
    val baseBeta: Double,
    val baseAnnualVolatility: Double,
    val baseEsg: Double,
    val baseLiquidity: Double,
)
