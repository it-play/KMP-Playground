package com.amond.kmpbook.domain.methodology.builtin

import com.amond.kmpbook.domain.methodology.EquityMethodologyCandidate
import com.amond.kmpbook.domain.methodology.EquityMethodologyCorporateActionDecision
import com.amond.kmpbook.domain.methodology.EquityMethodologyCorporateActionInput
import com.amond.kmpbook.domain.methodology.EquityMethodologyPolicy
import com.amond.kmpbook.domain.methodology.EquityMethodologyPortfolioConstraints
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
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioActionKind
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioCorporateActionKind
import com.amond.kmpbook.domain.model.methodology.EquityMethodologyRef
import kotlin.math.abs

/**
 * 공개 S&P 500 편입 자격과 유동시가총액 가중을 실행하는 v2 정책이다.
 *
 * 실제 구성 변경은 위원회 재량이므로, 자격 후보 중 동일 섹터 FMC 우선 대체라는 버전된 대리
 * 모델을 사용한다. 이 결정론적 구성은 실제 또는 미래 S&P 500 구성종목 예측이 아니다.
 */
internal object Sp500Policy : EquityMethodologyPolicy {
    override val schedule = Sp500Schedule
    override val hasRecurringScheduledReconstitution: Boolean = false

    override val requiredDecimalSignalIds: Set<String> = setOf(
        StandardEquityMethodologySignalIds.FLOAT_MARKET_CAP,
        StandardEquityMethodologySignalIds.TOTAL_COMPANY_MARKET_CAP,
        StandardEquityMethodologySignalIds.INVESTABLE_WEIGHT_FACTOR,
        StandardEquityMethodologySignalIds.FLOAT_ADJUSTED_LIQUIDITY_RATIO,
        StandardEquityMethodologySignalIds.MINIMUM_SIX_MONTH_MONTHLY_SHARE_VOLUME,
    )
    override val requiredBooleanSignalIds: Set<String> = setOf(
        StandardEquityMethodologySignalIds.LATEST_QUARTER_GAAP_NET_INCOME_POSITIVE,
        StandardEquityMethodologySignalIds.TRAILING_FOUR_QUARTER_GAAP_NET_INCOME_POSITIVE,
    )

    override fun validate(definition: BenchmarkDefinition, profile: EquityMethodologyProfile) {
        requireCanonical(definition.ref == CANONICAL_BENCHMARK_REF, "the canonical benchmark identity")
        requireCanonical(
            profile.methodologyRef == EquityMethodologyRef.SP_500_V2,
            "the built-in S&P 500 methodology registration",
        )
        requireCanonical(
            profile.referenceUniverse == FundReferenceUniverse.US_BROAD_EQUITY,
            "the US broad-equity reference universe",
        )
        requireCanonical(
            profile.decisionModel == EquityMethodologyDecisionModel.DISCRETIONARY_PROXY,
            "an explicit discretionary-proxy decision model",
        )
        requireCanonical(
            profile.modelAssumptionId == MODEL_ASSUMPTION_ID,
            "the versioned same-sector committee proxy assumption",
        )
        profile.parameters.requireExactKeys(
            integerKeys = INTEGER_PARAMETER_KEYS,
            decimalKeys = DECIMAL_PARAMETER_KEYS,
            textKeys = TEXT_PARAMETER_KEYS,
            integerSetKeys = INTEGER_SET_PARAMETER_KEYS,
        )
        requireCanonical(targetCompanyCount(profile) == 500, "500 companies")
        requireCanonical(
            quarterlyShareUpdateMonthCount(profile) == 4,
            "four quarterly share-update months",
        )
        requireCanonical(
            quarterlyShareUpdateMonths(profile) == setOf(3, 6, 9, 12),
            "March, June, September and December share updates",
        )
        requireCanonical(
            abs(minimumTotalCompanyMarketCap(profile) - 22_700_000_000.0) <= EPSILON,
            "the frozen July 2026 USD 22.7 billion company market-cap guideline",
        )
        requireCanonical(
            abs(minimumFloatAdjustedMarketCap(profile) - 11_350_000_000.0) <= EPSILON,
            "50% of the frozen company market-cap guideline in security FMC",
        )
        requireCanonical(
            abs(minimumInvestableWeightFactor(profile) - 0.10) <= EPSILON,
            "a 0.10 minimum IWF",
        )
        requireCanonical(
            abs(minimumFloatAdjustedLiquidityRatio(profile) - 0.75) <= EPSILON,
            "a 0.75 minimum FALR",
        )
        requireCanonical(
            abs(minimumMonthlyShareVolume(profile) - 250_000.0) <= EPSILON,
            "250,000 shares in each of the preceding six months",
        )
        requireCanonical(
            profile.parameters.texts.getValue(THRESHOLD_POLICY) == FROZEN_THRESHOLD_POLICY,
            "the frozen July 2026 threshold policy",
        )
        requireCanonical(
            schedule.initialScheduledAction(profile).effectiveDate == profile.effectiveFrom,
            "bootstrap at effectiveFrom",
        )
    }

