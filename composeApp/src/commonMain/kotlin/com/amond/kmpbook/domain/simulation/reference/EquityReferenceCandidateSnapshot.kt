package com.amond.kmpbook.domain.simulation.reference

import com.amond.kmpbook.domain.model.fund.EquityReferenceRegion
import com.amond.kmpbook.domain.model.fund.MethodologyEquitySector

/** One annual, keyed fundamental snapshot shared by every equity benchmark. */
internal data class EquityReferenceCandidateSnapshot(
    val assetId: String,
    val region: EquityReferenceRegion,
    val countryCode: String,
    val sector: MethodologyEquitySector,
    val marketCap: Double,
    val floatMarketCap: Double,
    val price: Double,
    val revenue: Double,
    val dividendYield: Double,
    val value: Double,
    val growth: Double,
    val quality: Double,
    val momentum: Double,
    val beta: Double,
    val annualVolatility: Double,
    val esg: Double,
    val liquidity: Double,
) {
    init {
        require(marketCap.isFinite() && marketCap > 0.0)
        require(floatMarketCap.isFinite() && floatMarketCap in 0.0..marketCap)
        require(price.isFinite() && price > 0.0)
        require(revenue.isFinite() && revenue > 0.0)
        require(dividendYield.isFinite() && dividendYield in 0.0..1.0)
        require(listOf(value, growth, quality, momentum, esg).all { it.isFinite() && it in -1.0..1.0 })
        require(beta.isFinite() && beta in 0.0..3.0)
        require(annualVolatility.isFinite() && annualVolatility in 0.0..5.0)
        require(liquidity.isFinite() && liquidity > 0.0)
    }
}
