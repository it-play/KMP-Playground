package com.amond.kmpbook.domain.simulation.reference

import com.amond.kmpbook.domain.model.fund.FundOfFundsCategory
import com.amond.kmpbook.domain.model.fund.FundOfFundsMethodologyProfile
import com.amond.kmpbook.domain.model.fund.FundOfFundsUniverse
import com.amond.kmpbook.domain.simulation.price.DeterministicRandom
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Campaign-seeded candidate repository shared by every fund-of-funds methodology.
 *
 * IDs represent synthetic future funds, not current real tickers. A candidate has the same
 * economics in PCEF and YYY when both methodologies happen to select it.
 */
class FundOfFundsUniverseRepository private constructor(private val seed: Long) {
    private val candidatesByUniverse: Map<FundOfFundsUniverse, List<FundOfFundsCandidate>> =
        FundOfFundsUniverse.entries.associateWith(::buildCandidates)
    private val candidateById: Map<String, FundOfFundsCandidate> = candidatesByUniverse.values
        .flatten()
        .associateBy(FundOfFundsCandidate::candidateFundId)
    private val supportedSnapshotsByCandidateId: Map<String, List<FundOfFundsCandidateSnapshot>> =
        candidateById.mapValues { (_, candidate) ->
            SUPPORTED_YEARS.map { year -> snapshot(candidate, year) }
        }

    val universeFingerprint: String = stableHex(
        "$UNIVERSE_MODEL_VERSION|$seed|" + candidatesByUniverse.entries.joinToString { (universe, values) ->
            "${universe.name}:${values.size}"
        },
    )

    fun containsCandidate(candidateFundId: String): Boolean = candidateFundId in candidateById

    internal fun snapshotFor(
        candidateFundId: String,
        year: Int,
    ): FundOfFundsCandidateSnapshot? {
        val candidate = candidateById[candidateFundId] ?: return null
        return if (year in FIRST_SUPPORTED_YEAR..LAST_SUPPORTED_YEAR) {
            supportedSnapshotsByCandidateId.getValue(candidateFundId)[year - FIRST_SUPPORTED_YEAR]
        } else {
            // Keep the historical internal contract for callers inspecting an out-of-campaign year.
            snapshot(candidate, year)
        }
    }

    internal fun snapshots(
        profile: FundOfFundsMethodologyProfile,
        year: Int,
    ): List<FundOfFundsCandidateSnapshot> {
        require(year in FIRST_SUPPORTED_YEAR..LAST_SUPPORTED_YEAR)
        val candidates = candidatesByUniverse.getValue(profile.universe)
            .filter { candidate -> candidate.category in profile.eligibleCategories }
            .take(profile.candidateUniverseSize)
        require(candidates.size == profile.candidateUniverseSize) {
            "The configured fund-of-funds candidate universe is larger than the canonical repository."
        }
        val snapshotIndex = year - FIRST_SUPPORTED_YEAR
        return candidates.map { candidate ->
            supportedSnapshotsByCandidateId.getValue(candidate.candidateFundId)[snapshotIndex]
        }
    }

    private fun buildCandidates(universe: FundOfFundsUniverse): List<FundOfFundsCandidate> {
        val (prefix, count, categories) = when (universe) {
            FundOfFundsUniverse.US_CLOSED_END_FUNDS -> Triple(
                "cef",
                CLOSED_END_CANDIDATE_COUNT,
                listOf(
                    FundOfFundsCategory.TAXABLE_INVESTMENT_GRADE,
                    FundOfFundsCategory.MUNICIPAL_FIXED_INCOME,
                    FundOfFundsCategory.HIGH_YIELD_CREDIT,
                    FundOfFundsCategory.EQUITY_OPTION_INCOME,
                    FundOfFundsCategory.MULTI_ASSET_INCOME,
                ),
            )
            FundOfFundsUniverse.US_OPTION_INCOME_ETFS -> Triple(
                "option",
                OPTION_INCOME_CANDIDATE_COUNT,
                listOf(
                    FundOfFundsCategory.EQUITY_OPTION_INCOME,
                    FundOfFundsCategory.SINGLE_SECURITY_OPTION_INCOME,
                ),
            )
        }
        return (1..count).map { ordinal ->
            val candidateFundId = "sim-fof:$prefix:${ordinal.toString().padStart(3, '0')}"
            val random = DeterministicRandom.keyed(seed, "fund-of-funds-candidate:$candidateFundId")
            val category = categories[(ordinal - 1) % categories.size]
            val categoryYield = when (category) {
                FundOfFundsCategory.TAXABLE_INVESTMENT_GRADE -> .065
                FundOfFundsCategory.MUNICIPAL_FIXED_INCOME -> .055
                FundOfFundsCategory.HIGH_YIELD_CREDIT -> .095
                FundOfFundsCategory.EQUITY_OPTION_INCOME -> .105
                FundOfFundsCategory.MULTI_ASSET_INCOME -> .085
                FundOfFundsCategory.SINGLE_SECURITY_OPTION_INCOME -> .24
            }
            val categoryLeverage = when (category) {
                FundOfFundsCategory.TAXABLE_INVESTMENT_GRADE,
                FundOfFundsCategory.MUNICIPAL_FIXED_INCOME,
                FundOfFundsCategory.HIGH_YIELD_CREDIT,
                -> .25
                FundOfFundsCategory.EQUITY_OPTION_INCOME,
                FundOfFundsCategory.MULTI_ASSET_INCOME,
                -> .15
                FundOfFundsCategory.SINGLE_SECURITY_OPTION_INCOME -> 0.0
            }
            FundOfFundsCandidate(
                candidateFundId = candidateFundId,
                universe = universe,
                category = category,
                baseNetAssetValue = exp(random.nextDouble(ln(25_000_000.0), ln(4_000_000_000.0))),
                baseMarketDiscountRate = (-.07 + random.nextGaussian() * .07).coerceIn(-.40, .20),
                baseDistributionYield = (categoryYield + random.nextGaussian() * .025).coerceIn(.005, .65),
                baseLeverageRatio = (categoryLeverage + random.nextGaussian() * .08).coerceIn(0.0, .60),
                baseLiquidityScore = random.nextDouble(.08, 1.0),
                baseQualityScore = tanh(random.nextGaussian() * .70),
                baseExpenseRate = random.nextDouble(.003, .035),
                annualResidualVolatility = when (universe) {
                    FundOfFundsUniverse.US_CLOSED_END_FUNDS -> random.nextDouble(.08, .35)
                    FundOfFundsUniverse.US_OPTION_INCOME_ETFS -> random.nextDouble(.18, .80)
                },
            )
        }.sortedBy(FundOfFundsCandidate::candidateFundId)
    }

