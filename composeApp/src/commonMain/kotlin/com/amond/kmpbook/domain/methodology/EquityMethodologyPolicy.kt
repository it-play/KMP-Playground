package com.amond.kmpbook.domain.methodology

import com.amond.kmpbook.domain.model.fund.BenchmarkDefinition
import com.amond.kmpbook.domain.model.fund.EquityMethodologyProfile

/**
 * Public SPI for trusted, pre-game equity-methodology implementations.
 *
 * Implementations must be pure and deterministic for the same input. The host validates every
 * returned ID, rank and weight before it can mutate campaign state.
 */
interface EquityMethodologyPolicy {
    val schedule: EquityMethodologySchedule

    val requiredDecimalSignalIds: Set<String> get() = emptySet()
    val requiredIntegerSignalIds: Set<String> get() = emptySet()
    val requiredBooleanSignalIds: Set<String> get() = emptySet()
    val requiredTextSignalIds: Set<String> get() = emptySet()

    fun validate(definition: BenchmarkDefinition, profile: EquityMethodologyProfile)

    fun portfolioConstraints(profile: EquityMethodologyProfile): EquityMethodologyPortfolioConstraints

    fun select(input: EquityMethodologySelectionInput): List<EquityMethodologySelection>

    fun targetWeights(input: EquityMethodologyWeightingInput): Map<String, Double>

    fun constraintReweightEffectiveDate(input: EquityMethodologyConstraintInput): kotlinx.datetime.LocalDate? = null

    fun nextExtraordinaryRemovalReviewDate(
        profile: EquityMethodologyProfile,
        afterExclusive: kotlinx.datetime.LocalDate,
    ): kotlinx.datetime.LocalDate? = null

    fun extraordinaryRemovalDecision(
        input: EquityMethodologyRemovalInput,
    ): EquityMethodologyRemovalDecision? = null

    fun corporateActionDecision(
        input: EquityMethodologyCorporateActionInput,
    ): EquityMethodologyCorporateActionDecision? = null
}