    override fun portfolioConstraints(profile: EquityMethodologyProfile) =
        EquityMethodologyPortfolioConstraints(
            minimumConstituentCount = targetCompanyCount(profile),
            maximumConstituentCount = targetCompanyCount(profile) + MAX_TEMPORARY_SPIN_OFF_LINES,
            scheduledSelectionCount = targetCompanyCount(profile),
        )

    override fun select(input: EquityMethodologySelectionInput): List<EquityMethodologySelection> {
        val targetCount = targetCompanyCount(input.profile)
        val candidatesById = input.candidates.associateBy(EquityMethodologyCandidate::assetId)
        val rankedUniverse = input.candidates.sortedWith(floatMarketCapOrder())
        val eligibleNewcomers = rankedUniverse.filter { candidate ->
            passesAdditionEligibility(input.profile, candidate)
        }
        val retainedIncumbents = rankedUniverse.filter { candidate ->
            candidate.assetId in input.incumbentAssetIds
        }.take(targetCount)
        val retainedIds = retainedIncumbents.mapTo(linkedSetOf(), EquityMethodologyCandidate::assetId)
        val selectedIds = buildList {
            addAll(retainedIncumbents.map(EquityMethodologyCandidate::assetId))
            addAll(
                eligibleNewcomers.asSequence()
                    .map(EquityMethodologyCandidate::assetId)
                    .filterNot(retainedIds::contains)
                    .take(targetCount - size),
            )
        }
        require(selectedIds.size == targetCount) {
            "The S&P 500 proxy universe contains too few addition-eligible candidates."
        }
        val rankById = rankedUniverse.withIndex().associate { (index, candidate) ->
            candidate.assetId to index + 1
        }
        return selectedIds.map { assetId ->
            requireNotNull(candidatesById[assetId])
            EquityMethodologySelection(assetId, rankById.getValue(assetId))
        }.sortedBy(EquityMethodologySelection::rank)
    }

    override fun referenceMarketValuesForWeighting(
        input: EquityMethodologyWeightingInput,
    ): Map<String, Double> = if (
        input.actionKind == ReferencePortfolioActionKind.SCHEDULED_REWEIGHT
    ) {
        input.referenceMarketValues.mapValues { (assetId, marketValue) ->
            marketValue * quarterlyFloatAdjustmentMultiplier(assetId, input.observationDate.toString())
        }
    } else {
        input.referenceMarketValues
    }

    override fun targetWeights(input: EquityMethodologyWeightingInput): Map<String, Double> =
        StandardEquityMethodologyComponents.proportionalWeights(input.referenceMarketValues)

    override fun corporateActionNoticeTradingDays(
        profile: EquityMethodologyProfile,
        kind: ReferencePortfolioCorporateActionKind,
    ): Int = STANDARD_CONSTITUENT_CHANGE_NOTICE_DAYS

