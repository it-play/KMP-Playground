package com.amond.kmpbook.domain.methodology.builtin

import com.amond.kmpbook.domain.methodology.EquityMethodologyCandidate
import com.amond.kmpbook.domain.methodology.EquityMethodologyConstraintInput
import com.amond.kmpbook.domain.methodology.EquityMethodologyCorporateActionDecision
import com.amond.kmpbook.domain.methodology.EquityMethodologyCorporateActionInput
import com.amond.kmpbook.domain.methodology.EquityMethodologyPolicy
import com.amond.kmpbook.domain.methodology.EquityMethodologyPortfolioConstraints
import com.amond.kmpbook.domain.methodology.EquityMethodologyReconstitutionResult
import com.amond.kmpbook.domain.methodology.EquityMethodologyReconstitutionTransitionStep
import com.amond.kmpbook.domain.methodology.EquityMethodologyScheduledAction
import com.amond.kmpbook.domain.methodology.EquityMethodologySelection
import com.amond.kmpbook.domain.methodology.EquityMethodologySelectionInput
import com.amond.kmpbook.domain.methodology.EquityMethodologyWeightingInput
import com.amond.kmpbook.domain.methodology.StandardEquityMethodologyComponents
import com.amond.kmpbook.domain.methodology.StandardEquityMethodologySignalIds
import com.amond.kmpbook.domain.model.fund.BenchmarkDefinition
import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import com.amond.kmpbook.domain.model.fund.EquityMethodologyDecisionModel
import com.amond.kmpbook.domain.model.fund.EquityMethodologyPathEntry
import com.amond.kmpbook.domain.model.fund.EquityMethodologyPathState
import com.amond.kmpbook.domain.model.fund.EquityMethodologyProfile
import com.amond.kmpbook.domain.model.fund.FundReferenceUniverse
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioActionKind
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioCorporateActionConsiderationKind
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioCorporateActionKind
import com.amond.kmpbook.domain.model.methodology.EquityMethodologyRef
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.datetime.LocalDate

/**
 * Executable v2 proxy for the public Morningstar US Large Cap Value Index methodology.
 *
 * The provider's company/security master, random price-day data and committee determinations are
 * licensed inputs. This policy therefore applies the published size and style packet rules to the
 * deterministic host universe and uses the first-Friday close declared by the versioned profile.
 */
internal object MorningstarLargeCapValuePolicy : EquityMethodologyPolicy {
    override val schedule = MorningstarLargeCapValueSchedule
    override val hasRecurringScheduledReweight: Boolean = false
    override val usesPathState: Boolean = true
    override val usesSelectionSnapshotMarketValuesForScheduledReconstitution: Boolean = true

    override val requiredDecimalSignalIds: Set<String> = setOf(
        StandardEquityMethodologySignalIds.FLOAT_MARKET_CAP,
        StandardEquityMethodologySignalIds.TOTAL_COMPANY_MARKET_CAP,
        StandardEquityMethodologySignalIds.INVESTABLE_WEIGHT_FACTOR,
        StandardEquityMethodologySignalIds.TRAILING_125_TRADING_DAY_AVERAGE_DAILY_VALUE_TRADED,
        StandardEquityMethodologySignalIds.BOOK_TO_PRICE,
        StandardEquityMethodologySignalIds.FUTURE_EARNINGS_TO_PRICE,
        StandardEquityMethodologySignalIds.HISTORICAL_EARNINGS_TO_PRICE,
        StandardEquityMethodologySignalIds.DIVIDEND_TO_PRICE,
        StandardEquityMethodologySignalIds.SALES_TO_PRICE,
        StandardEquityMethodologySignalIds.FUTURE_LONG_TERM_EARNINGS_GROWTH,
        StandardEquityMethodologySignalIds.FUTURE_SHORT_TERM_EARNINGS_GROWTH,
        StandardEquityMethodologySignalIds.THREE_YEAR_HISTORICAL_EARNINGS_GROWTH,
        StandardEquityMethodologySignalIds.THREE_YEAR_HISTORICAL_SALES_GROWTH,
        StandardEquityMethodologySignalIds.CURRENT_INVESTMENT_TO_ASSETS,
        StandardEquityMethodologySignalIds.RETURN_ON_ASSETS,
    )

    override fun validate(definition: BenchmarkDefinition, profile: EquityMethodologyProfile) {
        requireCanonical(definition.ref == CANONICAL_BENCHMARK_REF, "the canonical benchmark identity")
        requireCanonical(
            profile.methodologyRef == EquityMethodologyRef.MORNINGSTAR_US_LARGE_CAP_VALUE_V2,
            "the built-in Morningstar large-cap value methodology registration",
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
            "the versioned licensed-data and committee proxy assumption",
        )
        profile.parameters.requireExactKeys(
            integerKeys = INTEGER_PARAMETER_KEYS,
            decimalKeys = DECIMAL_PARAMETER_KEYS,
            textKeys = TEXT_PARAMETER_KEYS,
            integerSetKeys = INTEGER_SET_PARAMETER_KEYS,
        )
        requireCanonical(
            profile.effectiveFrom == CANONICAL_EFFECTIVE_FROM,
            "the modeled March 2026 historical-path bootstrap",
        )
        requireCanonical(maximumConstituentCount(profile) == 1_001, "1,000 regular lines plus one spin-off line")
        requireCanonical(transitionTradingDayCount(profile) == 5, "a five-close transition")
        requireCanonical(reentryClearanceRankingCount(profile) == 4, "four re-entry clearance rankings")
        requireCanonical(lowVolumeDeletionRankingCount(profile) == 2, "two low-volume deletion rankings")
        requireCanonical(reconstitutionMonths(profile) == setOf(3, 6, 9, 12), "quarterly reviews")
        requireCanonical(
            profile.parameters.texts.getValue(RANKING_PRICE_POLICY) ==
                FIRST_FRIDAY_CLOSE_LICENSED_RANDOM_DAY_PROXY,
            "the declared first-Friday-close proxy for the licensed random price day",
        )
        CANONICAL_DECIMAL_VALUES.forEach { (key, value) ->
            requireCanonical(abs(profile.parameters.decimals.getValue(key) - value) <= EPSILON, "$key=$value")
        }
        requireCanonical(
            megaAdjacentCoreLower(profile) < megaBandLower(profile) &&
                megaBandLower(profile) < megaCapTarget(profile) &&
                megaCapTarget(profile) < megaBandUpper(profile) &&
                megaBandUpper(profile) == midAdjacentCoreLower(profile) &&
                midAdjacentCoreLower(profile) < megaAdjacentCoreUpper(profile) &&
                megaAdjacentCoreUpper(profile) == midBandLower(profile) &&
                midBandLower(profile) < midCapTarget(profile) &&
                midCapTarget(profile) < midBandUpper(profile) &&
                midBandUpper(profile) == smallAdjacentCoreLower(profile) &&
                smallAdjacentCoreLower(profile) < midAdjacentCoreUpper(profile) &&
                midAdjacentCoreUpper(profile) == smallBandLower(profile) &&
                smallBandLower(profile) < smallCapTarget(profile) &&
                smallCapTarget(profile) < smallBandUpper(profile) &&
                smallBandUpper(profile) < smallAdjacentCoreUpper(profile),
            "the published adjacent size bands and cores",
        )
        requireCanonical(
            MorningstarLargeCapValueCalendar.quarterlyPartialTransitionCloseDates(2026, 6) == listOf(
                LocalDate(2026, 6, 16),
                LocalDate(2026, 6, 17),
                LocalDate(2026, 6, 18),
                LocalDate(2026, 6, 22),
            ) && MorningstarLargeCapValueCalendar.quarterlyFinalTransitionCloseDate(2026, 6) ==
                LocalDate(2026, 6, 23) &&
                MorningstarLargeCapValueCalendar.quarterlyFinalTransitionCloseDate(2026, 9) ==
                LocalDate(2026, 9, 22),
            "the published 2026 transition calendar",
        )
        requireCanonical(
            MorningstarLargeCapValueCalendar.quarterlyPartialApplicationDates(2026, 6) == listOf(
                LocalDate(2026, 6, 17),
                LocalDate(2026, 6, 18),
                LocalDate(2026, 6, 22),
                LocalDate(2026, 6, 23),
            ) && MorningstarLargeCapValueCalendar.quarterlyFinalApplicationDate(2026, 6) ==
                LocalDate(2026, 6, 24) &&
                MorningstarLargeCapValueCalendar.quarterlyFinalApplicationDate(2026, 9) ==
                LocalDate(2026, 9, 23),
            "the host next-session ledger applications for the 2026 closes",
        )
        requireCanonical(
            schedule.initialScheduledAction(profile).effectiveDate == profile.effectiveFrom,
            "bootstrap at effectiveFrom",
        )
    }

