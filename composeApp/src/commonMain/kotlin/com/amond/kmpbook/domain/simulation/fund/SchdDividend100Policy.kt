package com.amond.kmpbook.domain.simulation.fund

import com.amond.kmpbook.domain.model.fund.EquityMethodologyProfile
import com.amond.kmpbook.domain.model.fund.FundReferenceUniverse
import com.amond.kmpbook.domain.model.fund.FundSelectionModel
import com.amond.kmpbook.domain.model.fund.FundWeightingModel
import com.amond.kmpbook.domain.model.fund.MethodologyEquitySector
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

/**
 * Exact supported policy for the Dow Jones U.S. Dividend 100/SCHD reference methodology.
 *
 * This deliberately remains a named policy instead of pretending that its yield screen, four-rank
 * composite, incumbent buffer, caps and calendar are a generic passive-equity DSL.
 */
internal object SchdDividend100Policy {
    fun validate(profile: EquityMethodologyProfile) {
        requireOfficial(
            profile.referenceUniverse == FundReferenceUniverse.US_BROAD_EQUITY,
            "the US broad-equity reference universe",
        )
        requireOfficial(
            profile.selectionModel == FundSelectionModel.DIVIDEND_FUNDAMENTAL_COMPOSITE,
            "the dividend fundamental-composite selection model",
        )
        requireOfficial(
            profile.weightingModel == FundWeightingModel.CAPPED_FLOAT_MARKET_CAP,
            "capped float-market-cap weighting",
        )
        requireOfficial(profile.targetConstituentCount == TARGET_CONSTITUENT_COUNT, "100 constituents")
        requireOfficial(profile.minDividendPaymentYears == MIN_DIVIDEND_PAYMENT_YEARS, "10 dividend years")
        requireOfficial(
            abs(profile.minFloatMarketCap - MIN_FLOAT_MARKET_CAP) <= DOUBLE_EPSILON,
            "a USD 500 million minimum float market cap",
        )
        requireOfficial(
            abs(profile.minAverageDailyValueTraded - MIN_AVERAGE_DAILY_VALUE_TRADED) <= DOUBLE_EPSILON,
            "USD 2 million minimum three-month ADVT",
        )
        requireOfficial(
            abs(profile.eligibleYieldFraction - ELIGIBLE_YIELD_FRACTION) <= DOUBLE_EPSILON,
            "the top half of eligible indicated dividend yields",
        )
        requireOfficial(profile.incumbentRankBuffer == INCUMBENT_RANK_BUFFER, "the top-200 incumbent buffer")
        requireOfficial(
            abs(profile.individualWeightCap - INDIVIDUAL_WEIGHT_CAP) <= DOUBLE_EPSILON,
            "the 4% constituent cap",
        )
        requireOfficial(
            abs(profile.sectorWeightCap - SECTOR_WEIGHT_CAP) <= DOUBLE_EPSILON,
            "the 25% sector cap",
        )
        requireOfficial(
            abs(profile.dailyWeightThreshold - DAILY_WEIGHT_THRESHOLD) <= DOUBLE_EPSILON,
            "the 4.7% daily constituent threshold",
        )
        requireOfficial(
            abs(profile.dailyAggregateWeightLimit - DAILY_AGGREGATE_WEIGHT_LIMIT) <= DOUBLE_EPSILON,
            "the 22% daily aggregate threshold",
        )
        require(profile.annualReconstitutionMonth == 3) {
            "The SCHD policy supports a March annual reconstitution only."
        }
        require(profile.rebalanceMonths == setOf(3, 6, 9, 12)) {
            "The SCHD policy supports March, June, September and December rebalances only."
        }
        require(
            profile.effectiveFrom == ReferencePortfolioCalendar.scheduledRebalanceDate(
                profile.effectiveFrom.year,
                profile.annualReconstitutionMonth,
            ),
        ) { "The methodology effective date must be its first annual effective date." }
    }

    private fun requireOfficial(condition: Boolean, rule: String) {
        require(condition) { "Unsupported equity methodology: the SCHD policy requires $rule." }
    }

