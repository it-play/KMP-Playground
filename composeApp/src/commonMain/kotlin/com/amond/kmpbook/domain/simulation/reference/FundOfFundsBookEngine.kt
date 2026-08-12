package com.amond.kmpbook.domain.simulation.reference

import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import com.amond.kmpbook.domain.model.fund.FundOfFundsCategory
import com.amond.kmpbook.domain.model.fund.FundOfFundsMethodologyProfile
import com.amond.kmpbook.domain.model.fund.FundOfFundsSelectionModel
import com.amond.kmpbook.domain.model.fund.FundOfFundsWeightingModel
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.reference.FundOfFundsActionKind
import com.amond.kmpbook.domain.model.reference.FundOfFundsBook
import com.amond.kmpbook.domain.model.reference.FundOfFundsBookAdvance
import com.amond.kmpbook.domain.model.reference.FundOfFundsPosition
import com.amond.kmpbook.domain.model.reference.FundOfFundsRebalanceRecord
import com.amond.kmpbook.domain.model.reference.FundOfFundsState
import com.amond.kmpbook.domain.simulation.market.MacroEnvironment
import com.amond.kmpbook.domain.simulation.price.DeterministicRandom
import com.amond.kmpbook.domain.time.DefaultMarketHolidays
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Executes PCEF/YYY/YMAX-like selection over shared, non-tradable underlying-fund candidates.
 *
 * Candidate economics are shared across methodologies. Product fees, fund creation/redemption and
 * listed-price microstructure remain product-layer concerns and are never applied here.
 */