    override fun portfolioConstraints(profile: EquityMethodologyProfile) =
        EquityMethodologyPortfolioConstraints(
            minimumConstituentCount = 1,
            maximumConstituentCount = maximumConstituentCount(profile),
            scheduledSelectionCount = null,
            // The host-level cap must admit the quarter-end compliance target. Scheduled reviews
            // enforce their tighter 22.5% ranking buffer inside targetWeights.
            individualWeightCap = ricComplianceTargetIndividualCap(profile),
        )

    override fun select(input: EquityMethodologySelectionInput): List<EquityMethodologySelection> =
        reconstitute(input).selections

    override fun reconstitute(input: EquityMethodologySelectionInput): EquityMethodologyReconstitutionResult {
        val profile = input.profile
        val previousById = input.previousPathState.entries.associateBy(EquityMethodologyPathEntry::assetId)
        val candidatesById = input.candidates.associateBy(EquityMethodologyCandidate::assetId)
        val totalUniverseFloatMarketCap = input.candidates.sumOf { candidate ->
            positiveDecimal(candidate, StandardEquityMethodologySignalIds.FLOAT_MARKET_CAP)
        }
        require(totalUniverseFloatMarketCap.isFinite() && totalUniverseFloatMarketCap > 0.0)
        // Size breakpoints are established over the full index-eligible parent universe before
        // the security-level investability add/drop screens below are applied.
        val cumulativeMidpoints = cumulativeMarketCapMidpoints(input.candidates)

        val eligibleById = mutableMapOf<String, Boolean>()
        val reentryQuarantinedById = mutableMapOf<String, Boolean>()
        val reentryClearanceEarnedById = mutableMapOf<String, Boolean>()
        val reentryClearanceProgressById = mutableMapOf<String, Double>()
        val lowVolumeDeletionProgressById = mutableMapOf<String, Double>()
        input.candidates.sortedBy(EquityMethodologyCandidate::assetId).forEach { candidate ->
            val previous = previousById[candidate.assetId]
            val wasEligible = previous?.booleanValues?.get(UNIVERSE_ELIGIBLE) == true
            val wasReentryQuarantined = previous?.booleanValues?.get(REENTRY_QUARANTINED) == true
            val wasReentryClearanceEarned =
                previous?.booleanValues?.get(REENTRY_CLEARANCE_EARNED) == true
            require(!(wasEligible && wasReentryQuarantined))
            require(!wasReentryClearanceEarned || wasReentryQuarantined)
            val previousClearanceProgress = previous?.let { entry ->
                normalizedCounterProgress(
                    entry = entry,
                    key = REENTRY_CLEARANCE_PROGRESS,
                    rankingCount = reentryClearanceRankingCount(profile),
                )
            } ?: 0.0
            val previousLowVolumeProgress = previous?.let { entry ->
                normalizedCounterProgress(
                    entry = entry,
                    key = LOW_VOLUME_DELETION_PROGRESS,
                    rankingCount = lowVolumeDeletionRankingCount(profile),
                )
            } ?: 0.0
            require(wasReentryQuarantined || previousClearanceProgress == 0.0)
            require(
                !wasReentryClearanceEarned ||
                    previousClearanceProgress >= 1.0 - PATH_EPSILON,
            )
            require(
                wasReentryClearanceEarned ||
                    previousClearanceProgress < 1.0 - PATH_EPSILON,
            )
            require(wasEligible || previousLowVolumeProgress == 0.0)

            val volumeRatio = volumeToFloatMarketCapRatio(candidate)
            val passesDeletionCapitalAndFloat = passesCapitalAndFloatThresholds(
                profile = profile,
                candidate = candidate,
                totalUniverseFloatMarketCap = totalUniverseFloatMarketCap,
                addition = false,
            )
            val passesAddition = passesCapitalAndFloatThresholds(
                profile = profile,
                candidate = candidate,
                totalUniverseFloatMarketCap = totalUniverseFloatMarketCap,
                addition = true,
            ) && volumeRatio >= minimumAdditionVolumeRatio(profile)
            val passesReentryClearance = passesReentryClearanceThresholds(
                profile = profile,
                candidate = candidate,
                totalUniverseFloatMarketCap = totalUniverseFloatMarketCap,
                volumeRatio = volumeRatio,
            )

            var eligibleNow = false
            var quarantinedNow = false
            var clearanceEarnedNow = false
            var clearanceProgress = 0.0
            var lowVolumeProgress = 0.0
            when {
                wasEligible && !passesDeletionCapitalAndFloat -> {
                    quarantinedNow = true
                }
                wasEligible -> {
                    lowVolumeProgress = if (volumeRatio < minimumDeletionVolumeRatio(profile)) {
                        min(
                            1.0,
                            previousLowVolumeProgress +
                                1.0 / lowVolumeDeletionRankingCount(profile),
                        )
                    } else {
                        0.0
                    }
                    if (lowVolumeProgress >= 1.0 - PATH_EPSILON) {
                        quarantinedNow = true
                        lowVolumeProgress = 0.0
                    } else {
                        eligibleNow = true
                    }
                }
                wasReentryClearanceEarned -> {
                    if (passesAddition) {
                        eligibleNow = true
                    } else {
                        quarantinedNow = true
                        clearanceEarnedNow = true
                        clearanceProgress = 1.0
                    }
                }
                wasReentryQuarantined -> {
                    clearanceProgress = if (passesReentryClearance) {
                        min(
                            1.0,
                            previousClearanceProgress + 1.0 / reentryClearanceRankingCount(profile),
                        )
                    } else {
                        0.0
                    }
                    if (clearanceProgress >= 1.0 - PATH_EPSILON) {
                        if (passesAddition) {
                            eligibleNow = true
                            clearanceProgress = 0.0
                        } else {
                            quarantinedNow = true
                            clearanceEarnedNow = true
                            clearanceProgress = 1.0
                        }
                    } else {
                        quarantinedNow = true
                    }
                }
                passesAddition -> eligibleNow = true
            }
            require(!(eligibleNow && quarantinedNow))
            require(!clearanceEarnedNow || quarantinedNow)
            require(!clearanceEarnedNow || clearanceProgress >= 1.0 - PATH_EPSILON)
            eligibleById[candidate.assetId] = eligibleNow
            reentryQuarantinedById[candidate.assetId] = quarantinedNow
            reentryClearanceEarnedById[candidate.assetId] = clearanceEarnedNow
            reentryClearanceProgressById[candidate.assetId] = clearanceProgress
            lowVolumeDeletionProgressById[candidate.assetId] = lowVolumeProgress
        }
        val eligible = input.candidates.filter { candidate -> eligibleById.getValue(candidate.assetId) }
        require(eligible.isNotEmpty()) { "The Morningstar proxy universe contains no eligible security." }
        val sizeAllocations = eligible.associate { candidate ->
            val previousEntry = previousById[candidate.assetId]
            candidate.assetId to nextSizeAllocation(
                profile = profile,
                cumulativeMidpoint = cumulativeMidpoints.getValue(candidate.assetId),
                previous = previousEntry?.takeIf {
                    it.booleanValues[UNIVERSE_ELIGIBLE] == true
                }?.let(::sizeAllocation),
            )
        }
        val styleAllocations = nextStyleAllocations(
            profile = profile,
            eligibleCandidates = eligible,
            sizeAllocations = sizeAllocations,
            previousById = previousById,
        )

        val nextEntries = input.candidates.sortedBy(EquityMethodologyCandidate::assetId).map { candidate ->
            val eligibleNow = eligibleById.getValue(candidate.assetId)
            val sizes = sizeAllocations[candidate.assetId] ?: DoubleArray(SIZE_SEGMENT_COUNT)
            val styles = styleAllocations[candidate.assetId] ?: DoubleArray(SIZE_SEGMENT_COUNT) { 0.5 }
            EquityMethodologyPathEntry(
                assetId = candidate.assetId,
                decimalValues = buildMap {
                    put(
                        REENTRY_CLEARANCE_PROGRESS,
                        reentryClearanceProgressById.getValue(candidate.assetId),
                    )
                    put(
                        LOW_VOLUME_DELETION_PROGRESS,
                        lowVolumeDeletionProgressById.getValue(candidate.assetId),
                    )
                    SIZE_KEYS.forEachIndexed { index, key -> put(key, sizes[index]) }
                    STYLE_KEYS.forEachIndexed { index, key -> put(key, styles[index]) }
                }.toSortedMap(),
                booleanValues = buildMap {
                    put(UNIVERSE_ELIGIBLE, eligibleNow)
                    put(REENTRY_QUARANTINED, reentryQuarantinedById.getValue(candidate.assetId))
                    put(
                        REENTRY_CLEARANCE_EARNED,
                        reentryClearanceEarnedById.getValue(candidate.assetId),
                    )
                    STYLE_ASSIGNED_KEYS.forEachIndexed { index, key ->
                        put(key, eligibleNow && sizes[index] > PATH_EPSILON)
                    }
                }.toSortedMap(),
            )
        }
        val largeValueAllocations = eligible.associate { candidate ->
            val sizes = sizeAllocations.getValue(candidate.assetId)
            val styles = styleAllocations.getValue(candidate.assetId)
            candidate.assetId to (
                sizes[MEGA] * styles[MEGA] +
                    sizes[MID] * styles[MID]
                )
        }.filterValues { allocation -> allocation > 0.0 }
        require(largeValueAllocations.isNotEmpty()) {
            "The Morningstar large-cap value review selected no value allocation."
        }
        require(largeValueAllocations.size <= maximumConstituentCount(profile)) {
            "The Morningstar large-cap value proxy exceeded its persisted constituent bound."
        }
        val effectiveFloatAdjustedMultipliers = largeValueAllocations.mapValues { (assetId, allocation) ->
            val candidate = candidatesById.getValue(assetId)
            val currentIwf = positiveDecimal(
                candidate,
                StandardEquityMethodologySignalIds.INVESTABLE_WEIGHT_FACTOR,
            )
            allocation * roundedEffectiveFloatFactor(currentIwf) / currentIwf
        }
        val rankedIds = effectiveFloatAdjustedMultipliers.keys.sortedWith(
            compareByDescending<String> { assetId ->
                positiveDecimal(
                    candidatesById.getValue(assetId),
                    StandardEquityMethodologySignalIds.FLOAT_MARKET_CAP,
                ) * effectiveFloatAdjustedMultipliers.getValue(assetId)
            }.thenBy { assetId -> assetId },
        )
        return EquityMethodologyReconstitutionResult(
            selections = rankedIds.mapIndexed { index, assetId ->
                EquityMethodologySelection(assetId = assetId, rank = index + 1)
            },
            referenceMarketValueMultipliers = effectiveFloatAdjustedMultipliers,
            nextPathState = EquityMethodologyPathState(nextEntries),
        )
    }

