package com.amond.kmpbook.domain.methodology.builtin

import com.amond.kmpbook.domain.methodology.EquityMethodologyCandidate
import com.amond.kmpbook.domain.methodology.EquityMethodologyCorporateActionDecision
import com.amond.kmpbook.domain.methodology.EquityMethodologyCorporateActionInput
import com.amond.kmpbook.domain.methodology.EquityMethodologyPolicy
import com.amond.kmpbook.domain.methodology.EquityMethodologyPortfolioConstraints
import com.amond.kmpbook.domain.methodology.EquityMethodologySelection
import com.amond.kmpbook.domain.methodology.EquityMethodologySelectionInput
import com.amond.kmpbook.domain.methodology.EquityMethodologyReconstitutionTransitionStep
import com.amond.kmpbook.domain.methodology.EquityMethodologyScheduledAction
import com.amond.kmpbook.domain.methodology.EquityMethodologyWeightingInput
import com.amond.kmpbook.domain.methodology.StandardEquityMethodologyComponents
import com.amond.kmpbook.domain.methodology.StandardEquityMethodologySignalIds
import com.amond.kmpbook.domain.model.fund.BenchmarkDefinition
import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import com.amond.kmpbook.domain.model.fund.EquityMethodologyDecisionModel
import com.amond.kmpbook.domain.model.fund.EquityMethodologyProfile
import com.amond.kmpbook.domain.model.fund.FundReferenceUniverse
import com.amond.kmpbook.domain.model.fund.MethodologyEquitySector
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioCorporateActionConsiderationKind
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioCorporateActionKind
import com.amond.kmpbook.domain.model.methodology.EquityMethodologyRef
import kotlin.math.abs
import kotlin.math.ceil

/** Executable May 2026 KRX rules for KOSPI 200 Financial High Dividend TOP10. */
internal object KodexFinancialHighDividendTop10Policy : EquityMethodologyPolicy {
    override val schedule = KodexFinancialHighDividendTop10Schedule
    override val hasRecurringScheduledReweight: Boolean = false

    override val requiredDecimalSignalIds: Set<String> = setOf(
        StandardEquityMethodologySignalIds.THREE_YEAR_AVERAGE_DIVIDEND_PAYOUT_RATIO,
        StandardEquityMethodologySignalIds.THREE_YEAR_AVERAGE_RETURN_ON_EQUITY,
        StandardEquityMethodologySignalIds.ONE_MONTH_AVERAGE_DAILY_VALUE_TRADED,
        StandardEquityMethodologySignalIds.ONE_MONTH_AVERAGE_PRICE_TO_BOOK_RATIO,
        StandardEquityMethodologySignalIds.ONE_MONTH_AVERAGE_DIVIDEND_YIELD,
        StandardEquityMethodologySignalIds.ONE_MONTH_AVERAGE_MARKET_CAP,
        StandardEquityMethodologySignalIds.TRAILING_FOUR_QUARTER_TOTAL_CASH_DIVIDENDS,
    )
    override val requiredIntegerSignalIds: Set<String> = setOf(
        StandardEquityMethodologySignalIds.LISTING_AGE_YEARS,
        StandardEquityMethodologySignalIds.DIVIDEND_PAYMENT_YEARS,
    )
    override val requiredBooleanSignalIds: Set<String> = setOf(
        StandardEquityMethodologySignalIds.KOSPI200_FINANCIAL_MEMBER,
    )

