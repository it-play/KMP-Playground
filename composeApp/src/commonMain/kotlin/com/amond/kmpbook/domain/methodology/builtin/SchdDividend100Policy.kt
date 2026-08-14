package com.amond.kmpbook.domain.methodology.builtin

import com.amond.kmpbook.domain.methodology.EquityMethodologyCandidate
import com.amond.kmpbook.domain.methodology.EquityMethodologyConstraintInput
import com.amond.kmpbook.domain.methodology.EquityMethodologyPolicy
import com.amond.kmpbook.domain.methodology.EquityMethodologyPortfolioConstraints
import com.amond.kmpbook.domain.methodology.EquityMethodologyRemovalDecision
import com.amond.kmpbook.domain.methodology.EquityMethodologyRemovalInput
import com.amond.kmpbook.domain.methodology.EquityMethodologySelection
import com.amond.kmpbook.domain.methodology.EquityMethodologySelectionInput
import com.amond.kmpbook.domain.methodology.EquityMethodologyWeightingInput
import com.amond.kmpbook.domain.methodology.StandardEquityMethodologyComponents
import com.amond.kmpbook.domain.methodology.StandardEquityMethodologySignalIds
import com.amond.kmpbook.domain.model.fund.BenchmarkDefinition
import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import com.amond.kmpbook.domain.model.fund.EquityMethodologyProfile
import com.amond.kmpbook.domain.model.fund.FundReferenceUniverse
import com.amond.kmpbook.domain.model.fund.MethodologyEquitySector
import com.amond.kmpbook.domain.model.methodology.EquityMethodologyRef
import kotlin.math.abs
import kotlin.math.ceil
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/** Exact supported policy for the Dow Jones U.S. Dividend 100/SCHD reference methodology. */
internal object SchdDividend100Policy : EquityMethodologyPolicy {
    override val schedule = SchdDividend100Schedule
    override val requiredDecimalSignalIds: Set<String> = buildSet {
        add(StandardEquityMethodologySignalIds.FLOAT_MARKET_CAP)
        add(StandardEquityMethodologySignalIds.AVERAGE_DAILY_VALUE_TRADED)
        add(StandardEquityMethodologySignalIds.INDICATED_DIVIDEND_YIELD)
        add(StandardEquityMethodologySignalIds.FREE_CASH_FLOW_TO_DEBT)
        add(StandardEquityMethodologySignalIds.RETURN_ON_EQUITY)
        add(StandardEquityMethodologySignalIds.FIVE_YEAR_DIVIDEND_GROWTH)
    }
    override val requiredIntegerSignalIds: Set<String> = buildSet {
        add(StandardEquityMethodologySignalIds.DIVIDEND_PAYMENT_YEARS)
    }
    override val requiredBooleanSignalIds: Set<String> = buildSet {
        add(StandardEquityMethodologySignalIds.DIVIDEND_PROGRAM_SUSPENDED)
    }

    override fun validate(definition: BenchmarkDefinition, profile: EquityMethodologyProfile) {
        requireOfficial(definition.ref == CANONICAL_BENCHMARK_REF, "the canonical benchmark identity")
        requireOfficial(
            profile.methodologyRef == EquityMethodologyRef.SCHD_DIVIDEND_100_V1,
            "the built-in SCHD methodology registration",
        )
        requireOfficial(
            profile.referenceUniverse == FundReferenceUniverse.US_BROAD_EQUITY,
            "the US broad-equity reference universe",
        )
        profile.parameters.requireExactKeys(
            integerKeys = INTEGER_PARAMETER_KEYS,
            decimalKeys = DECIMAL_PARAMETER_KEYS,
            integerSetKeys = INTEGER_SET_PARAMETER_KEYS,
        )
        requireOfficial(targetCount(profile) == 100, "100 constituents")
        requireOfficial(minDividendPaymentYears(profile) == 10, "10 dividend years")
        requireOfficial(abs(minFloatMarketCap(profile) - 500_000_000.0) <= EPSILON, "a USD 500 million FMC")
        requireOfficial(abs(minAverageDailyValueTraded(profile) - 2_000_000.0) <= EPSILON, "USD 2 million ADVT")
        requireOfficial(abs(eligibleYieldFraction(profile) - 0.5) <= EPSILON, "the top half dividend-yield screen")
        requireOfficial(incumbentRankBuffer(profile) == 200, "the top-200 incumbent buffer")
        requireOfficial(abs(individualWeightCap(profile) - 0.04) <= EPSILON, "the 4% constituent cap")
        requireOfficial(abs(sectorWeightCap(profile) - 0.25) <= EPSILON, "the 25% sector cap")
        requireOfficial(abs(dailyWeightThreshold(profile) - 0.047) <= EPSILON, "the 4.7% threshold")
        requireOfficial(abs(dailyAggregateLimit(profile) - 0.22) <= EPSILON, "the 22% aggregate limit")
        requireOfficial(dailyDelay(profile) == 2, "the T+2 cap reweight delay")
        requireOfficial(SchdDividend100Schedule.annualReconstitutionMonth(profile) == 3, "March reconstitution")
        requireOfficial(SchdDividend100Schedule.rebalanceMonths(profile) == setOf(3, 6, 9, 12), "quarterly months")
        require(schedule.initialScheduledAction(profile).effectiveDate == profile.effectiveFrom)
    }