    override fun targetWeights(input: EquityMethodologyWeightingInput): Map<String, Double> {
        val isComplianceAction = input.actionKind == ReferencePortfolioActionKind.CONSTRAINT_REWEIGHT
        val idealWeights = if (isComplianceAction) {
            require(abs(input.referenceMarketValues.values.sum() - 1.0) <= WEIGHT_EPSILON) {
                "A Morningstar RIC compliance action requires normalized live closing weights."
            }
            buildMap { putAll(input.referenceMarketValues.toSortedMap()) }
        } else {
            StandardEquityMethodologyComponents.proportionalWeights(input.referenceMarketValues)
        }
        val result = if (isComplianceAction) {
            euclideanRicProjection(
                idealWeights = idealWeights,
                individualCap = ricComplianceTargetIndividualCap(input.profile),
                aggregateThreshold = ricComplianceTargetAggregateThreshold(input.profile),
                aggregateCap = ricComplianceTargetAggregateCap(input.profile),
                ratioCap = ricMaximumAdjustmentRatio(input.profile),
            )
        } else {
            euclideanRicProjection(
                idealWeights = idealWeights,
                individualCap = ricIndividualCap(input.profile),
                aggregateThreshold = ricAggregateThreshold(input.profile),
                aggregateCap = ricAggregateCap(input.profile),
                ratioCap = ricMaximumAdjustmentRatio(input.profile),
            ).also { weights ->
                require(weights.values.all { weight ->
                    weight <= ricIndividualCap(input.profile) + WEIGHT_EPSILON
                }) { "The scheduled Morningstar RIC ranking buffer exceeded 22.5%." }
            }
        }
        return result
    }

    override fun constraintReweightEffectiveDate(input: EquityMethodologyConstraintInput): LocalDate? {
        val observationDate = input.observationDate
        val month = observationDate.month.ordinal + 1
        if (month !in reconstitutionMonths(input.profile)) return null
        if (observationDate != MorningstarLargeCapValueCalendar.lastUsTradingDateOfMonth(
                observationDate.year,
                month,
            )
        ) return null
        require(abs(input.currentWeights.values.sum() - 1.0) <= WEIGHT_EPSILON) {
            "A Morningstar RIC compliance test requires normalized closing weights."
        }
        val individualBreach = input.currentWeights.values.any { weight ->
            weight > ricComplianceTestIndividualCap(input.profile)
        }
        val aggregateBreach = input.currentWeights.values
            .filter { weight -> weight > ricComplianceTestAggregateThreshold(input.profile) }
            .sum() > ricComplianceTestAggregateCap(input.profile)
        return if (individualBreach || aggregateBreach) {
            schedule.addTradingDays(observationDate, 1)
        } else {
            null
        }
    }

    override fun scheduledReconstitutionTransitionSteps(
        profile: EquityMethodologyProfile,
        action: EquityMethodologyScheduledAction,
    ): List<EquityMethodologyReconstitutionTransitionStep> {
        requireCanonical(
            action.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION,
            "a scheduled reconstitution transition",
        )
        val month = action.effectiveDate.month.ordinal + 1
        requireCanonical(
            action.effectiveDate == MorningstarLargeCapValueCalendar.quarterlyFinalApplicationDate(
                action.effectiveDate.year,
                month,
            ),
            "the canonical quarterly final transition date",
        )
        val transitionCount = transitionTradingDayCount(profile)
        return MorningstarLargeCapValueCalendar.quarterlyPartialApplicationDates(
            action.effectiveDate.year,
            month,
        ).mapIndexed { index, date ->
            EquityMethodologyReconstitutionTransitionStep(
                effectiveDate = date,
                completionFraction = (index + 1).toDouble() / transitionCount,
            )
        }
    }

    override fun corporateActionDecision(
        input: EquityMethodologyCorporateActionInput,
    ): EquityMethodologyCorporateActionDecision {
        requireCanonical(schedule.isTradingDate(input.event.effectiveDate), "a regular trading-day effective date")
        return when (input.event.kind) {
            ReferencePortfolioCorporateActionKind.TERMINAL_REMOVAL ->
                EquityMethodologyCorporateActionDecision(
                    removedAssetIds = setOf(input.event.primaryAssetId),
                )
            ReferencePortfolioCorporateActionKind.SPIN_OFF ->
                EquityMethodologyCorporateActionDecision(
                    addedAssetIds = setOf(requireNotNull(input.event.secondaryAssetId)),
                    transferredValueFraction = input.event.valueTransferFraction,
                    followUpRemovalDate = requireNotNull(input.event.followUpEffectiveDate),
                )
            ReferencePortfolioCorporateActionKind.MERGER -> mergerDecision(input)
        }
    }

    /**
     * The quarterly path state remains the replay authority. Between reviews, a stock/mixed
     * successor receives the target's index line and the next full review recomputes its packets.
     */
    private fun mergerDecision(
        input: EquityMethodologyCorporateActionInput,
    ): EquityMethodologyCorporateActionDecision {
        val targetId = input.event.primaryAssetId
        val acquirerId = requireNotNull(input.event.secondaryAssetId)
        val currentIds = input.currentConstituents.mapTo(hashSetOf(), EquityMethodologyCandidate::assetId)
        val transferredFraction = when (input.event.considerationKind) {
            ReferencePortfolioCorporateActionConsiderationKind.STOCK,
            ReferencePortfolioCorporateActionConsiderationKind.MIXED,
            -> input.event.valueTransferFraction
            ReferencePortfolioCorporateActionConsiderationKind.CASH -> 0.0
            ReferencePortfolioCorporateActionConsiderationKind.NONE ->
                error("A merger cannot have NONE consideration.")
        }
        return if (acquirerId in currentIds) {
            EquityMethodologyCorporateActionDecision(
                removedAssetIds = setOf(targetId),
                survivingAcquirerAssetId = acquirerId,
                transferredValueFraction = transferredFraction,
            )
        } else if (transferredFraction > 0.0) {
            EquityMethodologyCorporateActionDecision(
                removedAssetIds = setOf(targetId),
                addedAssetIds = setOf(acquirerId),
                transferredValueFraction = transferredFraction,
            )
        } else {
            EquityMethodologyCorporateActionDecision(removedAssetIds = setOf(targetId))
        }
    }

