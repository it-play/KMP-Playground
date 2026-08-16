package com.amond.kmpbook.domain.methodology.builtin

import com.amond.kmpbook.domain.methodology.EquityMethodologyCandidate
import com.amond.kmpbook.domain.methodology.EquityMethodologyCorporateActionDecision
import com.amond.kmpbook.domain.methodology.EquityMethodologyCorporateActionInput
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
import com.amond.kmpbook.domain.model.fund.EquityMethodologyDecisionModel
import com.amond.kmpbook.domain.model.fund.EquityMethodologyProfile
import com.amond.kmpbook.domain.model.fund.FundReferenceUniverse
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioCorporateActionConsiderationKind
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioCorporateActionKind
import com.amond.kmpbook.domain.model.methodology.EquityMethodologyRef
import kotlin.math.abs
import kotlin.math.ceil
import kotlinx.datetime.LocalDate

/** Executable v2 proxy for the public Dow Jones Korea Dividend 30 methodology. */
internal object DowJonesKoreaDividend30Policy : EquityMethodologyPolicy {
    override val schedule = DowJonesKoreaDividend30Schedule

    override val requiredDecimalSignalIds: Set<String> = setOf(
        StandardEquityMethodologySignalIds.FLOAT_MARKET_CAP,
        StandardEquityMethodologySignalIds.TOTAL_COMPANY_MARKET_CAP,
        StandardEquityMethodologySignalIds.AVERAGE_DAILY_VALUE_TRADED,
        StandardEquityMethodologySignalIds.INDICATED_DIVIDEND_YIELD,
        StandardEquityMethodologySignalIds.FREE_CASH_FLOW_TO_DEBT,
        StandardEquityMethodologySignalIds.RETURN_ON_EQUITY,
        StandardEquityMethodologySignalIds.FIVE_YEAR_DIVIDEND_GROWTH,
    )
    override val requiredIntegerSignalIds: Set<String> = setOf(
        StandardEquityMethodologySignalIds.DIVIDEND_PAYMENT_YEARS,
    )
    override val requiredBooleanSignalIds: Set<String> = setOf(
        StandardEquityMethodologySignalIds.ZERO_TOTAL_DEBT,
        StandardEquityMethodologySignalIds.NEGATIVE_BOOK_VALUE_PER_SHARE,
        StandardEquityMethodologySignalIds.SCHEDULED_DIVIDEND_PAYMENT_OMITTED,
        StandardEquityMethodologySignalIds.DIVIDEND_PROGRAM_CEASED_INDEFINITELY,
    )

    override fun validate(definition: BenchmarkDefinition, profile: EquityMethodologyProfile) {
        requireCanonical(definition.ref == CANONICAL_BENCHMARK_REF, "the canonical benchmark identity")
        requireCanonical(
            profile.methodologyRef == EquityMethodologyRef.DOW_JONES_KOREA_DIVIDEND_30_V2,
            "the built-in Dow Jones Korea Dividend 30 registration",
        )
        requireCanonical(
            profile.referenceUniverse == FundReferenceUniverse.KOREA_BROAD_EQUITY,
            "the Korea broad-equity reference universe",
        )
        requireCanonical(
            profile.decisionModel == EquityMethodologyDecisionModel.DISCRETIONARY_PROXY,
            "an explicit discretionary-proxy decision model",
        )
        requireCanonical(
            profile.modelAssumptionId == MODEL_ASSUMPTION_ID,
            "the versioned Korean index-administration proxy assumption",
        )
        profile.parameters.requireExactKeys(
            integerKeys = INTEGER_PARAMETER_KEYS,
            decimalKeys = DECIMAL_PARAMETER_KEYS,
            textKeys = TEXT_PARAMETER_KEYS,
            integerSetKeys = INTEGER_SET_PARAMETER_KEYS,
        )
        requireCanonical(targetCount(profile) == 30, "30 constituents")
        requireCanonical(minimumDividendPaymentYears(profile) == 10, "10 dividend-payment years")
        requireCanonical(incumbentRankBuffer(profile) == 40, "the top-40 incumbent buffer")
        requireCanonical(
            monthlyDividendReviewCutoffDay(profile) == 21,
            "the monthly dividend-review cutoff",
        )
        requireCanonical(
            februaryDividendReviewCutoffDay(profile) == 18,
            "the February dividend-review cutoff",
        )
        requireCanonical(corporateActionNoticeTradingDays(profile) == 2, "two trading days' notice")
        requireCanonical(
            abs(minimumTotalCompanyMarketCap(profile) - 1_000_000_000_000.0) <= EPSILON,
            "a KRW 1 trillion total company market cap",
        )
        requireCanonical(
            abs(minimumAverageDailyValueTraded(profile) - 1_000_000_000.0) <= EPSILON,
            "KRW 1 billion three-month ADVT",
        )
        requireCanonical(
            abs(eligibleYieldFraction(profile) - 0.5) <= EPSILON,
            "the top-half indicated-dividend-yield screen",
        )
        requireCanonical(abs(individualWeightCap(profile) - 0.04) <= EPSILON, "the 4% constituent cap")
        requireCanonical(
            DowJonesKoreaDividend30Schedule.reconstitutionMonths(profile) == setOf(6, 12),
            "June and December reconstitutions",
        )
        requireCanonical(
            DowJonesKoreaDividend30Schedule.reweightMonths(profile) == setOf(3, 6, 9, 12),
            "March, June, September and December reweights",
        )
        requireCanonical(
            profile.parameters.texts.getValue(THRESHOLD_POLICY) == FROZEN_THRESHOLD_POLICY,
            "the frozen August 2026 methodology thresholds",
        )
        requireCanonical(
            schedule.initialScheduledAction(profile).effectiveDate == profile.effectiveFrom,
            "bootstrap at effectiveFrom",
        )
    }