    private fun snapshot(
        candidate: FundOfFundsCandidate,
        year: Int,
    ): FundOfFundsCandidateSnapshot {
        val elapsedYears = (year - BASE_YEAR).coerceAtLeast(0)
        val random = DeterministicRandom.keyed(
            seed,
            "fund-of-funds-annual:${candidate.candidateFundId}:$year",
        )
        val quality = (candidate.baseQualityScore + random.nextGaussian() * .12).coerceIn(-1.0, 1.0)
        val momentum = tanh(random.nextGaussian() * .75 + quality * .15)
        val nav = candidate.baseNetAssetValue * exp(
            (.025 + quality * .012) * elapsedYears +
                candidate.annualResidualVolatility * .25 * random.nextGaussian() * sqrt(elapsedYears.toDouble()),
        )
        val discount = (
            candidate.baseMarketDiscountRate * .70 + random.nextGaussian() * .055 - momentum * .025
            ).coerceIn(-.70, .60)
        val distributionYield = (
            candidate.baseDistributionYield * exp(random.nextGaussian() * .13) - quality * .006
            ).coerceIn(0.0, .85)
        val liquidity = (
            candidate.baseLiquidityScore + random.nextGaussian() * .07 + ln(nav / candidate.baseNetAssetValue) * .02
            ).coerceIn(0.0, 1.0)
        val eligibilityDraw = DeterministicRandom.keyed(
            seed,
            "fund-of-funds-eligibility:${candidate.candidateFundId}:$year",
        ).nextDouble()
        val suspensionProbability = when (candidate.universe) {
            FundOfFundsUniverse.US_CLOSED_END_FUNDS -> .018
            FundOfFundsUniverse.US_OPTION_INCOME_ETFS -> .035
        }
        return FundOfFundsCandidateSnapshot(
            candidateFundId = candidate.candidateFundId,
            universe = candidate.universe,
            category = candidate.category,
            netAssetValue = nav.coerceIn(1.0, 1e16),
            marketDiscountRate = discount,
            indicatedAnnualDistributionYield = distributionYield,
            leverageRatio = candidate.baseLeverageRatio,
            liquidityScore = liquidity,
            qualityScore = quality,
            expenseRate = candidate.baseExpenseRate,
            annualResidualVolatility = candidate.annualResidualVolatility,
            trailingMomentumScore = momentum,
            isEligible = eligibilityDraw >= suspensionProbability,
        )
    }

    private fun stableHex(value: String): String =
        DeterministicRandom.stableHash64(value).toULong().toString(16).padStart(16, '0')

    companion object {
        fun forCampaignSeed(campaignSeed: Long): FundOfFundsUniverseRepository =
            FundOfFundsUniverseRepository(
                DeterministicRandom.mixSeed(campaignSeed, REPOSITORY_STREAM_ID),
            )

        const val UNIVERSE_MODEL_VERSION: String = "fund-of-funds-universe-v1"
        private const val REPOSITORY_STREAM_ID: Long = 0x464f46554e495652L
        private const val BASE_YEAR: Int = 2026
        private const val FIRST_SUPPORTED_YEAR: Int = 2026
        private const val LAST_SUPPORTED_YEAR: Int = 2040
        private val SUPPORTED_YEARS: IntRange = FIRST_SUPPORTED_YEAR..LAST_SUPPORTED_YEAR
        private const val CLOSED_END_CANDIDATE_COUNT: Int = 320
        private const val OPTION_INCOME_CANDIDATE_COUNT: Int = 192
    }
}