    private fun passesCapitalAndFloatThresholds(
        profile: EquityMethodologyProfile,
        candidate: EquityMethodologyCandidate,
        totalUniverseFloatMarketCap: Double,
        addition: Boolean,
    ): Boolean {
        val companyMarketCap = positiveDecimal(
            candidate,
            StandardEquityMethodologySignalIds.TOTAL_COMPANY_MARKET_CAP,
        )
        val floatMarketCap = positiveDecimal(candidate, StandardEquityMethodologySignalIds.FLOAT_MARKET_CAP)
        val floatFraction = decimal(candidate, StandardEquityMethodologySignalIds.INVESTABLE_WEIGHT_FACTOR)
        require(floatFraction > 0.0 && floatFraction <= 1.0)
        val universeFloatMarketCapShare = floatMarketCap / totalUniverseFloatMarketCap
        return if (addition) {
            companyMarketCap >= minimumAdditionCompanyMarketCap(profile) &&
                (floatFraction >= minimumAdditionFloatFraction(profile) ||
                    universeFloatMarketCapShare >= minimumAdditionUniverseFloatMarketCapShare(profile))
        } else {
            companyMarketCap >= minimumDeletionCompanyMarketCap(profile) &&
                !(floatFraction < minimumDeletionFloatFraction(profile) &&
                    universeFloatMarketCapShare < minimumDeletionUniverseFloatMarketCapShare(profile))
        }
    }

    private fun volumeToFloatMarketCapRatio(candidate: EquityMethodologyCandidate): Double {
        val averageDailyValueTraded = decimal(
            candidate,
            StandardEquityMethodologySignalIds
                .TRAILING_125_TRADING_DAY_AVERAGE_DAILY_VALUE_TRADED,
        )
        require(averageDailyValueTraded.isFinite() && averageDailyValueTraded >= 0.0)
        return averageDailyValueTraded / positiveDecimal(
            candidate,
            StandardEquityMethodologySignalIds.FLOAT_MARKET_CAP,
        )
    }

    /** Four-ranking re-entry clearance is stricter than the incumbent deletion screen. */
    private fun passesReentryClearanceThresholds(
        profile: EquityMethodologyProfile,
        candidate: EquityMethodologyCandidate,
        totalUniverseFloatMarketCap: Double,
        volumeRatio: Double,
    ): Boolean {
        val companyMarketCap = positiveDecimal(
            candidate,
            StandardEquityMethodologySignalIds.TOTAL_COMPANY_MARKET_CAP,
        )
        val floatMarketCap = positiveDecimal(
            candidate,
            StandardEquityMethodologySignalIds.FLOAT_MARKET_CAP,
        )
        val floatFraction = decimal(
            candidate,
            StandardEquityMethodologySignalIds.INVESTABLE_WEIGHT_FACTOR,
        )
        require(floatFraction > 0.0 && floatFraction <= 1.0)
        val universeFloatMarketCapShare = floatMarketCap / totalUniverseFloatMarketCap
        return companyMarketCap >= minimumDeletionCompanyMarketCap(profile) &&
            floatFraction >= minimumDeletionFloatFraction(profile) &&
            universeFloatMarketCapShare > minimumDeletionUniverseFloatMarketCapShare(profile) &&
            volumeRatio >= minimumDeletionVolumeRatio(profile)
    }

    private fun normalizedCounterProgress(
        entry: EquityMethodologyPathEntry,
        key: String,
        rankingCount: Int,
    ): Double {
        val progress = entry.decimalValues.getValue(key)
        val completedRankings = progress * rankingCount
        require(
            abs(completedRankings - floor(completedRankings + 0.5)) <= PATH_EPSILON,
        ) { "The Morningstar path counter $key is not on a canonical ranking step." }
        return progress
    }

    private fun cumulativeMarketCapMidpoints(
        candidates: List<EquityMethodologyCandidate>,
    ): Map<String, Double> {
        val ordered = candidates.sortedWith(
            compareByDescending<EquityMethodologyCandidate> { candidate ->
                positiveDecimal(candidate, StandardEquityMethodologySignalIds.TOTAL_COMPANY_MARKET_CAP)
            }.thenBy(EquityMethodologyCandidate::assetId),
        )
        val total = ordered.sumOf { candidate ->
            positiveDecimal(candidate, StandardEquityMethodologySignalIds.TOTAL_COMPANY_MARKET_CAP)
        }
        require(total.isFinite() && total > 0.0)
        var cumulative = 0.0
        return buildMap {
            ordered.forEach { candidate ->
                val marketCap = positiveDecimal(
                    candidate,
                    StandardEquityMethodologySignalIds.TOTAL_COMPANY_MARKET_CAP,
                )
                put(candidate.assetId, ((cumulative + marketCap / 2.0) / total).coerceIn(0.0, 1.0))
                cumulative += marketCap
            }
        }
    }

    private fun nextSizeAllocation(
        profile: EquityMethodologyProfile,
        cumulativeMidpoint: Double,
        previous: DoubleArray?,
    ): DoubleArray {
        require(cumulativeMidpoint in 0.0..1.0)
        val freshTarget = when {
            cumulativeMidpoint <= megaCapTarget(profile) -> MEGA
            cumulativeMidpoint <= midCapTarget(profile) -> MID
            cumulativeMidpoint <= smallCapTarget(profile) -> SMALL
            else -> MICRO
        }
        if (previous == null || abs(previous.sum() - 1.0) > PATH_EPSILON) {
            return oneHotSize(freshTarget)
        }
        return when {
            cumulativeMidpoint <= megaAdjacentCoreLower(profile) -> oneHotSize(MEGA)
            cumulativeMidpoint <= megaBandLower(profile) ->
                moveSizePacket(profile, previous, MEGA, MID, MEGA)
            cumulativeMidpoint <= megaBandUpper(profile) ->
                holdSizeBand(previous, MEGA, MID, freshTarget)
            cumulativeMidpoint <= megaAdjacentCoreUpper(profile) ->
                moveSizePacket(profile, previous, MEGA, MID, MID)
            cumulativeMidpoint <= midBandUpper(profile) ->
                holdSizeBand(previous, MID, SMALL, freshTarget)
            cumulativeMidpoint <= midAdjacentCoreUpper(profile) ->
                moveSizePacket(profile, previous, MID, SMALL, SMALL)
            cumulativeMidpoint <= smallBandUpper(profile) ->
                holdSizeBand(previous, SMALL, MICRO, freshTarget)
            else -> moveSizePacket(profile, previous, SMALL, MICRO, MICRO)
        }
    }

    private fun holdSizeBand(
        previous: DoubleArray,
        left: Int,
        right: Int,
        freshTarget: Int,
    ): DoubleArray {
        val result = DoubleArray(SIZE_SEGMENT_COUNT)
        previous.forEachIndexed { index, allocation ->
            if (index == left || index == right) {
                result[index] += allocation
            } else {
                // A packet that jumped across the band pair is placed directly in the current
                // full-assignment segment; adjacent packets retain their independent history.
                result[freshTarget] += allocation
            }
        }
        require(abs(result.sum() - 1.0) <= PATH_EPSILON)
        return result
    }

    private fun moveSizePacket(
        profile: EquityMethodologyProfile,
        previous: DoubleArray,
        left: Int,
        right: Int,
        toward: Int,
    ): DoubleArray {
        require(toward == left || toward == right)
        val result = DoubleArray(SIZE_SEGMENT_COUNT)
        val opposite = if (toward == left) right else left
        previous.forEachIndexed { index, allocation ->
            when (index) {
                toward -> result[toward] += allocation
                opposite -> {
                    val transfer = min(packetFraction(profile), allocation)
                    result[opposite] += allocation - transfer
                    result[toward] += transfer
                }
                // Non-adjacent packets do not wait at an unrelated boundary.
                else -> result[toward] += allocation
            }
        }
        require(abs(result.sum() - 1.0) <= PATH_EPSILON)
        return result
    }

