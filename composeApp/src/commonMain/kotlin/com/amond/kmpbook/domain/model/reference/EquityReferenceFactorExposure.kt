package com.amond.kmpbook.domain.model.reference

import com.amond.kmpbook.domain.model.fund.MethodologyEquitySector
import kotlin.math.abs

/** Vectorized exposure snapshot compiled from current representative constituent weights. */
class EquityReferenceFactorExposure(
    countryWeights: Map<String, Double>,
    sectorWeights: Map<MethodologyEquitySector, Double>,
    styleExposures: Map<EquityReferenceStyleFactor, Double>,
    idiosyncraticVolatilityWeights: List<Double>,
    val thematicExposure: Double,
    val activeManagementExposure: Double,
) {
    val countryWeights: Map<String, Double> = countryWeights.toSortedMap().toMap()
    val sectorWeights: Map<MethodologyEquitySector, Double> = sectorWeights
        .toSortedMap(compareBy(MethodologyEquitySector::ordinal))
        .toMap()
    val styleExposures: Map<EquityReferenceStyleFactor, Double> = styleExposures
        .toSortedMap(compareBy(EquityReferenceStyleFactor::ordinal))
        .toMap()
    val idiosyncraticVolatilityWeights: List<Double> = idiosyncraticVolatilityWeights.toList()

    init {
        require(this.countryWeights.isNotEmpty())
        require(this.countryWeights.keys.all(COUNTRY_CODE_PATTERN::matches))
        require(this.countryWeights.values.all { it.isFinite() && it in 0.0..1.0 })
        require(abs(this.countryWeights.values.sum() - 1.0) <= WEIGHT_EPSILON)
        require(this.sectorWeights.isNotEmpty())
        require(this.sectorWeights.values.all { it.isFinite() && it in 0.0..1.0 })
        require(abs(this.sectorWeights.values.sum() - 1.0) <= WEIGHT_EPSILON)
        require(this.styleExposures.keys == EquityReferenceStyleFactor.entries.toSet())
        require(
            this.styleExposures.values.all {
                it.isFinite() && it in -MAX_FACTOR_EXPOSURE..MAX_FACTOR_EXPOSURE
            },
        )
        require(this.idiosyncraticVolatilityWeights.size == IDIOSYNCRATIC_BUCKET_COUNT)
        require(
            this.idiosyncraticVolatilityWeights.all {
                it.isFinite() && it in -MAX_IDIOSYNCRATIC_WEIGHT..MAX_IDIOSYNCRATIC_WEIGHT
            },
        )
        require(thematicExposure.isFinite() && thematicExposure in -1.0..1.0)
        require(activeManagementExposure.isFinite() && activeManagementExposure in -1.0..1.0)
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is EquityReferenceFactorExposure &&
            countryWeights == other.countryWeights &&
            sectorWeights == other.sectorWeights &&
            styleExposures == other.styleExposures &&
            idiosyncraticVolatilityWeights == other.idiosyncraticVolatilityWeights &&
            thematicExposure == other.thematicExposure &&
            activeManagementExposure == other.activeManagementExposure

    override fun hashCode(): Int {
        var result = countryWeights.hashCode()
        result = 31 * result + sectorWeights.hashCode()
        result = 31 * result + styleExposures.hashCode()
        result = 31 * result + idiosyncraticVolatilityWeights.hashCode()
        result = 31 * result + thematicExposure.hashCode()
        result = 31 * result + activeManagementExposure.hashCode()
        return result
    }

    companion object {
        const val IDIOSYNCRATIC_BUCKET_COUNT: Int = 32
        private const val WEIGHT_EPSILON: Double = 1e-8
        private const val MAX_FACTOR_EXPOSURE: Double = 3.0
        private const val MAX_IDIOSYNCRATIC_WEIGHT: Double = 5.0
        private val COUNTRY_CODE_PATTERN = Regex("[A-Z]{2}")
    }
}
