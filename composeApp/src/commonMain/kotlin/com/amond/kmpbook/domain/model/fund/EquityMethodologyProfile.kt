package com.amond.kmpbook.domain.model.fund

import kotlinx.datetime.LocalDate

/**
 * Version-independent equity-index construction rules owned by a [BenchmarkDefinition].
 *
 * Product replication, fees and tracking error do not belong here. The enclosing benchmark owns
 * identity, version, administrator and official source metadata.
 */
data class EquityMethodologyProfile(
    val effectiveFrom: LocalDate,
    val referenceUniverse: FundReferenceUniverse,
    val selectionModel: FundSelectionModel,
    val weightingModel: FundWeightingModel,
    val targetConstituentCount: Int,
    val minDividendPaymentYears: Int,
    val minFloatMarketCap: Double,
    val minAverageDailyValueTraded: Double,
    val eligibleYieldFraction: Double,
    val incumbentRankBuffer: Int,
    val individualWeightCap: Double,
    val sectorWeightCap: Double,
    val annualReconstitutionMonth: Int,
    val rebalanceMonths: Set<Int>,
    val dailyWeightThreshold: Double,
    val dailyAggregateWeightLimit: Double,
) {
    init {
        require(targetConstituentCount in 1..MAX_CONSTITUENTS)
        require(minDividendPaymentYears in 0..MAX_DIVIDEND_YEARS)
        require(minFloatMarketCap.isFinite() && minFloatMarketCap >= 0.0)
        require(minAverageDailyValueTraded.isFinite() && minAverageDailyValueTraded >= 0.0)
        require(eligibleYieldFraction.isFinite() && eligibleYieldFraction in MIN_POSITIVE_FRACTION..1.0)
        require(incumbentRankBuffer in targetConstituentCount..MAX_RANK_BUFFER)
        require(individualWeightCap.isFinite() && individualWeightCap in MIN_POSITIVE_FRACTION..1.0)
        require(sectorWeightCap.isFinite() && sectorWeightCap in individualWeightCap..1.0)
        require(targetConstituentCount * individualWeightCap >= 1.0 - WEIGHT_EPSILON) {
            "The target constituent count cannot allocate 100% within the individual cap."
        }
        require(annualReconstitutionMonth in 1..12)
        require(rebalanceMonths.isNotEmpty() && rebalanceMonths.all { it in 1..12 })
        require(annualReconstitutionMonth in rebalanceMonths)
        require(dailyWeightThreshold.isFinite() && dailyWeightThreshold in individualWeightCap..1.0)
        require(
            dailyAggregateWeightLimit.isFinite() &&
                dailyAggregateWeightLimit in dailyWeightThreshold..1.0,
        )
        when (selectionModel) {
            FundSelectionModel.DIVIDEND_FUNDAMENTAL_COMPOSITE -> {
                require(minDividendPaymentYears > 0)
                require(minFloatMarketCap > 0.0)
                require(minAverageDailyValueTraded > 0.0)
            }
        }
    }

    companion object {
        const val MAX_CONSTITUENTS: Int = 1_024
        const val MAX_RANK_BUFFER: Int = 100_000
        private const val MAX_DIVIDEND_YEARS: Int = 200
        private const val MIN_POSITIVE_FRACTION: Double = 1e-12
        private const val WEIGHT_EPSILON: Double = 1e-9
    }
}