    private fun nextStyleAllocations(
        profile: EquityMethodologyProfile,
        eligibleCandidates: List<EquityMethodologyCandidate>,
        sizeAllocations: Map<String, DoubleArray>,
        previousById: Map<String, EquityMethodologyPathEntry>,
    ): Map<String, DoubleArray> {
        val result = eligibleCandidates.associate { candidate ->
            candidate.assetId to DoubleArray(SIZE_SEGMENT_COUNT) { 0.5 }
        }
        repeat(SIZE_SEGMENT_COUNT) { segment ->
            val members = eligibleCandidates.filter { candidate ->
                sizeAllocations.getValue(candidate.assetId)[segment] > PATH_EPSILON
            }
            if (members.isEmpty()) return@repeat
            val activeMarketCaps = members.associate { candidate ->
                candidate.assetId to positiveDecimal(
                    candidate,
                    StandardEquityMethodologySignalIds.TOTAL_COMPANY_MARKET_CAP,
                ) * sizeAllocations.getValue(candidate.assetId)[segment]
            }
            val activeRanks = activeStyleRanks(profile, members, activeMarketCaps)
            members.forEach { candidate ->
                val previous = previousById[candidate.assetId]
                val previousSizes = previous?.let(::sizeAllocation)
                val previouslyAssigned = previous?.booleanValues?.get(STYLE_ASSIGNED_KEYS[segment]) == true &&
                    previousSizes != null && previousSizes[segment] > PATH_EPSILON
                val nextStyle = if (!previouslyAssigned) {
                    if (activeRanks.getValue(candidate.assetId) > NEW_SECURITY_VALUE_THRESHOLD) 1.0 else 0.0
                } else {
                    val oldStyle = requireNotNull(previous.decimalValues[STYLE_KEYS[segment]])
                    when {
                        activeRanks.getValue(candidate.assetId) > styleBandUpper(profile) ->
                            min(1.0, oldStyle + packetFraction(profile))
                        activeRanks.getValue(candidate.assetId) < styleBandLower(profile) ->
                            (oldStyle - packetFraction(profile)).coerceAtLeast(0.0)
                        else -> oldStyle
                    }
                }
                result.getValue(candidate.assetId)[segment] = nextStyle
            }
        }
        return result
    }

    private fun activeStyleRanks(
        profile: EquityMethodologyProfile,
        candidates: List<EquityMethodologyCandidate>,
        activeMarketCaps: Map<String, Double>,
    ): Map<String, Double> {
        fun z(
            signalId: String,
            zeroFloor: Boolean = false,
        ): Map<String, Double> = winsorizedZScores(
            candidates = candidates,
            signalId = signalId,
            lowerQuantile = winsorLower(profile),
            upperQuantile = winsorUpper(profile),
            zeroFloor = zeroFloor,
        )
        val bookToPrice = z(
            StandardEquityMethodologySignalIds.BOOK_TO_PRICE,
            // The methodology assigns zero, rather than a negative factor value, when book
            // equity is negative. Keep the shared raw accounting signal unchanged.
            zeroFloor = true,
        )
        val futureEarningsToPrice = z(StandardEquityMethodologySignalIds.FUTURE_EARNINGS_TO_PRICE)
        val historicalEarningsToPrice = z(StandardEquityMethodologySignalIds.HISTORICAL_EARNINGS_TO_PRICE)
        val dividendToPrice = z(StandardEquityMethodologySignalIds.DIVIDEND_TO_PRICE)
        val salesToPrice = z(StandardEquityMethodologySignalIds.SALES_TO_PRICE)
        val futureLongGrowth = z(StandardEquityMethodologySignalIds.FUTURE_LONG_TERM_EARNINGS_GROWTH)
        val futureShortGrowth = z(StandardEquityMethodologySignalIds.FUTURE_SHORT_TERM_EARNINGS_GROWTH)
        val historicalEarningsGrowth = z(
            StandardEquityMethodologySignalIds.THREE_YEAR_HISTORICAL_EARNINGS_GROWTH,
        )
        val historicalSalesGrowth = z(
            StandardEquityMethodologySignalIds.THREE_YEAR_HISTORICAL_SALES_GROWTH,
        )
        val investmentToAssets = z(StandardEquityMethodologySignalIds.CURRENT_INVESTMENT_TO_ASSETS)
        val returnOnAssets = z(StandardEquityMethodologySignalIds.RETURN_ON_ASSETS)
        val valueScores = buildMap {
            candidates.forEach { candidate ->
                val id = candidate.assetId
                val earningsToPrice = TWO_THIRDS * futureEarningsToPrice.getValue(id) +
                    ONE_THIRD * historicalEarningsToPrice.getValue(id)
                val valueOne = TWO_THIRDS * earningsToPrice + ONE_THIRD * bookToPrice.getValue(id)
                val valueTwo = TWO_THIRDS * salesToPrice.getValue(id) +
                    ONE_THIRD * dividendToPrice.getValue(id)
                put(id, TWO_THIRDS * valueOne + ONE_THIRD * valueTwo)
            }
        }
        val growthScores = buildMap {
            candidates.forEach { candidate ->
                val id = candidate.assetId
                val futureGrowth = ONE_THIRD * futureLongGrowth.getValue(id) +
                    ONE_THIRD * futureShortGrowth.getValue(id) +
                    ONE_SIXTH * investmentToAssets.getValue(id) +
                    ONE_SIXTH * returnOnAssets.getValue(id)
                val historicalGrowth = TWO_THIRDS * historicalSalesGrowth.getValue(id) +
                    ONE_THIRD * historicalEarningsGrowth.getValue(id)
                put(id, TWO_THIRDS * futureGrowth + ONE_THIRD * historicalGrowth)
            }
        }
        val relativeValue = capitalizationWeightedMidpointRanks(
            scores = valueScores,
            activeMarketCaps = activeMarketCaps,
            descending = false,
        )
        val relativeGrowth = capitalizationWeightedMidpointRanks(
            scores = growthScores,
            activeMarketCaps = activeMarketCaps,
            descending = true,
        )
        return candidates.associate { candidate ->
            candidate.assetId to (
                (relativeValue.getValue(candidate.assetId) +
                    relativeGrowth.getValue(candidate.assetId)) / 2.0
                )
        }
    }

    private fun winsorizedZScores(
        candidates: List<EquityMethodologyCandidate>,
        signalId: String,
        lowerQuantile: Double,
        upperQuantile: Double,
        zeroFloor: Boolean,
    ): Map<String, Double> {
        val original = candidates.associate { candidate ->
            val value = decimal(candidate, signalId)
            candidate.assetId to if (zeroFloor) value.coerceAtLeast(0.0) else value
        }
        val sortedValues = original.values.sorted()
        val lower = quantile(sortedValues, lowerQuantile)
        val upper = quantile(sortedValues, upperQuantile)
        val winsorized = original.mapValues { (_, value) -> value.coerceIn(lower, upper) }
        val mean = winsorized.values.sum() / winsorized.size
        val variance = winsorized.values.sumOf { value ->
            val centered = value - mean
            centered * centered
        } / winsorized.size
        val standardDeviation = sqrt(variance)
        return if (standardDeviation <= STYLE_EPSILON) {
            winsorized.keys.associateWith { 0.0 }
        } else {
            winsorized.mapValues { (_, value) -> (value - mean) / standardDeviation }
        }
    }

    private fun quantile(sortedValues: List<Double>, fraction: Double): Double {
        require(sortedValues.isNotEmpty() && fraction in 0.0..1.0)
        val position = (sortedValues.lastIndex * fraction)
        val lowerIndex = position.toInt()
        val upperIndex = (lowerIndex + 1).coerceAtMost(sortedValues.lastIndex)
        val interpolation = position - lowerIndex
        return sortedValues[lowerIndex] * (1.0 - interpolation) +
            sortedValues[upperIndex] * interpolation
    }

