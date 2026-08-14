package com.amond.kmpbook.domain.methodology.builtin

import com.amond.kmpbook.domain.methodology.EquityMethodologyCandidate
import com.amond.kmpbook.domain.methodology.EquityMethodologyCorporateActionDecision
import com.amond.kmpbook.domain.methodology.EquityMethodologyCorporateActionInput
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
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioCorporateActionConsiderationKind
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioCorporateActionKind
import com.amond.kmpbook.domain.model.methodology.EquityMethodologyRef
import kotlin.math.abs
import kotlin.math.ceil
import kotlinx.datetime.LocalDate

/** Executable v2 policy for the public Dow Jones U.S. Dividend 100/SCHD rules. */
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
        add(StandardEquityMethodologySignalIds.GICS_CLASSIFICATION_CODE)
        add(StandardEquityMethodologySignalIds.DIVIDEND_PAYMENT_YEARS)
    }
    override val requiredBooleanSignalIds: Set<String> = buildSet {
        add(StandardEquityMethodologySignalIds.ZERO_TOTAL_DEBT)
        add(StandardEquityMethodologySignalIds.NEGATIVE_BOOK_VALUE_PER_SHARE)
        add(StandardEquityMethodologySignalIds.SCHEDULED_DIVIDEND_PAYMENT_OMITTED)
        add(StandardEquityMethodologySignalIds.DIVIDEND_PROGRAM_CEASED_INDEFINITELY)
    }

    override fun validate(definition: BenchmarkDefinition, profile: EquityMethodologyProfile) {
        requireOfficial(definition.ref == CANONICAL_BENCHMARK_REF, "the canonical benchmark identity")
        requireOfficial(
            profile.methodologyRef == EquityMethodologyRef.SCHD_DIVIDEND_100_V2,
            "the built-in SCHD methodology registration",
        )
        requireOfficial(
            profile.referenceUniverse == FundReferenceUniverse.US_BROAD_EQUITY,
            "the US broad-equity reference universe",
        )
        profile.parameters.requireExactKeys(
            integerKeys = INTEGER_PARAMETER_KEYS,
            decimalKeys = DECIMAL_PARAMETER_KEYS,
            textKeys = TEXT_PARAMETER_KEYS,
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
        requireOfficial(excludedGicsClassificationCodes(profile) == setOf(6010, 402040), "the two REIT classifications")
        requireOfficial(monthlyDividendReviewCutoffDay(profile) == 21, "the monthly information cutoff")
        requireOfficial(februaryDividendReviewCutoffDay(profile) == 18, "the February information cutoff")
        requireOfficial(
            profile.parameters.texts.getValue(CONSTITUENT_MERGER_RULE_EFFECTIVE_DATE) == "2026-04-30",
            "the current constituent-merger rule effective date",
        )
        requireOfficial(SchdDividend100Schedule.annualReconstitutionMonth(profile) == 3, "March reconstitution")
        requireOfficial(SchdDividend100Schedule.rebalanceMonths(profile) == setOf(3, 6, 9, 12), "quarterly months")
        require(schedule.initialScheduledAction(profile).effectiveDate == profile.effectiveFrom)
    }

    override fun portfolioConstraints(profile: EquityMethodologyProfile) =
        EquityMethodologyPortfolioConstraints(
            minimumConstituentCount = 1,
            maximumConstituentCount = targetCount(profile) + MAX_TEMPORARY_SPIN_OFF_CONSTITUENTS,
            scheduledSelectionCount = targetCount(profile),
            individualWeightCap = individualWeightCap(profile),
            sectorWeightCap = sectorWeightCap(profile),
        )

    override fun select(input: EquityMethodologySelectionInput): List<EquityMethodologySelection> {
        val profile = input.profile
        val eligible = dividendYieldEligibleCandidates(profile, input.candidates)
        require(eligible.size >= targetCount(profile)) { "The dividend-yield screen produced too few candidates." }
        val components = StandardEquityMethodologyComponents
        fun ranks(signalId: String) = components.descendingOrdinalRanks(
            eligible.associate { it.assetId to decimal(it, signalId) },
        )
        fun groupedRanks(
            firstGroup: (EquityMethodologyCandidate) -> Boolean,
            signalId: String,
        ): Map<String, Int> = eligible.sortedWith(
            compareByDescending<EquityMethodologyCandidate>(firstGroup)
                .thenByDescending { candidate -> decimal(candidate, signalId) }
                .thenBy(EquityMethodologyCandidate::assetId),
        ).withIndex().associate { (index, candidate) -> candidate.assetId to index + 1 }
        val fcfRanks = groupedRanks(
            firstGroup = { candidate ->
                candidate.signals.requireBoolean(StandardEquityMethodologySignalIds.ZERO_TOTAL_DEBT)
            },
            signalId = StandardEquityMethodologySignalIds.FREE_CASH_FLOW_TO_DEBT,
        )
        val roeRanks = groupedRanks(
            firstGroup = { candidate ->
                !candidate.signals.requireBoolean(StandardEquityMethodologySignalIds.NEGATIVE_BOOK_VALUE_PER_SHARE)
            },
            signalId = StandardEquityMethodologySignalIds.RETURN_ON_EQUITY,
        )
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
        return schedule.addTradingDays(input.observationDate, dailyDelay(profile))
    }

    override fun nextExtraordinaryRemovalReviewDate(
        profile: EquityMethodologyProfile,
        afterExclusive: LocalDate,
    ): LocalDate? = (afterExclusive.year..afterExclusive.year + 2).asSequence()
        .flatMap { year -> (1..12).asSequence().map { year to it } }
        .map { (year, month) ->
            val cutoffDay = if (month == 2) {
                februaryDividendReviewCutoffDay(profile)
            } else {
                monthlyDividendReviewCutoffDay(profile)
            }
            SchdDividend100Calendar.firstUsTradingDateOnOrAfter(LocalDate(year, month, cutoffDay))
        }
        .firstOrNull { it > afterExclusive }

    override fun extraordinaryRemovalDecision(input: EquityMethodologyRemovalInput): EquityMethodologyRemovalDecision? {
        val removed = input.constituents.filter { candidate ->
            candidate.signals.requireBoolean(
                StandardEquityMethodologySignalIds.SCHEDULED_DIVIDEND_PAYMENT_OMITTED,
            ) || candidate.signals.requireBoolean(
                StandardEquityMethodologySignalIds.DIVIDEND_PROGRAM_CEASED_INDEFINITELY,
            )
        }.mapTo(linkedSetOf(), EquityMethodologyCandidate::assetId)
        if (removed.isEmpty()) return null
        return EquityMethodologyRemovalDecision(
            effectiveDate = SchdDividend100Calendar.firstUsTradingDateOfNextMonth(input.observationDate),
            removedAssetIds = removed,
        )
    }

    override fun corporateActionDecision(
        input: EquityMethodologyCorporateActionInput,
    ): EquityMethodologyCorporateActionDecision {
        requireOfficial(schedule.isTradingDate(input.event.effectiveDate), "a regular-trading-day effective date")
        return when (input.event.kind) {
            ReferencePortfolioCorporateActionKind.MERGER -> mergerDecision(input)
            ReferencePortfolioCorporateActionKind.SPIN_OFF -> spinOffDecision(input)
            ReferencePortfolioCorporateActionKind.TERMINAL_REMOVAL ->
                EquityMethodologyCorporateActionDecision(
                    removedAssetIds = setOf(input.event.primaryAssetId),
                )
        }
    }

    private fun mergerDecision(
        input: EquityMethodologyCorporateActionInput,
    ): EquityMethodologyCorporateActionDecision {
        val event = input.event
        val targetId = event.primaryAssetId
        val acquirerId = requireNotNull(event.secondaryAssetId)
        val constituentIds = input.currentConstituents.mapTo(hashSetOf(), EquityMethodologyCandidate::assetId)
        if (acquirerId !in constituentIds) {
            return EquityMethodologyCorporateActionDecision(removedAssetIds = setOf(targetId))
        }
        if (event.effectiveDate < constituentMergerRuleEffectiveDate(input.profile)) {
            return EquityMethodologyCorporateActionDecision(
                removedAssetIds = setOf(targetId),
                survivingAcquirerAssetId = acquirerId,
            )
        }
        val acquirerIsEligible = dividendYieldEligibleCandidates(
            input.profile,
            input.universeCandidates,
        ).any { candidate -> candidate.assetId == acquirerId }
        if (!acquirerIsEligible) {
            return EquityMethodologyCorporateActionDecision(
                removedAssetIds = setOf(targetId, acquirerId),
            )
        }
        val transferredValueFraction = when (event.considerationKind) {
            ReferencePortfolioCorporateActionConsiderationKind.STOCK,
            ReferencePortfolioCorporateActionConsiderationKind.MIXED,
            -> event.valueTransferFraction
            ReferencePortfolioCorporateActionConsiderationKind.CASH -> 0.0
            ReferencePortfolioCorporateActionConsiderationKind.NONE ->
                error("A canonical merger cannot have NONE consideration.")
        }
        return EquityMethodologyCorporateActionDecision(
            removedAssetIds = setOf(targetId),
            survivingAcquirerAssetId = acquirerId,
            transferredValueFraction = transferredValueFraction,
        )
    }

    private fun spinOffDecision(
        input: EquityMethodologyCorporateActionInput,
    ): EquityMethodologyCorporateActionDecision {
        val event = input.event
        val childId = requireNotNull(event.secondaryAssetId)
        val followUpDate = requireNotNull(event.followUpEffectiveDate)
        requireOfficial(
            input.currentConstituents.size < portfolioConstraints(input.profile).maximumConstituentCount,
            "at most one temporary spin-off constituent",
        )
        requireOfficial(schedule.isTradingDate(followUpDate), "a regular-trading-day spin-off removal")
        requireOfficial(
            followUpDate >= schedule.addTradingDays(event.effectiveDate, 1),
            "at least one regular trading day for a temporary spin-off constituent",
        )
        return EquityMethodologyCorporateActionDecision(
            addedAssetIds = setOf(childId),
            transferredValueFraction = event.valueTransferFraction,
            followUpRemovalDate = followUpDate,
        )
    }

    private fun dividendYieldEligibleCandidates(
        profile: EquityMethodologyProfile,
        candidates: List<EquityMethodologyCandidate>,
    ): List<EquityMethodologyCandidate> {
        val screened = candidates.filter { candidate -> passesSelectionScreens(profile, candidate) }
        val eligibleCount = ceil(screened.size * eligibleYieldFraction(profile)).toInt().coerceAtMost(screened.size)
        return screened.sortedWith(
            compareByDescending<EquityMethodologyCandidate> {
                decimal(it, StandardEquityMethodologySignalIds.INDICATED_DIVIDEND_YIELD)
            }.thenBy(EquityMethodologyCandidate::assetId),
        ).take(eligibleCount)
    }

    private fun passesSelectionScreens(
        profile: EquityMethodologyProfile,
        candidate: EquityMethodologyCandidate,
    ): Boolean = integer(candidate, StandardEquityMethodologySignalIds.DIVIDEND_PAYMENT_YEARS) >=
        minDividendPaymentYears(profile) &&
        decimal(candidate, StandardEquityMethodologySignalIds.FLOAT_MARKET_CAP) >= minFloatMarketCap(profile) &&
        decimal(candidate, StandardEquityMethodologySignalIds.AVERAGE_DAILY_VALUE_TRADED) >=
        minAverageDailyValueTraded(profile) &&
        integer(candidate, StandardEquityMethodologySignalIds.GICS_CLASSIFICATION_CODE) !in
        excludedGicsClassificationCodes(profile)

    private fun requireOfficial(condition: Boolean, rule: String) =
        require(condition) { "Unsupported equity methodology: the SCHD policy requires $rule." }
    private fun decimal(candidate: EquityMethodologyCandidate, id: String) = candidate.signals.requireDecimal(id)
    private fun integer(candidate: EquityMethodologyCandidate, id: String) = candidate.signals.requireInteger(id)
    private fun targetCount(p: EquityMethodologyProfile) = p.parameters.integers.getValue("targetConstituentCount")
    private fun minDividendPaymentYears(p: EquityMethodologyProfile) = p.parameters.integers.getValue("minDividendPaymentYears")
    private fun incumbentRankBuffer(p: EquityMethodologyProfile) = p.parameters.integers.getValue("incumbentRankBuffer")
    private fun dailyDelay(p: EquityMethodologyProfile) = p.parameters.integers.getValue("dailyCapReweightDelayTradingDays")
    private fun monthlyDividendReviewCutoffDay(p: EquityMethodologyProfile) =
        p.parameters.integers.getValue("monthlyDividendReviewCutoffDay")
    private fun februaryDividendReviewCutoffDay(p: EquityMethodologyProfile) =
        p.parameters.integers.getValue("februaryDividendReviewCutoffDay")
    private fun minFloatMarketCap(p: EquityMethodologyProfile) = p.parameters.decimals.getValue("minFloatMarketCap")
    private fun minAverageDailyValueTraded(p: EquityMethodologyProfile) = p.parameters.decimals.getValue("minAverageDailyValueTraded")
    private fun eligibleYieldFraction(p: EquityMethodologyProfile) = p.parameters.decimals.getValue("eligibleYieldFraction")
    private fun individualWeightCap(p: EquityMethodologyProfile) = p.parameters.decimals.getValue("individualWeightCap")
    private fun sectorWeightCap(p: EquityMethodologyProfile) = p.parameters.decimals.getValue("sectorWeightCap")
    private fun dailyWeightThreshold(p: EquityMethodologyProfile) = p.parameters.decimals.getValue("dailyWeightThreshold")
    private fun dailyAggregateLimit(p: EquityMethodologyProfile) = p.parameters.decimals.getValue("dailyAggregateWeightLimit")
    private fun excludedGicsClassificationCodes(p: EquityMethodologyProfile) =
        p.parameters.integerSets.getValue("excludedGicsClassificationCodes")
    private fun constituentMergerRuleEffectiveDate(p: EquityMethodologyProfile) =
        LocalDate.parse(p.parameters.texts.getValue(CONSTITUENT_MERGER_RULE_EFFECTIVE_DATE))

    private const val EPSILON = 1e-12
    private const val MAX_TEMPORARY_SPIN_OFF_CONSTITUENTS = 1
    private const val CONSTITUENT_MERGER_RULE_EFFECTIVE_DATE = "constituentMergerRuleEffectiveDate"
    private val CANONICAL_BENCHMARK_REF = BenchmarkRef("spdj-dow-jones-us-dividend-100", 2)
    private val INTEGER_PARAMETER_KEYS = setOf(
        "annualReconstitutionMonth", "dailyCapReweightDelayTradingDays", "incumbentRankBuffer",
        "minDividendPaymentYears", "targetConstituentCount", "monthlyDividendReviewCutoffDay",
        "februaryDividendReviewCutoffDay",
    )
    private val DECIMAL_PARAMETER_KEYS = setOf(
        "dailyAggregateWeightLimit", "dailyWeightThreshold", "eligibleYieldFraction",
        "individualWeightCap", "minAverageDailyValueTraded", "minFloatMarketCap", "sectorWeightCap",
    )
    private val INTEGER_SET_PARAMETER_KEYS = setOf("rebalanceMonths", "excludedGicsClassificationCodes")
    private val TEXT_PARAMETER_KEYS = setOf(CONSTITUENT_MERGER_RULE_EFFECTIVE_DATE)
}