    override fun corporateActionDecision(
        input: EquityMethodologyCorporateActionInput,
    ): EquityMethodologyCorporateActionDecision {
        requireCanonical(schedule.isTradingDate(input.event.effectiveDate), "a regular-trading-day effective date")
        return when (input.event.kind) {
            ReferencePortfolioCorporateActionKind.MERGER,
            ReferencePortfolioCorporateActionKind.TERMINAL_REMOVAL,
            -> replacementDecision(input)
            ReferencePortfolioCorporateActionKind.SPIN_OFF -> {
                val childId = requireNotNull(input.event.secondaryAssetId)
                EquityMethodologyCorporateActionDecision(
                    addedAssetIds = setOf(childId),
                    transferredValueFraction = input.event.valueTransferFraction,
                    followUpRemovalDate = requireNotNull(input.event.followUpEffectiveDate),
                )
            }
        }
    }

    private fun replacementDecision(
        input: EquityMethodologyCorporateActionInput,
    ): EquityMethodologyCorporateActionDecision {
        val currentIds = input.currentConstituents.mapTo(hashSetOf(), EquityMethodologyCandidate::assetId)
        val removed = input.currentConstituents.first { candidate ->
            candidate.assetId == input.event.primaryAssetId
        }
        val replacement = input.universeCandidates.asSequence()
            .filter { candidate -> candidate.assetId !in currentIds }
            .filter { candidate -> passesAdditionEligibility(input.profile, candidate) }
            .sortedWith(
                compareByDescending<EquityMethodologyCandidate> { candidate ->
                    candidate.sector == removed.sector
                }.then(floatMarketCapOrder()),
            )
            .firstOrNull()
        requireNotNull(replacement) {
            "The S&P 500 proxy universe has no eligible replacement candidate."
        }
        return EquityMethodologyCorporateActionDecision(
            removedAssetIds = setOf(removed.assetId),
            addedAssetIds = setOf(replacement.assetId),
        )
    }

    private fun passesAdditionEligibility(
        profile: EquityMethodologyProfile,
        candidate: EquityMethodologyCandidate,
    ): Boolean = decimal(candidate, StandardEquityMethodologySignalIds.TOTAL_COMPANY_MARKET_CAP) >=
        minimumTotalCompanyMarketCap(profile) &&
        decimal(candidate, StandardEquityMethodologySignalIds.FLOAT_MARKET_CAP) >=
        minimumFloatAdjustedMarketCap(profile) &&
        decimal(candidate, StandardEquityMethodologySignalIds.INVESTABLE_WEIGHT_FACTOR) >=
        minimumInvestableWeightFactor(profile) &&
        decimal(candidate, StandardEquityMethodologySignalIds.FLOAT_ADJUSTED_LIQUIDITY_RATIO) >=
        minimumFloatAdjustedLiquidityRatio(profile) &&
        decimal(candidate, StandardEquityMethodologySignalIds.MINIMUM_SIX_MONTH_MONTHLY_SHARE_VOLUME) >=
        minimumMonthlyShareVolume(profile) &&
        candidate.signals.requireBoolean(
            StandardEquityMethodologySignalIds.LATEST_QUARTER_GAAP_NET_INCOME_POSITIVE,
        ) && candidate.signals.requireBoolean(
            StandardEquityMethodologySignalIds.TRAILING_FOUR_QUARTER_GAAP_NET_INCOME_POSITIVE,
        )

    private fun floatMarketCapOrder(): Comparator<EquityMethodologyCandidate> =
        compareByDescending<EquityMethodologyCandidate> { candidate ->
            decimal(candidate, StandardEquityMethodologySignalIds.FLOAT_MARKET_CAP)
        }.thenBy(EquityMethodologyCandidate::assetId)