    private fun capitalizationWeightedMidpointRanks(
        scores: Map<String, Double>,
        activeMarketCaps: Map<String, Double>,
        descending: Boolean,
    ): Map<String, Double> {
        require(scores.keys == activeMarketCaps.keys)
        val comparator = if (descending) {
            compareByDescending<String> { assetId -> scores.getValue(assetId) }.thenBy { it }
        } else {
            compareBy<String> { assetId -> scores.getValue(assetId) }.thenBy { it }
        }
        val ordered = scores.keys.sortedWith(comparator)
        val totalMarketCap = activeMarketCaps.values.sum()
        require(totalMarketCap.isFinite() && totalMarketCap > 0.0)
        var cumulative = 0.0
        val result = mutableMapOf<String, Double>()
        var index = 0
        while (index < ordered.size) {
            val score = scores.getValue(ordered[index])
            var end = index + 1
            while (end < ordered.size && scores.getValue(ordered[end]) == score) end += 1
            val tiedIds = ordered.subList(index, end)
            val tiedMarketCap = tiedIds.sumOf(activeMarketCaps::getValue)
            val rank = (cumulative + tiedMarketCap / 2.0) / totalMarketCap
            tiedIds.forEach { assetId -> result[assetId] = rank.coerceIn(0.0, 1.0) }
            cumulative += tiedMarketCap
            index = end
        }
        return buildMap { putAll(result.toSortedMap()) }
    }

    /**
     * Exact squared-error RIC target projection for the one-security-per-company host universe.
     *
     * For a fixed set of weights strictly above the aggregate threshold, box-simplex projection
     * is the Euclidean minimizer. Symmetry and monotonicity make that set a prefix of descending
     * ideal weight (asset ID breaks equal-weight ties), so only the bounded feasible prefixes are
     * enumerated. Threshold equality belongs to the small set; the large lower bound is the next
     * representable Double above the published threshold.
     */
    private fun euclideanRicProjection(
        idealWeights: Map<String, Double>,
        individualCap: Double,
        aggregateThreshold: Double,
        aggregateCap: Double,
        ratioCap: Double,
    ): Map<String, Double> {
        require(idealWeights.isNotEmpty())
        require(abs(idealWeights.values.sum() - 1.0) <= WEIGHT_EPSILON)
        require(idealWeights.values.all { weight -> weight.isFinite() && weight > 0.0 })
        require(individualCap > aggregateThreshold && aggregateThreshold > 0.0)
        require(aggregateCap in aggregateThreshold..1.0 && ratioCap >= 1.0)
        val ideal = buildMap { putAll(idealWeights.toSortedMap()) }
        if (isRicFeasible(ideal, ideal, individualCap, aggregateThreshold, aggregateCap, ratioCap)) {
            return ideal
        }

        val rankedIds = ideal.keys.sortedWith(
            compareByDescending<String>(ideal::getValue).thenBy { assetId -> assetId },
        )
        val strictLargeLower = nextRepresentableWeightAbove(aggregateThreshold)
        var bestWeights: Map<String, Double>? = null
        var bestObjective = Double.POSITIVE_INFINITY
        for (largeCount in 0..rankedIds.size) {
            if (largeCount * strictLargeLower > aggregateCap) break
            val largeIds = rankedIds.take(largeCount).toSet()
            val lowerBounds = ideal.keys.associateWith { assetId ->
                if (assetId in largeIds) strictLargeLower else 0.0
            }
            val upperBounds = ideal.mapValues { (assetId, weight) ->
                val commonUpper = min(individualCap, weight * ratioCap)
                if (assetId in largeIds) commonUpper else min(aggregateThreshold, commonUpper)
            }
            if (ideal.keys.any { assetId ->
                    lowerBounds.getValue(assetId) > upperBounds.getValue(assetId)
                }
            ) continue
            val candidate = projectFixedRicPrefix(
                ideal = ideal,
                lowerBounds = lowerBounds,
                upperBounds = upperBounds,
                largeIds = largeIds,
                aggregateCap = aggregateCap,
            ) ?: continue
            if (!isRicFeasible(
                    weights = candidate,
                    idealWeights = ideal,
                    individualCap = individualCap,
                    aggregateThreshold = aggregateThreshold,
                    aggregateCap = aggregateCap,
                    ratioCap = ratioCap,
                ) || candidate.filterValues { weight -> weight > aggregateThreshold }.keys != largeIds
            ) continue
            val objective = candidate.entries.sumOf { (assetId, weight) ->
                val difference = weight - ideal.getValue(assetId)
                difference * difference
            }
            val incumbent = bestWeights
            if (incumbent == null || objective < bestObjective - OBJECTIVE_TIE_EPSILON ||
                abs(objective - bestObjective) <= OBJECTIVE_TIE_EPSILON &&
                lexicographicallyPrecedes(candidate, incumbent)
            ) {
                bestWeights = candidate
                bestObjective = objective
            }
        }
        return requireNotNull(bestWeights) {
            "The Morningstar RIC constraints have no feasible Euclidean target."
        }.also { weights ->
            require(isRicFeasible(
                weights = weights,
                idealWeights = ideal,
                individualCap = individualCap,
                aggregateThreshold = aggregateThreshold,
                aggregateCap = aggregateCap,
                ratioCap = ratioCap,
            ))
        }
    }

    /**
     * Minimizes the convex large-group total value function. Its unconstrained minimizer is the
     * joint box-simplex projection; if the published aggregate interval cuts that minimizer off,
     * convexity puts the optimum at the nearest interval endpoint and the two groups project
     * independently there.
     */
    private fun projectFixedRicPrefix(
        ideal: Map<String, Double>,
        lowerBounds: Map<String, Double>,
        upperBounds: Map<String, Double>,
        largeIds: Set<String>,
        aggregateCap: Double,
    ): Map<String, Double>? {
        val lowerTotal = lowerBounds.values.sum()
        val upperTotal = upperBounds.values.sum()
        if (lowerTotal > 1.0 + PROJECTION_FEASIBILITY_EPSILON ||
            upperTotal < 1.0 - PROJECTION_FEASIBILITY_EPSILON
        ) return null
        val smallIds = ideal.keys - largeIds
        val largeLowerTotal = largeIds.sumOf(lowerBounds::getValue)
        val largeUpperTotal = largeIds.sumOf(upperBounds::getValue)
        val smallLowerTotal = smallIds.sumOf(lowerBounds::getValue)
        val smallUpperTotal = smallIds.sumOf(upperBounds::getValue)
        val feasibleLargeLow = max(largeLowerTotal, 1.0 - smallUpperTotal)
        val feasibleLargeHigh = min(min(largeUpperTotal, aggregateCap), 1.0 - smallLowerTotal)
        if (feasibleLargeLow > feasibleLargeHigh + PROJECTION_FEASIBILITY_EPSILON) return null

        val joint = projectToBoxSimplex(ideal, lowerBounds, upperBounds, 1.0) ?: return null
        val unconstrainedLargeTotal = largeIds.sumOf(joint::getValue)
        if (unconstrainedLargeTotal in feasibleLargeLow..feasibleLargeHigh) return joint
        val optimalLargeTotal = unconstrainedLargeTotal.coerceIn(feasibleLargeLow, feasibleLargeHigh)
        val largeProjection = projectToBoxSimplex(
            values = ideal.filterKeys(largeIds::contains),
            lowerBounds = lowerBounds.filterKeys(largeIds::contains),
            upperBounds = upperBounds.filterKeys(largeIds::contains),
            targetTotal = optimalLargeTotal,
        ) ?: return null
        val smallProjection = projectToBoxSimplex(
            values = ideal.filterKeys(smallIds::contains),
            lowerBounds = lowerBounds.filterKeys(smallIds::contains),
            upperBounds = upperBounds.filterKeys(smallIds::contains),
            targetTotal = 1.0 - optimalLargeTotal,
        ) ?: return null
        return buildMap {
            putAll(largeProjection)
            putAll(smallProjection)
        }.toSortedMap()
    }

