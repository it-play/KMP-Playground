package com.amond.kmpbook.domain.methodology

import com.amond.kmpbook.domain.model.fund.BenchmarkDefinition
import com.amond.kmpbook.domain.model.fund.EquityMethodologyPathState
import com.amond.kmpbook.domain.model.fund.EquityMethodologyProfile
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioCorporateAction
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioCorporateActionKind

/**
 * Public SPI for trusted, pre-game equity-methodology implementations.
 *
 * Implementations must be pure and deterministic for the same input. The host validates every
 * returned ID, rank and weight before it can mutate campaign state.
 */
interface EquityMethodologyPolicy {
    val schedule: EquityMethodologySchedule

    /** Whether scheduled reviews persist provider-specific per-security path memory. */
    val usesPathState: Boolean get() = false

    /**
     * Whether every scheduled-reconstitution weighting input is fixed from the deterministic
     * selection snapshot instead of the live incumbent FMC carried by the portfolio.
     */
    val usesSelectionSnapshotMarketValuesForScheduledReconstitution: Boolean get() = false

    /** 최초 bootstrap 뒤에도 정기 종목선정 일정이 존재하는지 나타낸다. */
    val hasRecurringScheduledReconstitution: Boolean get() = true

    /** 종목선정과 별개인 정기 비중조정 lane이 존재하는지 나타낸다. */
    val hasRecurringScheduledReweight: Boolean get() = true

    val requiredDecimalSignalIds: Set<String> get() = emptySet()
    val requiredIntegerSignalIds: Set<String> get() = emptySet()
    val requiredBooleanSignalIds: Set<String> get() = emptySet()
    val requiredTextSignalIds: Set<String> get() = emptySet()

    fun validate(definition: BenchmarkDefinition, profile: EquityMethodologyProfile)

    fun portfolioConstraints(profile: EquityMethodologyProfile): EquityMethodologyPortfolioConstraints

    fun select(input: EquityMethodologySelectionInput): List<EquityMethodologySelection>

    /**
     * Path-dependent scheduled review hook. Existing methodologies remain stateless through this
     * adapter; providers with size/style packets may return a full next-candidate path state and a
     * bounded provider weighting multiplier for each selected security.
     */
    fun reconstitute(
        input: EquityMethodologySelectionInput,
    ): EquityMethodologyReconstitutionResult {
        val selections = select(input)
        return EquityMethodologyReconstitutionResult(
            selections = selections,
            referenceMarketValueMultipliers = selections.associate { selection ->
                selection.assetId to 1.0
            },
            nextPathState = if (usesPathState) {
                input.previousPathState
            } else {
                EquityMethodologyPathState.EMPTY
            },
        )
    }

    /**
     * Provider-specific shares/IWF changes observed at a weighting reference close. The returned
     * values become the persisted float-market-value basis as well as the target-weight input.
     */
    fun referenceMarketValuesForWeighting(
        input: EquityMethodologyWeightingInput,
    ): Map<String, Double> = input.referenceMarketValues

    fun targetWeights(input: EquityMethodologyWeightingInput): Map<String, Double>

    /**
     * Optional partial composition steps between the fixed weighting close and the final
     * scheduled reconstitution. Most methodologies apply atomically and return an empty list.
     */
    fun scheduledReconstitutionTransitionSteps(
        profile: EquityMethodologyProfile,
        action: EquityMethodologyScheduledAction,
    ): List<EquityMethodologyReconstitutionTransitionStep> = emptyList()

    fun constraintReweightEffectiveDate(input: EquityMethodologyConstraintInput): kotlinx.datetime.LocalDate? = null

    fun nextExtraordinaryRemovalReviewDate(
        profile: EquityMethodologyProfile,
        afterExclusive: kotlinx.datetime.LocalDate,
    ): kotlinx.datetime.LocalDate? = null

    fun extraordinaryRemovalDecision(
        input: EquityMethodologyRemovalInput,
    ): EquityMethodologyRemovalDecision? = null

    fun corporateActionNoticeTradingDays(
        profile: EquityMethodologyProfile,
        kind: ReferencePortfolioCorporateActionKind,
    ): Int = 1

    fun corporateActionDecision(
        input: EquityMethodologyCorporateActionInput,
    ): EquityMethodologyCorporateActionDecision? = null

    /**
     * Optional provider execution points for a merger or terminal-removal replacement. The final
     * point must complete at 100%; spin-offs remain atomic and never use this lane.
     */
    fun corporateActionTransitionSteps(
        profile: EquityMethodologyProfile,
        event: ReferencePortfolioCorporateAction,
    ): List<EquityMethodologyCorporateActionTransitionStep> = emptyList()
}
