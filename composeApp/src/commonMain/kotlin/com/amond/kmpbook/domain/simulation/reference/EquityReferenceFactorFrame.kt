package com.amond.kmpbook.domain.simulation.reference

import com.amond.kmpbook.domain.model.fund.MethodologyEquitySector
import com.amond.kmpbook.domain.model.reference.EquityReferenceFactorExposure
import com.amond.kmpbook.domain.model.reference.EquityReferenceStyleFactor

/** One shared vector of country, sector, style and residual innovations for an hourly batch. */
internal data class EquityReferenceFactorFrame(
    val countryLogReturns: Map<String, Double>,
    val countryTradingFractions: Map<String, Double>,
    val sectorLogReturns: Map<MethodologyEquitySector, Double>,
    val styleStandardNormals: Map<EquityReferenceStyleFactor, Double>,
    val idiosyncraticStandardNormals: List<Double>,
    val elapsedYearFraction: Double,
) {
    init {
        require(countryLogReturns.isNotEmpty())
        require(countryLogReturns.keys == countryTradingFractions.keys)
        require(countryLogReturns.values.all(Double::isFinite))
        require(countryTradingFractions.values.all { it.isFinite() && it in 0.0..1.0 })
        require(sectorLogReturns.keys == MethodologyEquitySector.entries.toSet())
        require(sectorLogReturns.values.all(Double::isFinite))
        require(styleStandardNormals.keys == EquityReferenceStyleFactor.entries.toSet())
        require(styleStandardNormals.values.all(Double::isFinite))
        require(
            idiosyncraticStandardNormals.size ==
                EquityReferenceFactorExposure.IDIOSYNCRATIC_BUCKET_COUNT,
        )
        require(idiosyncraticStandardNormals.all(Double::isFinite))
        require(elapsedYearFraction.isFinite() && elapsedYearFraction in 0.0..1.0)
    }
}