    /** Euclidean projection onto lower/upper boxes intersected with one exact-sum hyperplane. */
    private fun projectToBoxSimplex(
        values: Map<String, Double>,
        lowerBounds: Map<String, Double>,
        upperBounds: Map<String, Double>,
        targetTotal: Double,
    ): Map<String, Double>? {
        require(values.keys == lowerBounds.keys && values.keys == upperBounds.keys)
        if (values.isEmpty()) {
            return if (abs(targetTotal) <= PROJECTION_FEASIBILITY_EPSILON) emptyMap() else null
        }
        val ids = values.keys.sorted()
        val lowerTotal = ids.sumOf(lowerBounds::getValue)
        val upperTotal = ids.sumOf(upperBounds::getValue)
        if (targetTotal < lowerTotal - PROJECTION_FEASIBILITY_EPSILON ||
            targetTotal > upperTotal + PROJECTION_FEASIBILITY_EPSILON
        ) return null
        if (targetTotal == lowerTotal) {
            return repairedBoundedTotal(lowerBounds, lowerBounds, upperBounds, targetTotal)
        }
        if (targetTotal == upperTotal) {
            return repairedBoundedTotal(upperBounds, lowerBounds, upperBounds, targetTotal)
        }

        var lambdaLow = ids.minOf { assetId ->
            values.getValue(assetId) - upperBounds.getValue(assetId)
        }
        var lambdaHigh = ids.maxOf { assetId ->
            values.getValue(assetId) - lowerBounds.getValue(assetId)
        }
        repeat(PROJECTION_BISECTION_STEPS) {
            val lambda = (lambdaLow + lambdaHigh) / 2.0
            val total = ids.sumOf { assetId ->
                (values.getValue(assetId) - lambda).coerceIn(
                    lowerBounds.getValue(assetId),
                    upperBounds.getValue(assetId),
                )
            }
            if (total > targetTotal) lambdaLow = lambda else lambdaHigh = lambda
        }
        val lambda = (lambdaLow + lambdaHigh) / 2.0
        val projected = ids.associateWith { assetId ->
            (values.getValue(assetId) - lambda).coerceIn(
                lowerBounds.getValue(assetId),
                upperBounds.getValue(assetId),
            )
        }
        return repairedBoundedTotal(projected, lowerBounds, upperBounds, targetTotal)
    }

    /** Removes the final floating-point sum residue in stable asset-ID order without leaving boxes. */
    private fun repairedBoundedTotal(
        weights: Map<String, Double>,
        lowerBounds: Map<String, Double>,
        upperBounds: Map<String, Double>,
        targetTotal: Double,
    ): Map<String, Double>? {
        val result = weights.toSortedMap().toMutableMap()
        var residue = targetTotal - result.values.sum()
        result.keys.sorted().forEach { assetId ->
            if (residue > 0.0) {
                val adjustment = min(
                    residue,
                    (upperBounds.getValue(assetId) - result.getValue(assetId)).coerceAtLeast(0.0),
                )
                result[assetId] = result.getValue(assetId) + adjustment
                residue -= adjustment
            } else if (residue < 0.0) {
                val adjustment = min(
                    -residue,
                    (result.getValue(assetId) - lowerBounds.getValue(assetId)).coerceAtLeast(0.0),
                )
                result[assetId] = result.getValue(assetId) - adjustment
                residue += adjustment
            }
        }
        if (abs(residue) > PROJECTION_FEASIBILITY_EPSILON) return null
        return buildMap { putAll(result.toSortedMap()) }
    }

    private fun isRicFeasible(
        weights: Map<String, Double>,
        idealWeights: Map<String, Double>,
        individualCap: Double,
        aggregateThreshold: Double,
        aggregateCap: Double,
        ratioCap: Double,
    ): Boolean =
        weights.keys == idealWeights.keys &&
            abs(weights.values.sum() - 1.0) <= WEIGHT_EPSILON &&
            weights.values.all { weight ->
                weight.isFinite() && weight >= 0.0 && weight <= individualCap + WEIGHT_EPSILON
            } &&
            weights.filterValues { weight -> weight > aggregateThreshold }.values.sum() <=
            aggregateCap + WEIGHT_EPSILON &&
            weights.all { (assetId, weight) ->
                weight <= idealWeights.getValue(assetId) * ratioCap + WEIGHT_EPSILON
            }

    private fun lexicographicallyPrecedes(
        candidate: Map<String, Double>,
        incumbent: Map<String, Double>,
    ): Boolean {
        candidate.keys.sorted().forEach { assetId ->
            val candidateWeight = candidate.getValue(assetId)
            val incumbentWeight = incumbent.getValue(assetId)
            if (candidateWeight < incumbentWeight) return true
            if (candidateWeight > incumbentWeight) return false
        }
        return false
    }

    private fun nextRepresentableWeightAbove(weight: Double): Double {
        require(weight.isFinite() && weight > 0.0)
        return Double.fromBits(weight.toBits() + 1L)
    }

    private fun sizeAllocation(entry: EquityMethodologyPathEntry): DoubleArray =
        DoubleArray(SIZE_SEGMENT_COUNT) { index ->
            entry.decimalValues.getValue(SIZE_KEYS[index])
        }

    private fun oneHotSize(index: Int): DoubleArray =
        DoubleArray(SIZE_SEGMENT_COUNT) { candidateIndex -> if (candidateIndex == index) 1.0 else 0.0 }

    private fun decimal(candidate: EquityMethodologyCandidate, id: String): Double =
        candidate.signals.requireDecimal(id)

    private fun positiveDecimal(candidate: EquityMethodologyCandidate, id: String): Double =
        decimal(candidate, id).also { value -> require(value.isFinite() && value > 0.0) }

    /** Published EFF rounding applied after the pure size/style packet allocation is known. */
    private fun roundedEffectiveFloatFactor(currentFloatFraction: Double): Double {
        require(currentFloatFraction > 0.0 && currentFloatFraction <= 1.0)
        val increment = when {
            currentFloatFraction >= HIGH_FLOAT_ROUNDING_THRESHOLD -> HIGH_FLOAT_ROUNDING_INCREMENT
            currentFloatFraction >= LOW_FLOAT_ROUNDING_THRESHOLD -> LOW_FLOAT_ROUNDING_INCREMENT
            else -> VERY_LOW_FLOAT_ROUNDING_INCREMENT
        }
        val rounded = floor(
            currentFloatFraction / increment + 0.5 + EFFECTIVE_FLOAT_ROUNDING_EPSILON,
        ) * increment
        require(rounded > 0.0) {
            "An eligible Morningstar security must retain a positive rounded effective float factor."
        }
        return rounded.coerceAtMost(1.0)
    }

    private fun requireCanonical(condition: Boolean, rule: String) =
        require(condition) { "Unsupported equity methodology: Morningstar large-cap value v2 requires $rule." }