    override fun portfolioConstraints(profile: EquityMethodologyProfile) =
        EquityMethodologyPortfolioConstraints(
            minimumConstituentCount = 1,
            maximumConstituentCount = targetCount(profile) + MAX_TEMPORARY_SPIN_OFF_CONSTITUENTS,
            scheduledSelectionCount = targetCount(profile),
            individualWeightCap = individualWeightCap(profile),
        )

    override fun select(input: EquityMethodologySelectionInput): List<EquityMethodologySelection> {
        val eligible = dividendYieldEligibleCandidates(input.profile, input.candidates)
        require(eligible.size >= targetCount(input.profile)) {
            "The Dow Jones Korea Dividend 30 yield screen produced too few candidates."
        }
        fun descendingRanks(signalId: String): Map<String, Int> =
            StandardEquityMethodologyComponents.descendingOrdinalRanks(
                eligible.associate { candidate -> candidate.assetId to decimal(candidate, signalId) },
            )

        fun groupedRanks(
            preferredGroup: (EquityMethodologyCandidate) -> Boolean,
            signalId: String,
        ): Map<String, Int> = eligible.sortedWith(
            compareByDescending<EquityMethodologyCandidate>(preferredGroup)
                .thenByDescending { candidate -> decimal(candidate, signalId) }
                .thenBy(EquityMethodologyCandidate::assetId),
        ).withIndex().associate { (index, candidate) -> candidate.assetId to index + 1 }

        val freeCashFlowRanks = groupedRanks(
            preferredGroup = { candidate ->
                candidate.signals.requireBoolean(StandardEquityMethodologySignalIds.ZERO_TOTAL_DEBT)
            },
            signalId = StandardEquityMethodologySignalIds.FREE_CASH_FLOW_TO_DEBT,
        )
        val returnOnEquityRanks = groupedRanks(
            preferredGroup = { candidate ->
                !candidate.signals.requireBoolean(
                    StandardEquityMethodologySignalIds.NEGATIVE_BOOK_VALUE_PER_SHARE,
                )
            },
            signalId = StandardEquityMethodologySignalIds.RETURN_ON_EQUITY,
        )
        val yieldRanks = descendingRanks(StandardEquityMethodologySignalIds.INDICATED_DIVIDEND_YIELD)
        val dividendGrowthRanks = descendingRanks(
            StandardEquityMethodologySignalIds.FIVE_YEAR_DIVIDEND_GROWTH,
        )
        val candidatesById = eligible.associateBy(EquityMethodologyCandidate::assetId)
        val rankedAssetIds = eligible.map(EquityMethodologyCandidate::assetId).sortedWith(
            compareBy<String> { assetId ->
                freeCashFlowRanks.getValue(assetId) + returnOnEquityRanks.getValue(assetId) +
                    yieldRanks.getValue(assetId) + dividendGrowthRanks.getValue(assetId)
            }.thenByDescending { assetId ->
                decimal(
                    candidatesById.getValue(assetId),
                    StandardEquityMethodologySignalIds.INDICATED_DIVIDEND_YIELD,
                )
            }.thenBy { assetId -> assetId },
        )
        val rankByAssetId = rankedAssetIds.withIndex().associate { (index, assetId) ->
            assetId to index + 1
        }
        return StandardEquityMethodologyComponents.bufferedSelection(
            rankedAssetIds = rankedAssetIds,
            incumbentAssetIds = input.incumbentAssetIds,
            targetCount = targetCount(input.profile),
            incumbentRankBuffer = incumbentRankBuffer(input.profile),
        ).map { assetId -> EquityMethodologySelection(assetId, rankByAssetId.getValue(assetId)) }
            .sortedBy(EquityMethodologySelection::rank)
    }