    /**
     * 공개 자료는 미래 회사별 shares/IWF 변경량을 제공하지 않는다. 이 버전된 대리값은 각
     * 분기 기준일에 -3%~+3% 범위의 결정론적 float-level 조정을 만들며 가격수익과 섞지 않는다.
     */
    private fun quarterlyFloatAdjustmentMultiplier(assetId: String, observationDate: String): Double {
        var hash = STABLE_HASH_SEED
        "$assetId:$observationDate".forEach { character ->
            hash = hash * STABLE_HASH_MULTIPLIER + character.code.toLong()
        }
        val bucket = (hash xor (hash ushr 32)).and(STABLE_HASH_BUCKET_MASK).toDouble()
        val centered = bucket / STABLE_HASH_BUCKET_MASK.toDouble() - 0.5
        return 1.0 + centered * MAX_QUARTERLY_FLOAT_LEVEL_RANGE
    }

    private fun requireCanonical(condition: Boolean, rule: String) =
        require(condition) { "Unsupported equity methodology: the S&P 500 v2 policy requires $rule." }

    private fun decimal(candidate: EquityMethodologyCandidate, id: String): Double =
        candidate.signals.requireDecimal(id)

    private fun targetCompanyCount(profile: EquityMethodologyProfile): Int =
        profile.parameters.integers.getValue("targetCompanyCount")

    private fun quarterlyShareUpdateMonthCount(profile: EquityMethodologyProfile): Int =
        profile.parameters.integers.getValue("quarterlyShareUpdateMonthCount")

    private fun minimumTotalCompanyMarketCap(profile: EquityMethodologyProfile): Double =
        profile.parameters.decimals.getValue("minimumTotalCompanyMarketCap")

    private fun minimumFloatAdjustedMarketCap(profile: EquityMethodologyProfile): Double =
        profile.parameters.decimals.getValue("minimumFloatAdjustedMarketCap")

    private fun minimumInvestableWeightFactor(profile: EquityMethodologyProfile): Double =
        profile.parameters.decimals.getValue("minimumInvestableWeightFactor")

    private fun minimumFloatAdjustedLiquidityRatio(profile: EquityMethodologyProfile): Double =
        profile.parameters.decimals.getValue("minimumFloatAdjustedLiquidityRatio")

    private fun minimumMonthlyShareVolume(profile: EquityMethodologyProfile): Double =
        profile.parameters.decimals.getValue("minimumMonthlyShareVolume")

    private fun quarterlyShareUpdateMonths(profile: EquityMethodologyProfile): Set<Int> =
        Sp500Schedule.quarterlyShareUpdateMonths(profile)

    private const val EPSILON: Double = 1e-12
    private const val MAX_TEMPORARY_SPIN_OFF_LINES: Int = 1
    private const val STANDARD_CONSTITUENT_CHANGE_NOTICE_DAYS: Int = 3
    private const val STABLE_HASH_SEED: Long = 1_125_899_906_842_597L
    private const val STABLE_HASH_MULTIPLIER: Long = 31L
    private const val STABLE_HASH_BUCKET_MASK: Long = 0xffffL
    private const val MAX_QUARTERLY_FLOAT_LEVEL_RANGE: Double = 0.06
    private const val MODEL_ASSUMPTION_ID: String = "sp500-committee-proxy-2026-08-15-v1"
    private const val THRESHOLD_POLICY: String = "thresholdPolicy"
    private const val FROZEN_THRESHOLD_POLICY: String = "FROZEN_JULY_2026_GUIDELINE"
    private val CANONICAL_BENCHMARK_REF = BenchmarkRef("spdj-sp-500", 2)
    private val INTEGER_PARAMETER_KEYS = setOf(
        "quarterlyShareUpdateMonthCount",
        "targetCompanyCount",
    )
    private val DECIMAL_PARAMETER_KEYS = setOf(
        "minimumFloatAdjustedLiquidityRatio",
        "minimumFloatAdjustedMarketCap",
        "minimumInvestableWeightFactor",
        "minimumMonthlyShareVolume",
        "minimumTotalCompanyMarketCap",
    )
    private val TEXT_PARAMETER_KEYS = setOf(THRESHOLD_POLICY)
    private val INTEGER_SET_PARAMETER_KEYS = setOf("quarterlyShareUpdateMonths")
}