    private fun maximumConstituentCount(profile: EquityMethodologyProfile) =
        profile.parameters.integers.getValue("maximumConstituentCount")
    private fun transitionTradingDayCount(profile: EquityMethodologyProfile) =
        profile.parameters.integers.getValue("transitionTradingDayCount")
    private fun reentryClearanceRankingCount(profile: EquityMethodologyProfile) =
        profile.parameters.integers.getValue("reentryClearanceRankingCount")
    private fun lowVolumeDeletionRankingCount(profile: EquityMethodologyProfile) =
        profile.parameters.integers.getValue("lowVolumeDeletionRankingCount")
    private fun reconstitutionMonths(profile: EquityMethodologyProfile) =
        MorningstarLargeCapValueSchedule.reconstitutionMonths(profile)
    private fun d(profile: EquityMethodologyProfile, key: String) = profile.parameters.decimals.getValue(key)
    private fun minimumAdditionCompanyMarketCap(p: EquityMethodologyProfile) = d(p, "minimumAdditionCompanyMarketCap")
    private fun minimumDeletionCompanyMarketCap(p: EquityMethodologyProfile) = d(p, "minimumDeletionCompanyMarketCap")
    private fun minimumAdditionFloatFraction(p: EquityMethodologyProfile) = d(p, "minimumAdditionFloatFraction")
    private fun minimumDeletionFloatFraction(p: EquityMethodologyProfile) = d(p, "minimumDeletionFloatFraction")
    private fun minimumAdditionUniverseFloatMarketCapShare(p: EquityMethodologyProfile) =
        d(p, "minimumAdditionUniverseFloatMarketCapShare")
    private fun minimumDeletionUniverseFloatMarketCapShare(p: EquityMethodologyProfile) =
        d(p, "minimumDeletionUniverseFloatMarketCapShare")
    private fun minimumAdditionVolumeRatio(p: EquityMethodologyProfile) =
        d(p, "minimumAdditionVolumeRatio")
    private fun minimumDeletionVolumeRatio(p: EquityMethodologyProfile) =
        d(p, "minimumDeletionVolumeRatio")
    private fun megaCapTarget(p: EquityMethodologyProfile) = d(p, "megaCapTarget")
    private fun midCapTarget(p: EquityMethodologyProfile) = d(p, "midCapTarget")
    private fun smallCapTarget(p: EquityMethodologyProfile) = d(p, "smallCapTarget")
    private fun megaBandLower(p: EquityMethodologyProfile) = d(p, "megaBandLower")
    private fun megaBandUpper(p: EquityMethodologyProfile) = d(p, "megaBandUpper")
    private fun megaAdjacentCoreLower(p: EquityMethodologyProfile) = d(p, "megaAdjacentCoreLower")
    private fun megaAdjacentCoreUpper(p: EquityMethodologyProfile) = d(p, "megaAdjacentCoreUpper")
    private fun midBandLower(p: EquityMethodologyProfile) = d(p, "midBandLower")
    private fun midBandUpper(p: EquityMethodologyProfile) = d(p, "midBandUpper")
    private fun midAdjacentCoreLower(p: EquityMethodologyProfile) = d(p, "midAdjacentCoreLower")
    private fun midAdjacentCoreUpper(p: EquityMethodologyProfile) = d(p, "midAdjacentCoreUpper")
    private fun smallBandLower(p: EquityMethodologyProfile) = d(p, "smallBandLower")
    private fun smallBandUpper(p: EquityMethodologyProfile) = d(p, "smallBandUpper")
    private fun smallAdjacentCoreLower(p: EquityMethodologyProfile) = d(p, "smallAdjacentCoreLower")
    private fun smallAdjacentCoreUpper(p: EquityMethodologyProfile) = d(p, "smallAdjacentCoreUpper")
    private fun styleBandLower(p: EquityMethodologyProfile) = d(p, "styleBandLower")
    private fun styleBandUpper(p: EquityMethodologyProfile) = d(p, "styleBandUpper")
    private fun packetFraction(p: EquityMethodologyProfile) = d(p, "packetFraction")
    private fun winsorLower(p: EquityMethodologyProfile) = d(p, "winsorLower")
    private fun winsorUpper(p: EquityMethodologyProfile) = d(p, "winsorUpper")
    private fun ricIndividualCap(p: EquityMethodologyProfile) = d(p, "ricIndividualCap")
    private fun ricAggregateThreshold(p: EquityMethodologyProfile) = d(p, "ricAggregateThreshold")
    private fun ricAggregateCap(p: EquityMethodologyProfile) = d(p, "ricAggregateCap")
    private fun ricComplianceTestIndividualCap(p: EquityMethodologyProfile) =
        d(p, "ricComplianceTestIndividualCap")
    private fun ricComplianceTestAggregateThreshold(p: EquityMethodologyProfile) =
        d(p, "ricComplianceTestAggregateThreshold")
    private fun ricComplianceTestAggregateCap(p: EquityMethodologyProfile) =
        d(p, "ricComplianceTestAggregateCap")
    private fun ricComplianceTargetIndividualCap(p: EquityMethodologyProfile) =
        d(p, "ricComplianceTargetIndividualCap")
    private fun ricComplianceTargetAggregateThreshold(p: EquityMethodologyProfile) =
        d(p, "ricComplianceTargetAggregateThreshold")
    private fun ricComplianceTargetAggregateCap(p: EquityMethodologyProfile) =
        d(p, "ricComplianceTargetAggregateCap")
    private fun ricMaximumAdjustmentRatio(p: EquityMethodologyProfile) = d(p, "ricMaximumAdjustmentRatio")

    private const val MEGA = 0
    private const val MID = 1
    private const val SMALL = 2
    private const val MICRO = 3
    private const val SIZE_SEGMENT_COUNT = 4
    private const val PATH_EPSILON = 1e-10
    private const val STYLE_EPSILON = 1e-14
    private const val WEIGHT_EPSILON = 1e-10
    private const val PROJECTION_FEASIBILITY_EPSILON = 1e-12
    private const val OBJECTIVE_TIE_EPSILON = 1e-18
    private const val EPSILON = 1e-12
    private const val PROJECTION_BISECTION_STEPS = 128
    private const val NEW_SECURITY_VALUE_THRESHOLD = 0.5
    private const val HIGH_FLOAT_ROUNDING_THRESHOLD = 0.10
    private const val LOW_FLOAT_ROUNDING_THRESHOLD = 0.01
    private const val HIGH_FLOAT_ROUNDING_INCREMENT = 0.05
    private const val LOW_FLOAT_ROUNDING_INCREMENT = 0.01
    private const val VERY_LOW_FLOAT_ROUNDING_INCREMENT = 0.001
    private const val EFFECTIVE_FLOAT_ROUNDING_EPSILON = 1e-12
    private const val ONE_THIRD = 1.0 / 3.0
    private const val TWO_THIRDS = 2.0 / 3.0
    private const val ONE_SIXTH = 1.0 / 6.0
    private const val UNIVERSE_ELIGIBLE = "universeEligible"
    private const val REENTRY_QUARANTINED = "reentryQuarantined"
    private const val REENTRY_CLEARANCE_EARNED = "reentryClearanceEarned"
    private const val REENTRY_CLEARANCE_PROGRESS = "reentryClearanceProgress"
    private const val LOW_VOLUME_DELETION_PROGRESS = "lowVolumeDeletionProgress"
    private const val RANKING_PRICE_POLICY = "rankingPricePolicy"
    private const val FIRST_FRIDAY_CLOSE_LICENSED_RANDOM_DAY_PROXY =
        "FIRST_FRIDAY_CLOSE_LICENSED_RANDOM_DAY_PROXY"
    private const val MODEL_ASSUMPTION_ID =
        "morningstar-large-cap-value-data-and-committee-proxy-2026-07-v1"
    private val CANONICAL_EFFECTIVE_FROM = LocalDate(2026, 3, 25)
    private val CANONICAL_BENCHMARK_REF = BenchmarkRef("morningstar.us-large-cap-value", 2)
    private val SIZE_KEYS = listOf(
        "megaSizeMultiplier",
        "midSizeMultiplier",
        "smallSizeMultiplier",
        "microSizeMultiplier",
    )
    private val STYLE_KEYS = listOf(
        "megaValueMultiplier",
        "midValueMultiplier",
        "smallValueMultiplier",
        "microValueMultiplier",
    )
    private val STYLE_ASSIGNED_KEYS = listOf(
        "megaStyleAssigned",
        "midStyleAssigned",
        "smallStyleAssigned",
        "microStyleAssigned",
    )
    private val INTEGER_PARAMETER_KEYS = setOf(
        "lowVolumeDeletionRankingCount",
        "maximumConstituentCount",
        "reentryClearanceRankingCount",
        "transitionTradingDayCount",
    )
    private val INTEGER_SET_PARAMETER_KEYS = setOf("reconstitutionMonths")
    private val TEXT_PARAMETER_KEYS = setOf(RANKING_PRICE_POLICY)
    private val CANONICAL_DECIMAL_VALUES = mapOf(
        "minimumAdditionCompanyMarketCap" to 15_000_000.0,
        "minimumDeletionCompanyMarketCap" to 10_000_000.0,
        "minimumAdditionFloatFraction" to 0.125,
        "minimumDeletionFloatFraction" to 0.10,
        "minimumAdditionUniverseFloatMarketCapShare" to 0.00005,
        "minimumDeletionUniverseFloatMarketCapShare" to 0.00001,
        "minimumAdditionVolumeRatio" to 0.001,
        "minimumDeletionVolumeRatio" to 0.0008,
        "megaCapTarget" to 0.70,
        "midCapTarget" to 0.85,
        "smallCapTarget" to 0.98,
        "megaBandLower" to 0.64,
        "megaBandUpper" to 0.76,
        "megaAdjacentCoreLower" to 0.50,
        "megaAdjacentCoreUpper" to 0.81,
        "midBandLower" to 0.81,
        "midBandUpper" to 0.89,
        "midAdjacentCoreLower" to 0.76,
        "midAdjacentCoreUpper" to 0.96,
        "smallBandLower" to 0.96,
        "smallBandUpper" to 0.995,
        "smallAdjacentCoreLower" to 0.89,
        "smallAdjacentCoreUpper" to 1.0,
        "styleBandLower" to 0.3333,
        "styleBandUpper" to 0.6667,
        "packetFraction" to 0.5,
        "winsorLower" to 0.05,
        "winsorUpper" to 0.95,
        "ricIndividualCap" to 0.225,
        "ricAggregateThreshold" to 0.045,
        "ricAggregateCap" to 0.45,
        "ricComplianceTestIndividualCap" to 0.25,
        "ricComplianceTestAggregateThreshold" to 0.05,
        "ricComplianceTestAggregateCap" to 0.50,
        "ricComplianceTargetIndividualCap" to 0.245,
        "ricComplianceTargetAggregateThreshold" to 0.0475,
        "ricComplianceTargetAggregateCap" to 0.49,
        "ricMaximumAdjustmentRatio" to 10.0,
    )
    private val DECIMAL_PARAMETER_KEYS = CANONICAL_DECIMAL_VALUES.keys
}