    override fun targetWeights(input: EquityMethodologyWeightingInput): Map<String, Double> =
        StandardEquityMethodologyComponents.cappedGroupWeights(
            rawValues = input.referenceMarketValues,
            groupIds = input.referenceMarketValues.keys.associateWith { ALL_CONSTITUENTS_GROUP },
            groupOrder = listOf(ALL_CONSTITUENTS_GROUP),
            individualCap = individualWeightCap(input.profile),
            groupCap = 1.0,
        )

    override fun nextExtraordinaryRemovalReviewDate(
        profile: EquityMethodologyProfile,
        afterExclusive: LocalDate,
    ): LocalDate? = (afterExclusive.year..afterExclusive.year + 2).asSequence()
        .flatMap { year -> (1..12).asSequence().map { month -> year to month } }
        .map { (year, month) ->
            val cutoffDay = if (month == 2) {
                februaryDividendReviewCutoffDay(profile)
            } else {
                monthlyDividendReviewCutoffDay(profile)
            }
            KoreaEquityMethodologyCalendar.firstKrxTradingDateOnOrAfter(
                LocalDate(year, month, cutoffDay),
            )
        }
        .firstOrNull { reviewDate -> reviewDate > afterExclusive }

    override fun extraordinaryRemovalDecision(
        input: EquityMethodologyRemovalInput,
    ): EquityMethodologyRemovalDecision? {
        val removedAssetIds = input.constituents.filter { candidate ->
            candidate.signals.requireBoolean(
                StandardEquityMethodologySignalIds.SCHEDULED_DIVIDEND_PAYMENT_OMITTED,
            ) || candidate.signals.requireBoolean(
                StandardEquityMethodologySignalIds.DIVIDEND_PROGRAM_CEASED_INDEFINITELY,
            )
        }.mapTo(linkedSetOf(), EquityMethodologyCandidate::assetId)
        if (removedAssetIds.isEmpty()) return null
        return EquityMethodologyRemovalDecision(
            effectiveDate = KoreaEquityMethodologyCalendar.firstKrxTradingDateOfNextMonth(
                input.observationDate,
            ),
            removedAssetIds = removedAssetIds,
        )
    }

    override fun corporateActionNoticeTradingDays(
        profile: EquityMethodologyProfile,
        kind: ReferencePortfolioCorporateActionKind,
    ): Int = corporateActionNoticeTradingDays(profile)