    override fun portfolioConstraints(profile: EquityMethodologyProfile) =
        EquityMethodologyPortfolioConstraints(
            minimumConstituentCount = 1,
            maximumConstituentCount = targetCount(profile),
            scheduledSelectionCount = targetCount(profile),
            individualWeightCap = individualWeightCap(profile),
            sectorWeightCap = sectorWeightCap(profile),
        )

    override fun select(input: EquityMethodologySelectionInput): List<EquityMethodologySelection> {
        val profile = input.profile
        val screened = input.candidates.filter { candidate ->
            integer(candidate, StandardEquityMethodologySignalIds.DIVIDEND_PAYMENT_YEARS) >=
                minDividendPaymentYears(profile) &&
                decimal(candidate, StandardEquityMethodologySignalIds.FLOAT_MARKET_CAP) >= minFloatMarketCap(profile) &&
                decimal(candidate, StandardEquityMethodologySignalIds.AVERAGE_DAILY_VALUE_TRADED) >=
                minAverageDailyValueTraded(profile) && candidate.sector != MethodologyEquitySector.REAL_ESTATE
        }
        val eligibleCount = ceil(screened.size * eligibleYieldFraction(profile)).toInt().coerceAtMost(screened.size)
        val eligible = screened.sortedWith(
            compareByDescending<EquityMethodologyCandidate> {
                decimal(it, StandardEquityMethodologySignalIds.INDICATED_DIVIDEND_YIELD)
            }.thenBy(EquityMethodologyCandidate::assetId),
        ).take(eligibleCount)
        require(eligible.size >= targetCount(profile)) { "The dividend-yield screen produced too few candidates." }
        val components = StandardEquityMethodologyComponents
        fun ranks(signalId: String) = components.descendingOrdinalRanks(
            eligible.associate { it.assetId to decimal(it, signalId) },
        )
        val fcfRanks = ranks(StandardEquityMethodologySignalIds.FREE_CASH_FLOW_TO_DEBT)
        val roeRanks = ranks(StandardEquityMethodologySignalIds.RETURN_ON_EQUITY)
        val yieldRanks = ranks(StandardEquityMethodologySignalIds.INDICATED_DIVIDEND_YIELD)
        val growthRanks = ranks(StandardEquityMethodologySignalIds.FIVE_YEAR_DIVIDEND_GROWTH)
        val candidatesById = eligible.associateBy(EquityMethodologyCandidate::assetId)
        val rankedIds = eligible.map(EquityMethodologyCandidate::assetId).sortedWith(
            compareBy<String> { id -> fcfRanks.getValue(id) + roeRanks.getValue(id) + yieldRanks.getValue(id) + growthRanks.getValue(id) }
                .thenByDescending { id -> decimal(candidatesById.getValue(id), StandardEquityMethodologySignalIds.INDICATED_DIVIDEND_YIELD) }
                .thenBy { it },
        )
        val rankById = rankedIds.withIndex().associate { (index, id) -> id to index + 1 }
        return components.bufferedSelection(
            rankedAssetIds = rankedIds,
            incumbentAssetIds = input.incumbentAssetIds,
            targetCount = targetCount(profile),
            incumbentRankBuffer = incumbentRankBuffer(profile),
        ).map { id -> EquityMethodologySelection(id, rankById.getValue(id)) }
            .sortedBy(EquityMethodologySelection::rank)
    }

    override fun targetWeights(input: EquityMethodologyWeightingInput): Map<String, Double> {
        val groupIds = input.selectedCandidates.associate { it.assetId to it.sector.name }
        val groups = groupIds.values.toSet()
        return StandardEquityMethodologyComponents.cappedGroupWeights(
            rawValues = input.referenceMarketValues,
            groupIds = groupIds,
            groupOrder = MethodologyEquitySector.entries.map(MethodologyEquitySector::name).filter(groups::contains),
            individualCap = individualWeightCap(input.profile),
            groupCap = sectorWeightCap(input.profile),
        )
    }