    override fun validate(definition: BenchmarkDefinition, profile: EquityMethodologyProfile) {
        requireCanonical(definition.ref == CANONICAL_BENCHMARK_REF, "the canonical benchmark identity")
        requireCanonical(
            profile.methodologyRef == EquityMethodologyRef.KOSPI200_FINANCIAL_HIGH_DIVIDEND_TOP10_V2,
            "the built-in KOSPI 200 Financial High Dividend TOP10 registration",
        )
        requireCanonical(
            profile.referenceUniverse == FundReferenceUniverse.KOREA_BROAD_EQUITY,
            "the Korea broad-equity reference universe",
        )
        requireCanonical(
            profile.decisionModel == EquityMethodologyDecisionModel.RULE_BASED,
            "a rule-based decision model",
        )
        profile.parameters.requireExactKeys(
            integerKeys = INTEGER_PARAMETER_KEYS,
            decimalKeys = DECIMAL_PARAMETER_KEYS,
            textKeys = TEXT_PARAMETER_KEYS,
            integerSetKeys = INTEGER_SET_PARAMETER_KEYS,
        )
        requireCanonical(targetConstituentCount(profile) == 10, "10 constituents")
        requireCanonical(stageTwoCandidateCount(profile) == 13, "the 13-stock dividend-yield shortlist")
        requireCanonical(minimumListingYears(profile) == 3, "at least three listing years")
        requireCanonical(minimumDividendPaymentYears(profile) == 3, "three consecutive dividend years")
        requireCanonical(
            abs(minimumDividendPayoutRatio(profile) - 0.10) <= EPSILON,
            "a strictly greater than 10% three-year average payout ratio",
        )
        requireCanonical(
            abs(maximumDividendPayoutRatio(profile) - 0.70) <= EPSILON,
            "a strictly less than 70% three-year average payout ratio",
        )
        requireCanonical(
            abs(minimumAverageDailyValueTraded(profile) - 4_000_000_000.0) <= EPSILON,
            "KRW 4 billion one-month ADVT",
        )
        requireCanonical(abs(roeEligibleFraction(profile) - 0.90) <= EPSILON, "the top 90% ROE screen")
        requireCanonical(
            abs(maximumPriceToBookRatio(profile) - 1.0) <= EPSILON,
            "a newcomer PBR below 1.0",
        )
        requireCanonical(
            abs(incumbentMaximumPriceToBookRatio(profile) - 1.5) <= EPSILON,
            "the May 2026 incumbent PBR buffer below 1.5",
        )
        requireCanonical(abs(individualWeightCap(profile) - 0.25) <= EPSILON, "the 25% constituent cap")
        requireCanonical(
            KodexFinancialHighDividendTop10Schedule.reconstitutionMonths(profile) == setOf(6, 12),
            "June and December reconstitutions",
        )
        requireCanonical(
            profile.parameters.texts.getValue(THRESHOLD_POLICY) == CURRENT_THRESHOLD_POLICY,
            "the May 2026 KRX threshold policy",
        )
        requireCanonical(
            schedule.initialScheduledAction(profile).effectiveDate == profile.effectiveFrom,
            "bootstrap on a canonical completed transition date",
        )
    }

    override fun portfolioConstraints(profile: EquityMethodologyProfile) =
        EquityMethodologyPortfolioConstraints(
            minimumConstituentCount = ceil(1.0 / individualWeightCap(profile)).toInt(),
            maximumConstituentCount = targetConstituentCount(profile) * 2 +
                MAX_TEMPORARY_SPIN_OFF_CONSTITUENTS,
            scheduledSelectionCount = targetConstituentCount(profile),
            individualWeightCap = individualWeightCap(profile),
        )

    override fun select(input: EquityMethodologySelectionInput): List<EquityMethodologySelection> {
        val profile = input.profile
        val dividendYieldShortlist = dividendYieldShortlist(
            profile = profile,
            candidates = input.candidates,
            incumbentAssetIds = input.incumbentAssetIds,
            contextDate = input.scheduledAction.selectionDate.toString(),
        )
        val selected = dividendYieldShortlist.sortedWith(
            descendingSignalOrder(StandardEquityMethodologySignalIds.ONE_MONTH_AVERAGE_MARKET_CAP),
        ).take(targetConstituentCount(profile))
        return selected.mapIndexed { index, candidate ->
            EquityMethodologySelection(assetId = candidate.assetId, rank = index + 1)
        }
    }

    override fun targetWeights(input: EquityMethodologyWeightingInput): Map<String, Double> {
        val dividendValues = input.selectedCandidates.associate { candidate ->
            candidate.assetId to decimal(
                candidate,
                StandardEquityMethodologySignalIds.TRAILING_FOUR_QUARTER_TOTAL_CASH_DIVIDENDS,
            )
        }
        require(dividendValues.values.all { value -> value > 0.0 }) {
            "Trailing four-quarter cash dividends must be positive for every selected constituent."
        }
        val groupIds = dividendValues.keys.associateWith { ALL_CONSTITUENTS_GROUP }
        return StandardEquityMethodologyComponents.cappedGroupWeights(
            rawValues = dividendValues,
            groupIds = groupIds,
            groupOrder = listOf(ALL_CONSTITUENTS_GROUP),
            individualCap = individualWeightCap(input.profile),
            groupCap = 1.0,
        )
    }