    override fun corporateActionDecision(
        input: EquityMethodologyCorporateActionInput,
    ): EquityMethodologyCorporateActionDecision {
        requireCanonical(schedule.isTradingDate(input.event.effectiveDate), "a KRX trading-day effective date")
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
        val acquirerAssetId = requireNotNull(event.secondaryAssetId)
        val constituentAssetIds = input.currentConstituents.mapTo(hashSetOf()) { candidate ->
            candidate.assetId
        }
        if (acquirerAssetId !in constituentAssetIds) {
            return EquityMethodologyCorporateActionDecision(
                removedAssetIds = setOf(event.primaryAssetId),
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
            removedAssetIds = setOf(event.primaryAssetId),
            survivingAcquirerAssetId = acquirerAssetId,
            transferredValueFraction = transferredValueFraction,
        )
    }

    private fun spinOffDecision(
        input: EquityMethodologyCorporateActionInput,
    ): EquityMethodologyCorporateActionDecision {
        val event = input.event
        val childAssetId = requireNotNull(event.secondaryAssetId)
        val followUpDate = requireNotNull(event.followUpEffectiveDate)
        requireCanonical(
            input.currentConstituents.size < portfolioConstraints(input.profile).maximumConstituentCount,
            "at most one temporary spin-off constituent",
        )
        requireCanonical(schedule.isTradingDate(followUpDate), "a KRX trading-day spin-off removal")
        requireCanonical(
            followUpDate >= schedule.addTradingDays(event.effectiveDate, 1),
            "at least one KRX trading day for a temporary spin-off constituent",
        )
        return EquityMethodologyCorporateActionDecision(
            addedAssetIds = setOf(childAssetId),
            transferredValueFraction = event.valueTransferFraction,
            followUpRemovalDate = followUpDate,
        )
    }

    private fun dividendYieldEligibleCandidates(
        profile: EquityMethodologyProfile,
        candidates: List<EquityMethodologyCandidate>,
    ): List<EquityMethodologyCandidate> {
        val screened = candidates.filter { candidate -> passesEligibilityScreens(profile, candidate) }
        val eligibleCount = ceil(screened.size * eligibleYieldFraction(profile)).toInt()
            .coerceAtMost(screened.size)
        return screened.sortedWith(
            compareByDescending<EquityMethodologyCandidate> { candidate ->
                decimal(candidate, StandardEquityMethodologySignalIds.INDICATED_DIVIDEND_YIELD)
            }.thenBy(EquityMethodologyCandidate::assetId),
        ).take(eligibleCount)
    }

    private fun passesEligibilityScreens(
        profile: EquityMethodologyProfile,
        candidate: EquityMethodologyCandidate,
    ): Boolean = candidate.signals.requireInteger(
        StandardEquityMethodologySignalIds.DIVIDEND_PAYMENT_YEARS,
    ) >= minimumDividendPaymentYears(profile) &&
        decimal(candidate, StandardEquityMethodologySignalIds.TOTAL_COMPANY_MARKET_CAP) >=
        minimumTotalCompanyMarketCap(profile) &&
        decimal(candidate, StandardEquityMethodologySignalIds.AVERAGE_DAILY_VALUE_TRADED) >=
        minimumAverageDailyValueTraded(profile)

    private fun requireCanonical(condition: Boolean, rule: String) =
        require(condition) {
            "Unsupported equity methodology: Dow Jones Korea Dividend 30 v2 requires $rule."
        }

    private fun decimal(candidate: EquityMethodologyCandidate, signalId: String): Double =
        candidate.signals.requireDecimal(signalId)

    private fun targetCount(profile: EquityMethodologyProfile): Int =
        profile.parameters.integers.getValue("targetConstituentCount")

    private fun minimumDividendPaymentYears(profile: EquityMethodologyProfile): Int =
        profile.parameters.integers.getValue("minimumDividendPaymentYears")

    private fun incumbentRankBuffer(profile: EquityMethodologyProfile): Int =
        profile.parameters.integers.getValue("incumbentRankBuffer")

    private fun monthlyDividendReviewCutoffDay(profile: EquityMethodologyProfile): Int =
        profile.parameters.integers.getValue("monthlyDividendReviewCutoffDay")

    private fun februaryDividendReviewCutoffDay(profile: EquityMethodologyProfile): Int =
        profile.parameters.integers.getValue("februaryDividendReviewCutoffDay")

    private fun corporateActionNoticeTradingDays(profile: EquityMethodologyProfile): Int =
        profile.parameters.integers.getValue("corporateActionNoticeTradingDays")

    private fun minimumTotalCompanyMarketCap(profile: EquityMethodologyProfile): Double =
        profile.parameters.decimals.getValue("minimumTotalCompanyMarketCap")

    private fun minimumAverageDailyValueTraded(profile: EquityMethodologyProfile): Double =
        profile.parameters.decimals.getValue("minimumAverageDailyValueTraded")

    private fun eligibleYieldFraction(profile: EquityMethodologyProfile): Double =
        profile.parameters.decimals.getValue("eligibleYieldFraction")

    private fun individualWeightCap(profile: EquityMethodologyProfile): Double =
        profile.parameters.decimals.getValue("individualWeightCap")

    private const val EPSILON: Double = 1e-12
    private const val MAX_TEMPORARY_SPIN_OFF_CONSTITUENTS: Int = 1
    private const val ALL_CONSTITUENTS_GROUP: String = "all"
    private const val MODEL_ASSUMPTION_ID: String =
        "dj-korea-dividend-30-committee-proxy-2026-08-v1"
    private const val THRESHOLD_POLICY: String = "thresholdPolicy"
    private const val FROZEN_THRESHOLD_POLICY: String = "FROZEN_AUGUST_2026_METHODOLOGY"
    private val CANONICAL_BENCHMARK_REF = BenchmarkRef(
        benchmarkId = "spdj.dow-jones-korea-dividend-30",
        version = 2,
    )
    private val INTEGER_PARAMETER_KEYS = setOf(
        "corporateActionNoticeTradingDays",
        "februaryDividendReviewCutoffDay",
        "incumbentRankBuffer",
        "minimumDividendPaymentYears",
        "monthlyDividendReviewCutoffDay",
        "targetConstituentCount",
    )
    private val DECIMAL_PARAMETER_KEYS = setOf(
        "eligibleYieldFraction",
        "individualWeightCap",
        "minimumAverageDailyValueTraded",
        "minimumTotalCompanyMarketCap",
    )
    private val TEXT_PARAMETER_KEYS = setOf(THRESHOLD_POLICY)
    private val INTEGER_SET_PARAMETER_KEYS = setOf("reconstitutionMonths", "reweightMonths")
}