    override fun constraintReweightEffectiveDate(input: EquityMethodologyConstraintInput): LocalDate? {
        val profile = input.profile
        val breach = input.currentWeights.values.filter { it > dailyWeightThreshold(profile) }.sum() >
            dailyAggregateLimit(profile)
        if (!breach || SchdDividend100Calendar.isDailyCapFreezeDate(
                SchdDividend100Schedule.rebalanceMonths(profile), input.observationDate,
            )
        ) return null
        val effective = schedule.addTradingDays(input.observationDate, dailyDelay(profile))
        return effective.takeUnless {
            SchdDividend100Calendar.isDailyCapFreezeDate(
                SchdDividend100Schedule.rebalanceMonths(profile), it,
            )
        }
    }

    override fun nextExtraordinaryRemovalReviewDate(
        profile: EquityMethodologyProfile,
        afterExclusive: LocalDate,
    ): LocalDate? = (afterExclusive.year..afterExclusive.year + 2).asSequence()
        .flatMap { year -> (1..12).asSequence().map { year to it } }
        .map { (year, month) -> SchdDividend100Calendar.lastUsTradingDateOfMonth(year, month) }
        .firstOrNull { it > afterExclusive }

    override fun extraordinaryRemovalDecision(input: EquityMethodologyRemovalInput): EquityMethodologyRemovalDecision? {
        val removed = input.constituents.filter {
            it.signals.requireBoolean(StandardEquityMethodologySignalIds.DIVIDEND_PROGRAM_SUSPENDED)
        }.mapTo(linkedSetOf(), EquityMethodologyCandidate::assetId)
        if (removed.isEmpty()) return null
        return EquityMethodologyRemovalDecision(
            effectiveDate = SchdDividend100Calendar.firstUsTradingDateOfNextMonth(input.observationDate),
            removedAssetIds = removed,
        )
    }

    private fun requireOfficial(condition: Boolean, rule: String) =
        require(condition) { "Unsupported equity methodology: the SCHD policy requires $rule." }
    private fun decimal(candidate: EquityMethodologyCandidate, id: String) = candidate.signals.requireDecimal(id)
    private fun integer(candidate: EquityMethodologyCandidate, id: String) = candidate.signals.requireInteger(id)
    private fun targetCount(p: EquityMethodologyProfile) = p.parameters.integers.getValue("targetConstituentCount")
    private fun minDividendPaymentYears(p: EquityMethodologyProfile) = p.parameters.integers.getValue("minDividendPaymentYears")
    private fun incumbentRankBuffer(p: EquityMethodologyProfile) = p.parameters.integers.getValue("incumbentRankBuffer")
    private fun dailyDelay(p: EquityMethodologyProfile) = p.parameters.integers.getValue("dailyCapReweightDelayTradingDays")
    private fun minFloatMarketCap(p: EquityMethodologyProfile) = p.parameters.decimals.getValue("minFloatMarketCap")
    private fun minAverageDailyValueTraded(p: EquityMethodologyProfile) = p.parameters.decimals.getValue("minAverageDailyValueTraded")
    private fun eligibleYieldFraction(p: EquityMethodologyProfile) = p.parameters.decimals.getValue("eligibleYieldFraction")
    private fun individualWeightCap(p: EquityMethodologyProfile) = p.parameters.decimals.getValue("individualWeightCap")
    private fun sectorWeightCap(p: EquityMethodologyProfile) = p.parameters.decimals.getValue("sectorWeightCap")
    private fun dailyWeightThreshold(p: EquityMethodologyProfile) = p.parameters.decimals.getValue("dailyWeightThreshold")
    private fun dailyAggregateLimit(p: EquityMethodologyProfile) = p.parameters.decimals.getValue("dailyAggregateWeightLimit")

    private const val EPSILON = 1e-12
    private val CANONICAL_BENCHMARK_REF = BenchmarkRef("spdj-dow-jones-us-dividend-100", 1)
    private val INTEGER_PARAMETER_KEYS = setOf(
        "annualReconstitutionMonth", "dailyCapReweightDelayTradingDays", "incumbentRankBuffer",
        "minDividendPaymentYears", "targetConstituentCount",
    )
    private val DECIMAL_PARAMETER_KEYS = setOf(
        "dailyAggregateWeightLimit", "dailyWeightThreshold", "eligibleYieldFraction",
        "individualWeightCap", "minAverageDailyValueTraded", "minFloatMarketCap", "sectorWeightCap",
    )
    private val INTEGER_SET_PARAMETER_KEYS = setOf("rebalanceMonths")
}