    override fun scheduledReconstitutionTransitionSteps(
        profile: EquityMethodologyProfile,
        action: EquityMethodologyScheduledAction,
    ): List<EquityMethodologyReconstitutionTransitionStep> {
        requireCanonical(
            action.kind == com.amond.kmpbook.domain.model.fund.ReferencePortfolioActionKind
                .SCHEDULED_RECONSTITUTION,
            "a scheduled reconstitution transition",
        )
        val year = action.effectiveDate.year
        val month = action.effectiveDate.month.ordinal + 1
        return listOf(
            EquityMethodologyReconstitutionTransitionStep(
                effectiveDate = KodexFinancialHighDividendTop10Schedule.transitionStartDate(year, month),
                completionFraction =
                    KodexFinancialHighDividendTop10Schedule.incomingTransitionFraction(0),
            ),
            EquityMethodologyReconstitutionTransitionStep(
                effectiveDate =
                    KodexFinancialHighDividendTop10Schedule.transitionIntermediateDate(year, month),
                completionFraction =
                    KodexFinancialHighDividendTop10Schedule.incomingTransitionFraction(1),
            ),
        )
    }

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
                    addedAssetIds = setOf(replacementCandidate(input)),
                )
        }
    }

    private fun mergerDecision(
        input: EquityMethodologyCorporateActionInput,
    ): EquityMethodologyCorporateActionDecision {
        val targetId = input.event.primaryAssetId
        val acquirerId = requireNotNull(input.event.secondaryAssetId)
        val currentIds = input.currentConstituents.mapTo(hashSetOf(), EquityMethodologyCandidate::assetId)
        val transferredValueFraction = when (input.event.considerationKind) {
            ReferencePortfolioCorporateActionConsiderationKind.STOCK,
            ReferencePortfolioCorporateActionConsiderationKind.MIXED,
            -> input.event.valueTransferFraction
            ReferencePortfolioCorporateActionConsiderationKind.CASH -> 0.0
            ReferencePortfolioCorporateActionConsiderationKind.NONE ->
                error("A canonical merger cannot have NONE consideration.")
        }
        val acquirerCandidate = input.universeCandidates.single { candidate ->
            candidate.assetId == acquirerId
        }
        val qualifiedSuccessor = acquirerId !in currentIds &&
            acquirerCandidate.sector == MethodologyEquitySector.FINANCIALS &&
            acquirerCandidate.signals.requireBoolean(
                StandardEquityMethodologySignalIds.KOSPI200_FINANCIAL_MEMBER,
            )
        val addedAssetId = if (qualifiedSuccessor) {
            acquirerId
        } else {
            replacementCandidate(input)
        }
        return EquityMethodologyCorporateActionDecision(
            removedAssetIds = setOf(targetId),
            addedAssetIds = setOf(addedAssetId),
            survivingAcquirerAssetId = acquirerId.takeIf(currentIds::contains),
            transferredValueFraction = transferredValueFraction,
        )
    }

    /**
     * The public methodology keeps up to three non-selected stage-two names as reserves. The host
     * does not persist a licensed reserve file, so the same point-in-time screens deterministically
     * reconstruct that list at the corporate-action announcement close.
     */
    private fun replacementCandidate(input: EquityMethodologyCorporateActionInput): String {
        val currentIds = input.currentConstituents.mapTo(linkedSetOf(), EquityMethodologyCandidate::assetId)
        val screened = screenedCandidates(
            profile = input.profile,
            candidates = input.universeCandidates,
            incumbentAssetIds = currentIds,
            contextDate = input.event.announcementDate.toString(),
        )
        val shortlist = screened.sortedWith(
            descendingSignalOrder(StandardEquityMethodologySignalIds.ONE_MONTH_AVERAGE_DIVIDEND_YIELD),
        ).take(stageTwoCandidateCount(input.profile))
        return shortlist.asSequence()
            .filter { candidate -> candidate.assetId !in currentIds }
            .sortedWith(
                descendingSignalOrder(StandardEquityMethodologySignalIds.ONE_MONTH_AVERAGE_MARKET_CAP),
            )
            .map(EquityMethodologyCandidate::assetId)
            .firstOrNull()
            ?: screened.asSequence()
                .filter { candidate -> candidate.assetId !in currentIds }
                .sortedWith(
                    descendingSignalOrder(
                        StandardEquityMethodologySignalIds.ONE_MONTH_AVERAGE_MARKET_CAP,
                    ),
                )
                .map(EquityMethodologyCandidate::assetId)
                .firstOrNull()
            ?: error("The KRX reserve list has no eligible non-constituent replacement.")
    }

    private fun dividendYieldShortlist(
        profile: EquityMethodologyProfile,
        candidates: List<EquityMethodologyCandidate>,
        incumbentAssetIds: Set<String>,
        contextDate: String,
    ): List<EquityMethodologyCandidate> = screenedCandidates(
        profile = profile,
        candidates = candidates,
        incumbentAssetIds = incumbentAssetIds,
        contextDate = contextDate,
    ).sortedWith(
        descendingSignalOrder(StandardEquityMethodologySignalIds.ONE_MONTH_AVERAGE_DIVIDEND_YIELD),
    ).take(stageTwoCandidateCount(profile))

    private fun screenedCandidates(
        profile: EquityMethodologyProfile,
        candidates: List<EquityMethodologyCandidate>,
        incumbentAssetIds: Set<String>,
        contextDate: String,
    ): List<EquityMethodologyCandidate> {
        val reviewUniverse = candidates.filter { candidate ->
            candidate.sector == MethodologyEquitySector.FINANCIALS &&
                candidate.signals.requireBoolean(StandardEquityMethodologySignalIds.KOSPI200_FINANCIAL_MEMBER) &&
                integer(candidate, StandardEquityMethodologySignalIds.LISTING_AGE_YEARS) >=
                minimumListingYears(profile)
        }
        require(reviewUniverse.isNotEmpty()) { "The KOSPI 200 Financial review universe is empty." }
        val roeRanks = StandardEquityMethodologyComponents.descendingOrdinalRanks(
            reviewUniverse.associate { candidate ->
                candidate.assetId to decimal(
                    candidate,
                    StandardEquityMethodologySignalIds.THREE_YEAR_AVERAGE_RETURN_ON_EQUITY,
                )
            },
        )
        val maximumEligibleRoeRank = ceil(reviewUniverse.size * roeEligibleFraction(profile)).toInt()
        val screened = reviewUniverse.filter { candidate ->
            passesFundamentalAndLiquidityScreens(
                profile = profile,
                candidate = candidate,
                isIncumbent = candidate.assetId in incumbentAssetIds,
                roeRank = roeRanks.getValue(candidate.assetId),
                maximumEligibleRoeRank = maximumEligibleRoeRank,
            )
        }
        require(screened.size >= stageTwoCandidateCount(profile)) {
            "The KRX eligibility screens produced ${screened.size} candidates at $contextDate; " +
                "${stageTwoCandidateCount(profile)} are required."
        }
        return screened
    }

    private fun spinOffDecision(
        input: EquityMethodologyCorporateActionInput,
    ): EquityMethodologyCorporateActionDecision {
        val event = input.event
        val childId = requireNotNull(event.secondaryAssetId)
        val followUpDate = requireNotNull(event.followUpEffectiveDate)
        requireCanonical(
            input.currentConstituents.size < portfolioConstraints(input.profile).maximumConstituentCount,
            "at most one temporary spin-off constituent",
        )
        requireCanonical(schedule.isTradingDate(followUpDate), "a KRX trading-day spin-off removal")
        requireCanonical(
            followUpDate >= schedule.addTradingDays(event.effectiveDate, 1),
            "at least one KRX trading day for a temporary spin-off line",
        )
        return EquityMethodologyCorporateActionDecision(
            addedAssetIds = setOf(childId),
            transferredValueFraction = event.valueTransferFraction,
            followUpRemovalDate = followUpDate,
        )
    }

    private fun passesFundamentalAndLiquidityScreens(
        profile: EquityMethodologyProfile,
        candidate: EquityMethodologyCandidate,
        isIncumbent: Boolean,
        roeRank: Int,
        maximumEligibleRoeRank: Int,
    ): Boolean {
        val payoutRatio = decimal(
            candidate,
            StandardEquityMethodologySignalIds.THREE_YEAR_AVERAGE_DIVIDEND_PAYOUT_RATIO,
        )
        val priceToBook = decimal(
            candidate,
            StandardEquityMethodologySignalIds.ONE_MONTH_AVERAGE_PRICE_TO_BOOK_RATIO,
        )
        val priceToBookLimit = if (isIncumbent) {
            incumbentMaximumPriceToBookRatio(profile)
        } else {
            maximumPriceToBookRatio(profile)
        }
        return integer(candidate, StandardEquityMethodologySignalIds.DIVIDEND_PAYMENT_YEARS) >=
            minimumDividendPaymentYears(profile) &&
            payoutRatio > minimumDividendPayoutRatio(profile) &&
            payoutRatio < maximumDividendPayoutRatio(profile) &&
            decimal(candidate, StandardEquityMethodologySignalIds.ONE_MONTH_AVERAGE_DAILY_VALUE_TRADED) >=
            minimumAverageDailyValueTraded(profile) &&
            roeRank <= maximumEligibleRoeRank &&
            priceToBook < priceToBookLimit
    }

    private fun descendingSignalOrder(signalId: String): Comparator<EquityMethodologyCandidate> =
        compareByDescending<EquityMethodologyCandidate> { candidate -> decimal(candidate, signalId) }
            .thenBy(EquityMethodologyCandidate::assetId)

    private fun requireCanonical(condition: Boolean, rule: String) =
        require(condition) {
            "Unsupported equity methodology: the KRX Financial High Dividend TOP10 v2 policy requires $rule."
        }

    private fun decimal(candidate: EquityMethodologyCandidate, id: String): Double =
        candidate.signals.requireDecimal(id)

    private fun integer(candidate: EquityMethodologyCandidate, id: String): Int =
        candidate.signals.requireInteger(id)

    private fun targetConstituentCount(profile: EquityMethodologyProfile): Int =
        profile.parameters.integers.getValue("targetConstituentCount")

    private fun stageTwoCandidateCount(profile: EquityMethodologyProfile): Int =
        profile.parameters.integers.getValue("stageTwoCandidateCount")

    private fun minimumListingYears(profile: EquityMethodologyProfile): Int =
        profile.parameters.integers.getValue("minimumListingYears")

    private fun minimumDividendPaymentYears(profile: EquityMethodologyProfile): Int =
        profile.parameters.integers.getValue("minimumDividendPaymentYears")

    private fun minimumDividendPayoutRatio(profile: EquityMethodologyProfile): Double =
        profile.parameters.decimals.getValue("minimumDividendPayoutRatio")

    private fun maximumDividendPayoutRatio(profile: EquityMethodologyProfile): Double =
        profile.parameters.decimals.getValue("maximumDividendPayoutRatio")

    private fun minimumAverageDailyValueTraded(profile: EquityMethodologyProfile): Double =
        profile.parameters.decimals.getValue("minimumAverageDailyValueTraded")

    private fun roeEligibleFraction(profile: EquityMethodologyProfile): Double =
        profile.parameters.decimals.getValue("roeEligibleFraction")

    private fun maximumPriceToBookRatio(profile: EquityMethodologyProfile): Double =
        profile.parameters.decimals.getValue("maximumPriceToBookRatio")

    private fun incumbentMaximumPriceToBookRatio(profile: EquityMethodologyProfile): Double =
        profile.parameters.decimals.getValue("incumbentMaximumPriceToBookRatio")

    private fun individualWeightCap(profile: EquityMethodologyProfile): Double =
        profile.parameters.decimals.getValue("individualWeightCap")

    private const val CURRENT_THRESHOLD_POLICY = "KRX_MAY_2026_RULES"
    private const val THRESHOLD_POLICY = "thresholdPolicy"
    private const val ALL_CONSTITUENTS_GROUP = "ALL"
    private const val EPSILON = 1e-12
    private const val MAX_TEMPORARY_SPIN_OFF_CONSTITUENTS = 1
    private val CANONICAL_BENCHMARK_REF = BenchmarkRef(
        benchmarkId = "krx.kospi200-financial-high-dividend-top10",
        version = 2,
    )
    private val INTEGER_PARAMETER_KEYS = setOf(
        "targetConstituentCount",
        "stageTwoCandidateCount",
        "minimumListingYears",
        "minimumDividendPaymentYears",
    )
    private val DECIMAL_PARAMETER_KEYS = setOf(
        "minimumDividendPayoutRatio",
        "maximumDividendPayoutRatio",
        "minimumAverageDailyValueTraded",
        "roeEligibleFraction",
        "maximumPriceToBookRatio",
        "incumbentMaximumPriceToBookRatio",
        "individualWeightCap",
    )
    private val INTEGER_SET_PARAMETER_KEYS = setOf("reconstitutionMonths")
    private val TEXT_PARAMETER_KEYS = setOf(THRESHOLD_POLICY)
}