    fun selectConstituents(
        profile: EquityMethodologyProfile,
        snapshots: Collection<SimulatedReferenceEquitySnapshot>,
        incumbentAssetIds: Set<String>,
    ): List<RankedReferenceCandidate> {
        validate(profile)
        val ranked = rankCandidates(profile, snapshots)
        val retained = ranked.filter { candidate ->
            candidate.snapshot.definition.assetId in incumbentAssetIds &&
                candidate.compositeRank <= profile.incumbentRankBuffer
        }.take(profile.targetConstituentCount)
        val retainedIds = retained.mapTo(hashSetOf()) { it.snapshot.definition.assetId }
        val additions = ranked.asSequence()
            .filterNot { it.snapshot.definition.assetId in retainedIds }
            .take(profile.targetConstituentCount - retained.size)
            .toList()
        return (retained + additions)
            .sortedBy(RankedReferenceCandidate::compositeRank)
            .also { selected ->
                require(selected.size == profile.targetConstituentCount) {
                    "The SCHD screen produced fewer candidates than its target constituent count."
                }
            }
    }

    fun cappedFloatMarketCapWeights(
        rawFloatMarketValues: Map<String, Double>,
        methodologySectors: Map<String, MethodologyEquitySector>,
        individualCap: Double,
        sectorCap: Double,
    ): Map<String, Double> {
        require(rawFloatMarketValues.isNotEmpty() && rawFloatMarketValues.keys == methodologySectors.keys)
        require(rawFloatMarketValues.values.all { it > 0.0 && it.isFinite() })
        val assetIds = rawFloatMarketValues.keys.sorted()
        val bySector = assetIds.groupBy(methodologySectors::getValue)
        val totalCapacity = bySector.values.sumOf { assets ->
            min(sectorCap, assets.size * individualCap)
        }
        require(totalCapacity >= 1.0 - WEIGHT_ALLOCATION_EPSILON) {
            "The constituent and sector caps cannot allocate 100%."
        }

        fun sectorScale(assets: List<String>, globalScale: Double): Double {
            val uncappedTotal = assets.sumOf { assetId ->
                min(individualCap, rawFloatMarketValues.getValue(assetId) * globalScale)
            }
            if (uncappedTotal <= sectorCap) return globalScale
            var low = 0.0
            var high = globalScale
            repeat(CAP_BISECTION_STEPS) {
                val middle = (low + high) / 2.0
                val total = assets.sumOf { assetId ->
                    min(individualCap, rawFloatMarketValues.getValue(assetId) * middle)
                }
                if (total < sectorCap) low = middle else high = middle
            }
            return low
        }

        fun weightsAt(globalScale: Double): Map<String, Double> = buildMap {
            bySector.toSortedMap(compareBy(MethodologyEquitySector::ordinal)).forEach { (_, assets) ->
                val scale = sectorScale(assets, globalScale)
                assets.sorted().forEach { assetId ->
                    put(assetId, min(individualCap, rawFloatMarketValues.getValue(assetId) * scale))
                }
            }
        }

        var low = 0.0
        var high = 1.0 / rawFloatMarketValues.values.sum()
        var expansionCount = 0
        while (
            weightsAt(high).values.sum() < 1.0 - WEIGHT_ALLOCATION_EPSILON &&
            expansionCount < CAP_SCALE_EXPANSION_STEPS
        ) {
            high *= 2.0
            require(high.isFinite()) { "The capped-weight scale exceeded the finite range." }
            expansionCount += 1
        }
        require(weightsAt(high).values.sum() >= 1.0 - WEIGHT_ALLOCATION_EPSILON) {
            "No finite scale satisfies the constituent and sector caps."
        }
        repeat(CAP_BISECTION_STEPS) {
            val middle = (low + high) / 2.0
            if (weightsAt(middle).values.sum() < 1.0 - WEIGHT_ALLOCATION_EPSILON) low = middle else high = middle
        }
        val result = weightsAt(low).toMutableMap()
        var remaining = 1.0 - result.values.sum()
        val sectorTotals = result.entries.groupBy { methodologySectors.getValue(it.key) }
            .mapValues { (_, entries) -> entries.sumOf(Map.Entry<String, Double>::value) }
            .toMutableMap()
        for (assetId in assetIds) {
            if (remaining <= WEIGHT_ALLOCATION_EPSILON) break
            val sector = methodologySectors.getValue(assetId)
            val slack = min(
                individualCap - result.getValue(assetId),
                sectorCap - sectorTotals.getValue(sector),
            ).coerceAtLeast(0.0)
            val addition = min(slack, remaining)
            result[assetId] = result.getValue(assetId) + addition
            sectorTotals[sector] = sectorTotals.getValue(sector) + addition
            remaining -= addition
        }
        require(remaining <= 1e-10 && abs(result.values.sum() - 1.0) <= 1e-10)
        require(result.values.all { it <= individualCap + 1e-10 })
        require(sectorTotals.values.all { it <= sectorCap + 1e-10 })
        return result
    }