class FundOfFundsBookEngine private constructor(
    private val seed: Long,
    private val repository: FundOfFundsUniverseRepository,
) {
    val universeFingerprint: String get() = repository.universeFingerprint

    fun hasCanonicalCandidate(
        universe: com.amond.kmpbook.domain.model.fund.FundOfFundsUniverse,
        position: FundOfFundsPosition,
    ): Boolean =
        repository.snapshotFor(position.candidateFundId, position.enteredOn.year)?.let { snapshot ->
            snapshot.universe == universe && snapshot.category == position.category
        } == true

    fun initialBook(
        profiles: Map<BenchmarkRef, FundOfFundsMethodologyProfile>,
        atDate: LocalDate,
        at: Instant,
    ): FundOfFundsBook {
        require(profiles.isNotEmpty())
        val states = profiles.toSortedMap().mapValuesTo(linkedMapOf()) { (ref, profile) ->
            val selection = select(profile, atDate.year, atDate, emptyMap(), at)
            FundOfFundsState(
                benchmarkRef = ref,
                universe = profile.universe,
                positions = selection.positions,
                revision = 0L,
                bootstrapDate = atDate,
                lastSelectionDate = null,
                nextSelectionDate = canonicalNextScheduledDate(
                    profile.selectionMonths,
                    after = atDate,
                ),
                lastReweightDate = null,
                nextReweightDate = canonicalNextScheduledDate(
                    profile.reweightMonths,
                    after = atDate,
                ),
                estimatedAnnualIncomeYield = canonicalEstimatedAnnualIncomeYield(selection.positions),
                eligibleCandidateCount = selection.eligibleCandidateCount,
                profileFingerprint = canonicalProfileFingerprint(ref, profile),
                universeFingerprint = repository.universeFingerprint,
                compositionHash = canonicalCompositionHash(selection.positions),
                asOf = at,
            )
        }
        return FundOfFundsBook(states)
    }

    @Suppress("LongParameterList")
    fun advanceHour(
        book: FundOfFundsBook,
        profiles: Map<BenchmarkRef, FundOfFundsMethodologyProfile>,
        componentGrossLogReturns: Map<BenchmarkRef, Double>,
        componentAnnualIncomeYields: Map<BenchmarkRef, Double>,
        macro: MacroEnvironment,
        referenceTradingDate: LocalDate,
        referenceTradingFraction: Double,
        reachesReferenceClose: Boolean,
        from: Instant,
        to: Instant,
    ): FundOfFundsBookAdvance {
        require(from == book.asOf)
        require(to > from && to - from <= MAX_ADVANCE_DURATION)
        require(referenceTradingFraction.isFinite() && referenceTradingFraction in 0.0..1.0)
        require(profiles.keys == book.states.keys)
        val requiredComponents = profiles.values.flatMapTo(linkedSetOf()) { profile ->
            profile.componentBenchmarkRefs
        }
        require(requiredComponents.all(componentGrossLogReturns::containsKey))
        require(requiredComponents.all(componentAnnualIncomeYields::containsKey))
        require(componentGrossLogReturns.values.all(Double::isFinite))
        require(componentAnnualIncomeYields.values.all { value -> value.isFinite() && value in 0.0..1.0 })

        val elapsedYearFraction = (to - from).inWholeNanoseconds.toDouble() / NANOSECONDS_PER_YEAR
        val nextStates = linkedMapOf<BenchmarkRef, FundOfFundsState>()
        val returns = linkedMapOf<BenchmarkRef, Double>()
        val incomeYields = linkedMapOf<BenchmarkRef, Double>()
        val records = mutableListOf<FundOfFundsRebalanceRecord>()
        book.states.toSortedMap().forEach { (ref, previous) ->
            val profile = profiles.getValue(ref)
            require(previous.profileFingerprint == canonicalProfileFingerprint(ref, profile))
            require(previous.universeFingerprint == repository.universeFingerprint)
            require(previous.positions.all { position ->
                hasCanonicalCandidate(previous.universe, position)
            })
            val drift = drift(
                previous = previous,
                profile = profile,
                componentGrossLogReturns = componentGrossLogReturns,
                componentAnnualIncomeYields = componentAnnualIncomeYields,
                macro = macro,
                elapsedYearFraction = elapsedYearFraction,
                referenceTradingFraction = referenceTradingFraction,
                from = from,
                to = to,
            )
            var next = drift.state
            var record: FundOfFundsRebalanceRecord? = null
            val selectionDue = reachesReferenceClose && referenceTradingDate >= next.nextSelectionDate
            val reweightDue = reachesReferenceClose && referenceTradingDate >= next.nextReweightDate
            if (selectionDue) {
                val effectiveDate = next.nextSelectionDate
                val selection = select(
                    profile = profile,
                    year = effectiveDate.year.coerceIn(FIRST_YEAR, LAST_YEAR),
                    effectiveDate = effectiveDate,
                    incumbents = next.positions.associateBy(FundOfFundsPosition::candidateFundId),
                    at = to,
                )
                val revised = applySelection(next, selection, profile, effectiveDate, to)
                record = recordFor(
                    previous = next,
                    next = revised,
                    kind = FundOfFundsActionKind.RECONSTITUTION,
                    effectiveDate = effectiveDate,
                    effectiveAt = to,
                )
                next = revised
                if (reweightDue && previous.nextReweightDate == effectiveDate) {
                    next = next.copy(
                        lastReweightDate = effectiveDate,
                        nextReweightDate = canonicalNextScheduledDate(
                            profile.reweightMonths,
                            effectiveDate,
                        ),
                    )
                }
            } else if (reweightDue) {
                val effectiveDate = next.nextReweightDate
                val selection = reweight(profile, next, effectiveDate.year.coerceIn(FIRST_YEAR, LAST_YEAR), to)
                val revised = applyReweight(next, selection, profile, effectiveDate, to)
                record = recordFor(
                    previous = next,
                    next = revised,
                    kind = FundOfFundsActionKind.REWEIGHT,
                    effectiveDate = effectiveDate,
                    effectiveAt = to,
                )
                next = revised
            }
            record?.let(records::add)
            nextStates[ref] = next
            returns[ref] = drift.grossLogReturn
            incomeYields[ref] = next.estimatedAnnualIncomeYield
        }
        return FundOfFundsBookAdvance(
            book = FundOfFundsBook(nextStates),
            grossReferenceLogReturns = returns,
            estimatedAnnualIncomeYields = incomeYields,
            rebalanceRecords = records.sortedWith(
                compareBy<FundOfFundsRebalanceRecord> { record -> record.effectiveAt }
                    .thenBy(FundOfFundsRebalanceRecord::benchmarkRef)
                    .thenBy(FundOfFundsRebalanceRecord::revision),
            ),
        )
    }

    private fun drift(
        previous: FundOfFundsState,
        profile: FundOfFundsMethodologyProfile,
        componentGrossLogReturns: Map<BenchmarkRef, Double>,
        componentAnnualIncomeYields: Map<BenchmarkRef, Double>,
        macro: MacroEnvironment,
        elapsedYearFraction: Double,
        referenceTradingFraction: Double,
        from: Instant,
        to: Instant,
    ): DriftResult {
        val logReturns = previous.positions.associate { position ->
            val underlyingReturn = componentGrossLogReturns.getValue(position.underlyingBenchmarkRef)
            val underlyingIncomeYield = componentAnnualIncomeYields.getValue(position.underlyingBenchmarkRef)
            val borrowingCost = position.leverageRatio *
                (macro.policyRate + BASE_LEVERAGE_SPREAD).coerceIn(0.0, 1.0) * elapsedYearFraction
            val expense = position.annualExpenseRate * elapsedYearFraction
            val residual = if (referenceTradingFraction == 0.0) 0.0 else {
                position.annualResidualVolatility *
                    sqrt(elapsedYearFraction * referenceTradingFraction * macro.volatilityRegime.coerceIn(.25, 4.0)) *
                    DeterministicRandom.keyed(
                        seed,
                        "fund-of-funds-return:${position.candidateFundId}:${from.epochSeconds}:${to.epochSeconds}",
                    ).nextGaussian()
            }
            val discountShock = if (referenceTradingFraction == 0.0) 0.0 else {
                DISCOUNT_SHOCK_ANNUAL_VOLATILITY *
                    sqrt(elapsedYearFraction * referenceTradingFraction) *
                    DeterministicRandom.keyed(
                        seed,
                        "fund-of-funds-discount:${position.candidateFundId}:${from.epochSeconds}:${to.epochSeconds}",
                    ).nextGaussian()
            }
            val targetDiscount = targetDiscount(position.category)
            val nextDiscount = (
                position.marketDiscountRate +
                    DISCOUNT_MEAN_REVERSION_RATE *
                    (targetDiscount - position.marketDiscountRate) * elapsedYearFraction + discountShock
                ).coerceIn(FundOfFundsPosition.MIN_DISCOUNT, FundOfFundsPosition.MAX_PREMIUM)
            val discountReturn = ln((1.0 + nextDiscount) / (1.0 + position.marketDiscountRate))
            val distributionTarget = (
                underlyingIncomeYield + categoryDistributionOverlay(position.category)
                ).coerceIn(0.0, FundOfFundsPosition.MAX_DISTRIBUTION_YIELD)
            val nextDistributionYield = (
                position.indicatedAnnualDistributionYield +
                    DISTRIBUTION_POLICY_ADJUSTMENT_RATE *
                    (distributionTarget - position.indicatedAnnualDistributionYield) *
                    elapsedYearFraction
                ).coerceIn(0.0, FundOfFundsPosition.MAX_DISTRIBUTION_YIELD)
            val logReturn = (
                underlyingReturn * (1.0 + position.leverageRatio) - borrowingCost - expense +
                    residual + discountReturn
                ).coerceIn(-MAX_INTERVAL_LOG_RETURN, MAX_INTERVAL_LOG_RETURN)
            position.candidateFundId to CandidateDrift(
                logReturn = logReturn,
                nextDiscount = nextDiscount,
                nextDistributionYield = nextDistributionYield,
            )
        }
        val grossFactor = previous.positions.sumOf { position ->
            position.currentWeight * exp(logReturns.getValue(position.candidateFundId).logReturn)
        }.coerceAtLeast(MIN_GROSS_FACTOR)
        val positions = previous.positions.map { position ->
            val candidate = logReturns.getValue(position.candidateFundId)
            position.copy(
                currentWeight = position.currentWeight * exp(candidate.logReturn) / grossFactor,
                marketDiscountRate = candidate.nextDiscount,
                indicatedAnnualDistributionYield = candidate.nextDistributionYield,
                asOf = to,
            )
        }.repairCurrentWeightRounding().sortedBy(FundOfFundsPosition::candidateFundId)
        return DriftResult(
            state = previous.copy(
                positions = positions,
                estimatedAnnualIncomeYield = canonicalEstimatedAnnualIncomeYield(positions),
                compositionHash = canonicalCompositionHash(positions),
                asOf = to,
            ),
            grossLogReturn = ln(grossFactor).coerceIn(-MAX_INTERVAL_LOG_RETURN, MAX_INTERVAL_LOG_RETURN),
        )
    }

    private fun select(
        profile: FundOfFundsMethodologyProfile,
        year: Int,
        effectiveDate: LocalDate,
        incumbents: Map<String, FundOfFundsPosition>,
        at: Instant,
    ): SelectionResult {
        val allSnapshots = repository.snapshots(profile, year)
        val eligible = allSnapshots.filter { snapshot ->
            snapshot.isEligible &&
                snapshot.indicatedAnnualDistributionYield >= profile.minimumDistributionYield &&
                abs(snapshot.marketDiscountRate) <= profile.maximumAbsoluteDiscount &&
                snapshot.liquidityScore >= profile.minimumLiquidityScore
        }
        require(eligible.size >= profile.targetFundCount) {
            "The fund-of-funds methodology cannot fill its target constituent count."
        }
        val ranked = eligible.map { snapshot ->
            snapshot to selectionScore(profile, snapshot, snapshot.candidateFundId in incumbents)
        }.sortedWith(
            compareByDescending<Pair<FundOfFundsCandidateSnapshot, Double>> { pair -> pair.second }
                .thenBy { pair -> pair.first.candidateFundId },
        )
        val selected = selectCapFeasibleCandidates(profile, ranked)
        val weights = targetWeights(profile, selected)
        val positions = selected.map { (snapshot, score) ->
            val prior = incumbents[snapshot.candidateFundId]
            FundOfFundsPosition(
                candidateFundId = snapshot.candidateFundId,
                category = snapshot.category,
                underlyingBenchmarkRef = profile.benchmarkRefFor(snapshot.category),
                currentWeight = weights.getValue(snapshot.candidateFundId),
                targetWeight = weights.getValue(snapshot.candidateFundId),
                marketDiscountRate = snapshot.marketDiscountRate,
                indicatedAnnualDistributionYield = snapshot.indicatedAnnualDistributionYield,
                leverageRatio = snapshot.leverageRatio,
                annualExpenseRate = snapshot.expenseRate,
                annualResidualVolatility = snapshot.annualResidualVolatility,
                liquidityScore = snapshot.liquidityScore,
                selectionScore = score,
                enteredOn = prior?.enteredOn ?: effectiveDate,
                asOf = at,
            )
        }.repairAllWeightRounding().sortedBy(FundOfFundsPosition::candidateFundId)
        return SelectionResult(positions, eligible.size)
    }

    private fun reweight(
        profile: FundOfFundsMethodologyProfile,
        state: FundOfFundsState,
        year: Int,
        at: Instant,
    ): SelectionResult {
        val scored = state.positions.map { position ->
            val snapshot = requireNotNull(repository.snapshotFor(position.candidateFundId, year))
            snapshot to selectionScore(profile, snapshot, incumbent = true)
        }.sortedWith(
            compareByDescending<Pair<FundOfFundsCandidateSnapshot, Double>> { pair -> pair.second }
                .thenBy { pair -> pair.first.candidateFundId },
        )
        val weights = targetWeights(profile, scored)
        val positions = state.positions.map { position ->
            val (snapshot, score) = scored.first { pair ->
                pair.first.candidateFundId == position.candidateFundId
            }
            position.copy(
                currentWeight = weights.getValue(position.candidateFundId),
                targetWeight = weights.getValue(position.candidateFundId),
                marketDiscountRate = snapshot.marketDiscountRate,
                indicatedAnnualDistributionYield = snapshot.indicatedAnnualDistributionYield,
                leverageRatio = snapshot.leverageRatio,
                annualExpenseRate = snapshot.expenseRate,
                annualResidualVolatility = snapshot.annualResidualVolatility,
                liquidityScore = snapshot.liquidityScore,
                selectionScore = score,
                asOf = at,
            )
        }.repairAllWeightRounding().sortedBy(FundOfFundsPosition::candidateFundId)
        return SelectionResult(positions, state.eligibleCandidateCount)
    }

    /**
     * Preserves global score order while repairing a top-N set whose category caps cannot fund 100%.
     * These private step records and helpers stay with the engine because they are not persisted or
     * shared domain types.
     */
    private fun selectCapFeasibleCandidates(
        profile: FundOfFundsMethodologyProfile,
        ranked: List<Pair<FundOfFundsCandidateSnapshot, Double>>,
    ): List<Pair<FundOfFundsCandidateSnapshot, Double>> {
        val selected = ranked.take(profile.targetFundCount).toMutableList()
        val omitted = ranked.drop(profile.targetFundCount).toMutableList()
        while (selectedCategoryCapacity(profile, selected) < 1.0 - ALLOCATION_EPSILON) {
            val currentCapacity = selectedCategoryCapacity(profile, selected)
            val bestSwap = omitted.flatMapIndexed { omittedIndex, incoming ->
                selected.mapIndexedNotNull { selectedIndex, outgoing ->
                    if (incoming.first.category == outgoing.first.category) return@mapIndexedNotNull null
                    val trial = selected.toMutableList().apply { this[selectedIndex] = incoming }
                    val capacityGain = selectedCategoryCapacity(profile, trial) - currentCapacity
                    if (capacityGain <= ALLOCATION_EPSILON) return@mapIndexedNotNull null
                    CandidateSwap(
                        selectedIndex = selectedIndex,
                        omittedIndex = omittedIndex,
                        capacityGain = capacityGain,
                        scoreDelta = incoming.second - outgoing.second,
                        incomingId = incoming.first.candidateFundId,
                        outgoingId = outgoing.first.candidateFundId,
                    )
                }
            }.maxWithOrNull(
                compareBy<CandidateSwap> { swap -> swap.scoreDelta }
                    .thenBy(CandidateSwap::capacityGain)
                    .thenByDescending(CandidateSwap::incomingId)
                    .thenByDescending(CandidateSwap::outgoingId),
            ) ?: error("Eligible candidates cannot satisfy the fund-of-funds category caps.")
            val outgoing = selected[bestSwap.selectedIndex]
            selected[bestSwap.selectedIndex] = omitted[bestSwap.omittedIndex]
            omitted[bestSwap.omittedIndex] = outgoing
        }
        return selected.sortedWith(
            compareByDescending<Pair<FundOfFundsCandidateSnapshot, Double>> { pair -> pair.second }
                .thenBy { pair -> pair.first.candidateFundId },
        )
    }

    private fun selectedCategoryCapacity(
        profile: FundOfFundsMethodologyProfile,
        selected: List<Pair<FundOfFundsCandidateSnapshot, Double>>,
    ): Double {
        val ranked = selected.sortedWith(
            compareByDescending<Pair<FundOfFundsCandidateSnapshot, Double>> { pair -> pair.second }
                .thenBy { pair -> pair.first.candidateFundId },
        )
        val capacityByCategory = mutableMapOf<FundOfFundsCategory, Double>()
        ranked.forEachIndexed { index, pair ->
            capacityByCategory.merge(
                pair.first.category,
                profile.weightCapAtRank(index + 1),
                Double::plus,
            )
        }
        return capacityByCategory.values.sumOf { capacity ->
            minOf(profile.categoryWeightCap, capacity)
        }
    }

    private fun selectionScore(
        profile: FundOfFundsMethodologyProfile,
        snapshot: FundOfFundsCandidateSnapshot,
        incumbent: Boolean,
    ): Double {
        val yieldScore = (snapshot.indicatedAnnualDistributionYield / .20).coerceIn(0.0, 2.0)
        val discountScore = (-snapshot.marketDiscountRate / .20).coerceIn(-2.0, 2.0)
        val score = when (profile.selectionModel) {
            FundOfFundsSelectionModel.DISTRIBUTION_DISCOUNT_LIQUIDITY ->
                .45 * yieldScore + .30 * discountScore + .20 * snapshot.liquidityScore +
                    .05 * snapshot.qualityScore
            FundOfFundsSelectionModel.ACTIVE_INCOME_ROTATION ->
                .40 * yieldScore + .30 * snapshot.trailingMomentumScore +
                    .15 * snapshot.qualityScore + .15 * snapshot.liquidityScore
        } + if (incumbent) INCUMBENT_SCORE_BONUS else 0.0
        return score.coerceIn(-100.0, 100.0)
    }

    private fun targetWeights(
        profile: FundOfFundsMethodologyProfile,
        selected: List<Pair<FundOfFundsCandidateSnapshot, Double>>,
    ): Map<String, Double> {
        val rawById = selected.associate { (snapshot, score) ->
            val raw = when (profile.weightingModel) {
                FundOfFundsWeightingModel.EQUAL_WEIGHT -> 1.0
                FundOfFundsWeightingModel.SCORE_WEIGHTED -> exp(score.coerceIn(-6.0, 6.0))
                FundOfFundsWeightingModel.DISTRIBUTION_WEIGHTED ->
                    snapshot.indicatedAnnualDistributionYield.coerceAtLeast(MIN_RAW_WEIGHT)
                FundOfFundsWeightingModel.MODIFIED_NET_ASSET_VALUE -> sqrt(snapshot.netAssetValue)
            }
            snapshot.candidateFundId to raw.coerceAtLeast(MIN_RAW_WEIGHT)
        }
        val individualCaps = selected.mapIndexed { index, (snapshot, _) ->
            snapshot.candidateFundId to profile.weightCapAtRank(index + 1)
        }.toMap()
        val byCategory = selected.groupBy { pair -> pair.first.category }
        val categoryRaw = byCategory.mapValues { (_, values) ->
            values.sumOf { pair -> rawById.getValue(pair.first.candidateFundId) }
        }
        val categoryCaps = byCategory.mapValues { (_, values) ->
            minOf(
                profile.categoryWeightCap,
                values.sumOf { pair -> individualCaps.getValue(pair.first.candidateFundId) },
            )
        }
        val categoryWeights = allocateWithCaps(
            keys = categoryRaw.keys.sortedBy(FundOfFundsCategory::ordinal),
            raw = categoryRaw,
            caps = categoryCaps,
            total = 1.0,
        )
        val result = linkedMapOf<String, Double>()
        byCategory.toSortedMap(compareBy(FundOfFundsCategory::ordinal)).forEach { (category, values) ->
            val ids = values.map { pair -> pair.first.candidateFundId }.sorted()
            result += allocateWithCaps(
                keys = ids,
                raw = ids.associateWith(rawById::getValue),
                caps = ids.associateWith(individualCaps::getValue),
                total = categoryWeights.getValue(category),
            )
        }
        return repairWeightMap(result)
    }

    private fun applySelection(
        state: FundOfFundsState,
        selection: SelectionResult,
        profile: FundOfFundsMethodologyProfile,
        effectiveDate: LocalDate,
        at: Instant,
    ): FundOfFundsState = state.copy(
        positions = selection.positions,
        revision = state.revision + 1L,
        lastSelectionDate = effectiveDate,
        nextSelectionDate = canonicalNextScheduledDate(profile.selectionMonths, effectiveDate),
        estimatedAnnualIncomeYield = canonicalEstimatedAnnualIncomeYield(selection.positions),
        eligibleCandidateCount = selection.eligibleCandidateCount,
        compositionHash = canonicalCompositionHash(selection.positions),
        asOf = at,
    )

    private fun applyReweight(
        state: FundOfFundsState,
        selection: SelectionResult,
        profile: FundOfFundsMethodologyProfile,
        effectiveDate: LocalDate,
        at: Instant,
    ): FundOfFundsState = state.copy(
        positions = selection.positions,
        revision = state.revision + 1L,
        lastReweightDate = effectiveDate,
        nextReweightDate = canonicalNextScheduledDate(profile.reweightMonths, effectiveDate),
        estimatedAnnualIncomeYield = canonicalEstimatedAnnualIncomeYield(selection.positions),
        compositionHash = canonicalCompositionHash(selection.positions),
        asOf = at,
    )

    private fun recordFor(
        previous: FundOfFundsState,
        next: FundOfFundsState,
        kind: FundOfFundsActionKind,
        effectiveDate: LocalDate,
        effectiveAt: Instant,
    ): FundOfFundsRebalanceRecord {
        val before = previous.positions.associate { position ->
            position.candidateFundId to position.currentWeight
        }
        val after = next.positions.associate { position ->
            position.candidateFundId to position.currentWeight
        }
        val allIds = before.keys + after.keys
        val turnover = .5 * allIds.sumOf { id -> abs((before[id] ?: 0.0) - (after[id] ?: 0.0)) }
        return FundOfFundsRebalanceRecord(
            id = "fund-of-funds-${kind.name.lowercase()}:${next.benchmarkRef.benchmarkId}:" +
                "v${next.benchmarkRef.version}:$effectiveDate:r${next.revision}",
            benchmarkRef = next.benchmarkRef,
            kind = kind,
            effectiveDate = effectiveDate,
            effectiveAt = effectiveAt,
            addedCandidateFundIds = (after.keys - before.keys).sorted(),
            removedCandidateFundIds = (before.keys - after.keys).sorted(),
            compositionHashBefore = previous.compositionHash,
            compositionHashAfter = next.compositionHash,
            oneWayTurnoverRate = turnover.coerceIn(0.0, 1.0),
            resultingFundCount = next.positions.size,
            revision = next.revision,
        )
    }

    fun canonicalEstimatedAnnualIncomeYield(positions: List<FundOfFundsPosition>): Double =
        positions.sumOf { position ->
            position.currentWeight * position.indicatedAnnualDistributionYield
        }.coerceIn(0.0, 1.0)

    fun canonicalCompositionHash(positions: List<FundOfFundsPosition>): String = stableHex(
        positions.sortedBy(FundOfFundsPosition::candidateFundId).joinToString("|") { position ->
            "${position.candidateFundId}:${position.category}:${position.underlyingBenchmarkRef}:" +
                "${position.targetWeight.toBits()}:${position.enteredOn}"
        },
    )

    fun canonicalProfileFingerprint(
        ref: BenchmarkRef,
        profile: FundOfFundsMethodologyProfile,
    ): String = stableHex(
        listOf(
            ref,
            profile.universe,
            profile.selectionModel,
            profile.weightingModel,
            profile.targetFundCount,
            profile.candidateUniverseSize,
            profile.eligibleCategories.joinToString(),
            profile.categoryReferences.joinToString { reference ->
                "${reference.category}:${reference.benchmarkRef}"
            },
            profile.minimumDistributionYield.toBits(),
            profile.maximumAbsoluteDiscount.toBits(),
            profile.minimumLiquidityScore.toBits(),
            profile.individualWeightCap.toBits(),
            profile.categoryWeightCap.toBits(),
            profile.rankedWeightCapTiers.joinToString { tier ->
                "${tier.lastRankInclusive}:${tier.maximumWeight.toBits()}"
            },
            profile.selectionCalendar,
            profile.selectionMonths.joinToString(),
            profile.reweightCalendar,
            profile.reweightMonths.joinToString(),
            profile.supportLevel,
            profile.provenance,
            profile.confidence,
            profile.officialSourceUrls.joinToString(),
            profile.assumptionId,
        ).joinToString("|"),
    )

    fun canonicalNextScheduledDate(months: Set<Int>, after: LocalDate): LocalDate {
        for (year in after.year..(LAST_YEAR + 1)) {
            for (month in months.sorted()) {
                val candidate = lastUsTradingDateOfMonth(year, month)
                if (candidate > after) return candidate
            }
        }
        error("No future fund-of-funds schedule date after $after.")
    }

    private fun lastUsTradingDateOfMonth(year: Int, month: Int): LocalDate {
        var date = if (month == 12) LocalDate(year + 1, 1, 1) else LocalDate(year, month + 1, 1)
        date = date.minus(1, DateTimeUnit.DAY)
        while (!isUsTradingDate(date)) date = date.minus(1, DateTimeUnit.DAY)
        return date
    }

    private fun isUsTradingDate(date: LocalDate): Boolean =
        date.dayOfWeek !in WEEKEND &&
            (date.year !in FIRST_YEAR..LAST_YEAR ||
                date !in DefaultMarketHolidays.closedDates(Market.NYSE, date.year))

    private fun targetDiscount(category: FundOfFundsCategory): Double = when (category) {
        FundOfFundsCategory.TAXABLE_INVESTMENT_GRADE -> -.055
        FundOfFundsCategory.MUNICIPAL_FIXED_INCOME -> -.060
        FundOfFundsCategory.HIGH_YIELD_CREDIT -> -.070
        FundOfFundsCategory.EQUITY_OPTION_INCOME -> -.045
        FundOfFundsCategory.MULTI_ASSET_INCOME -> -.060
        FundOfFundsCategory.SINGLE_SECURITY_OPTION_INCOME -> 0.0
    }

    private fun categoryDistributionOverlay(category: FundOfFundsCategory): Double = when (category) {
        FundOfFundsCategory.TAXABLE_INVESTMENT_GRADE -> .015
        FundOfFundsCategory.MUNICIPAL_FIXED_INCOME -> .012
        FundOfFundsCategory.HIGH_YIELD_CREDIT -> .020
        FundOfFundsCategory.EQUITY_OPTION_INCOME -> .060
        FundOfFundsCategory.MULTI_ASSET_INCOME -> .030
        FundOfFundsCategory.SINGLE_SECURITY_OPTION_INCOME -> .180
    }

    private fun <K : Comparable<K>> allocateWithCaps(
        keys: List<K>,
        raw: Map<K, Double>,
        caps: Map<K, Double>,
        total: Double,
    ): Map<K, Double> {
        require(keys.isNotEmpty())
        require(keys.distinct().size == keys.size)
        require(total.isFinite() && total > 0.0)
        require(keys.sumOf { key -> caps.getValue(key) } >= total - ALLOCATION_EPSILON) {
            "Fund-of-funds weight caps cannot allocate the requested total."
        }
        val result = keys.associateWith { 0.0 }.toMutableMap()
        val active = keys.toMutableSet()
        var remaining = total
        while (active.isNotEmpty() && remaining > ALLOCATION_EPSILON) {
            val rawTotal = active.sumOf { key -> raw.getValue(key).coerceAtLeast(MIN_RAW_WEIGHT) }
            var cappedAny = false
            for (key in active.sorted()) {
                val proposed = remaining * raw.getValue(key).coerceAtLeast(MIN_RAW_WEIGHT) / rawTotal
                val available = caps.getValue(key) - result.getValue(key)
                if (proposed >= available - ALLOCATION_EPSILON) {
                    result[key] = result.getValue(key) + available.coerceAtLeast(0.0)
                    remaining -= available.coerceAtLeast(0.0)
                    active.remove(key)
                    cappedAny = true
                    break
                }
            }
            if (!cappedAny) {
                for (key in active.sorted()) {
                    result[key] = result.getValue(key) +
                        remaining * raw.getValue(key).coerceAtLeast(MIN_RAW_WEIGHT) / rawTotal
                }
                remaining = 0.0
            }
        }
        require(remaining <= ALLOCATION_EPSILON)
        return repairWeightMap(result, total)
    }

    private fun <K : Comparable<K>> repairWeightMap(
        values: Map<K, Double>,
        target: Double = 1.0,
    ): Map<K, Double> {
        val result = values.toSortedMap().toMutableMap()
        val difference = target - result.values.sum()
        val key = result.entries.maxWithOrNull(
            compareBy<Map.Entry<K, Double>> { entry -> entry.value }.thenBy { entry -> entry.key },
        )?.key ?: error("Cannot repair an empty weight map.")
        result[key] = result.getValue(key) + difference
        require(result.values.all { value -> value > 0.0 && value.isFinite() })
        return result
    }

    private fun List<FundOfFundsPosition>.repairCurrentWeightRounding(): List<FundOfFundsPosition> {
        val difference = 1.0 - sumOf(FundOfFundsPosition::currentWeight)
        val candidateId = maxWithOrNull(
            compareBy<FundOfFundsPosition> { position -> position.currentWeight }
                .thenBy(FundOfFundsPosition::candidateFundId),
        )?.candidateFundId ?: error("Cannot repair an empty fund-of-funds basket.")
        return map { position ->
            if (position.candidateFundId == candidateId) {
                position.copy(currentWeight = position.currentWeight + difference)
            } else {
                position
            }
        }
    }

    private fun List<FundOfFundsPosition>.repairAllWeightRounding(): List<FundOfFundsPosition> {
        val currentDifference = 1.0 - sumOf(FundOfFundsPosition::currentWeight)
        val targetDifference = 1.0 - sumOf(FundOfFundsPosition::targetWeight)
        val candidateId = maxWithOrNull(
            compareBy<FundOfFundsPosition> { position -> position.currentWeight }
                .thenBy(FundOfFundsPosition::candidateFundId),
        )?.candidateFundId ?: error("Cannot repair an empty fund-of-funds basket.")
        return map { position ->
            if (position.candidateFundId == candidateId) {
                position.copy(
                    currentWeight = position.currentWeight + currentDifference,
                    targetWeight = position.targetWeight + targetDifference,
                )
            } else {
                position
            }
        }
    }

    private fun stableHex(value: String): String =
        DeterministicRandom.stableHash64(value).toULong().toString(16).padStart(16, '0')

    // These transient step results are private to this engine and deliberately do not enlarge the
    // persisted/public reference model with implementation-only types.
    private data class SelectionResult(
        val positions: List<FundOfFundsPosition>,
        val eligibleCandidateCount: Int,
    )

    private data class CandidateDrift(
        val logReturn: Double,
        val nextDiscount: Double,
        val nextDistributionYield: Double,
    )

    private data class DriftResult(
        val state: FundOfFundsState,
        val grossLogReturn: Double,
    )

    private data class CandidateSwap(
        val selectedIndex: Int,
        val omittedIndex: Int,
        val capacityGain: Double,
        val scoreDelta: Double,
        val incomingId: String,
        val outgoingId: String,
    )

    companion object {
        fun forCampaignSeed(campaignSeed: Long): FundOfFundsBookEngine = FundOfFundsBookEngine(
            seed = DeterministicRandom.mixSeed(campaignSeed, ENGINE_STREAM_ID),
            repository = FundOfFundsUniverseRepository.forCampaignSeed(campaignSeed),
        )

        private const val ENGINE_STREAM_ID: Long = 0x464f46454e47494eL
        private const val FIRST_YEAR: Int = 2026
        private const val LAST_YEAR: Int = 2040
        private const val NANOSECONDS_PER_YEAR: Double = 365.2425 * 24.0 * 60.0 * 60.0 * 1e9
        private const val BASE_LEVERAGE_SPREAD: Double = .0125
        private const val DISCOUNT_MEAN_REVERSION_RATE: Double = .75
        private const val DISCOUNT_SHOCK_ANNUAL_VOLATILITY: Double = .12
        private const val DISTRIBUTION_POLICY_ADJUSTMENT_RATE: Double = .50
        private const val INCUMBENT_SCORE_BONUS: Double = .08
        private const val MIN_GROSS_FACTOR: Double = 1e-12
        private const val MIN_RAW_WEIGHT: Double = 1e-12
        private const val ALLOCATION_EPSILON: Double = 1e-12
        private const val MAX_INTERVAL_LOG_RETURN: Double = 2.5
        private val MAX_ADVANCE_DURATION = 24.hours
        private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    }
}