    private fun rankCandidates(
        profile: EquityMethodologyProfile,
        snapshots: Collection<SimulatedReferenceEquitySnapshot>,
    ): List<RankedReferenceCandidate> {
        val screened = snapshots.filter { snapshot ->
            snapshot.dividendPaymentYears >= profile.minDividendPaymentYears &&
                snapshot.floatMarketCap >= profile.minFloatMarketCap &&
                snapshot.averageDailyValueTraded >= profile.minAverageDailyValueTraded &&
                snapshot.definition.methodologySector != MethodologyEquitySector.REAL_ESTATE
        }
        val eligibleCount = ceil(screened.size * profile.eligibleYieldFraction)
            .toInt()
            .coerceAtMost(screened.size)
        val eligible = screened.sortedWith(
            compareByDescending<SimulatedReferenceEquitySnapshot> { it.indicatedDividendYield }
                .thenBy { it.definition.assetId },
        ).take(eligibleCount)
        require(eligible.size >= profile.targetConstituentCount) {
            "The dividend-yield screen produced too few candidates."
        }

        fun ranks(value: (SimulatedReferenceEquitySnapshot) -> Double): Map<String, Int> =
            eligible.sortedWith(compareByDescending(value).thenBy { it.definition.assetId })
                .mapIndexed { index, candidate -> candidate.definition.assetId to index + 1 }
                .toMap()

        val fcfRanks = ranks(SimulatedReferenceEquitySnapshot::freeCashFlowToDebt)
        val roeRanks = ranks(SimulatedReferenceEquitySnapshot::returnOnEquity)
        val yieldRanks = ranks(SimulatedReferenceEquitySnapshot::indicatedDividendYield)
        val growthRanks = ranks(SimulatedReferenceEquitySnapshot::fiveYearDividendGrowth)
        return eligible.sortedWith(
            compareBy<SimulatedReferenceEquitySnapshot> { candidate ->
                fcfRanks.getValue(candidate.definition.assetId) +
                    roeRanks.getValue(candidate.definition.assetId) +
                    yieldRanks.getValue(candidate.definition.assetId) +
                    growthRanks.getValue(candidate.definition.assetId)
            }.thenByDescending(SimulatedReferenceEquitySnapshot::indicatedDividendYield)
                .thenBy { it.definition.assetId },
        ).mapIndexed { index, snapshot -> RankedReferenceCandidate(snapshot, index + 1) }
    }

    private const val CAP_BISECTION_STEPS: Int = 96
    private const val CAP_SCALE_EXPANSION_STEPS: Int = 256
    private const val WEIGHT_ALLOCATION_EPSILON: Double = 1e-12
    private const val DOUBLE_EPSILON: Double = 1e-12
    private const val TARGET_CONSTITUENT_COUNT: Int = 100
    private const val MIN_DIVIDEND_PAYMENT_YEARS: Int = 10
    private const val MIN_FLOAT_MARKET_CAP: Double = 500_000_000.0
    private const val MIN_AVERAGE_DAILY_VALUE_TRADED: Double = 2_000_000.0
    private const val ELIGIBLE_YIELD_FRACTION: Double = 0.5
    private const val INCUMBENT_RANK_BUFFER: Int = 200
    private const val INDIVIDUAL_WEIGHT_CAP: Double = 0.04
    private const val SECTOR_WEIGHT_CAP: Double = 0.25
    private const val DAILY_WEIGHT_THRESHOLD: Double = 0.047
    private const val DAILY_AGGREGATE_WEIGHT_LIMIT: Double = 0.22
}
