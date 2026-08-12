package com.amond.kmpbook.domain.simulation.reference

import com.amond.kmpbook.domain.model.fund.BenchmarkDefinition
import com.amond.kmpbook.domain.model.fund.BenchmarkEngineKind
import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import com.amond.kmpbook.domain.model.fund.CompositeAllocationModel
import com.amond.kmpbook.domain.model.fund.CompositeReferenceProfile
import com.amond.kmpbook.domain.model.fund.CompositeReferenceSleeve
import com.amond.kmpbook.domain.model.fund.CompositeReferenceSource
import com.amond.kmpbook.domain.model.fund.CompositeReferenceSourceKind
import com.amond.kmpbook.domain.model.fund.CompositeSleeveDirection
import com.amond.kmpbook.domain.model.market.ReferenceCurrency
import com.amond.kmpbook.domain.model.reference.CompositeReferenceActionKind
import com.amond.kmpbook.domain.model.reference.CompositeReferenceAdvanceInput
import com.amond.kmpbook.domain.model.reference.CompositeReferenceBook
import com.amond.kmpbook.domain.model.reference.CompositeReferenceBookAdvance
import com.amond.kmpbook.domain.model.reference.CompositeReferenceRebalanceRecord
import com.amond.kmpbook.domain.model.reference.CompositeReferenceSleevePosition
import com.amond.kmpbook.domain.model.reference.CompositeReferenceState
import com.amond.kmpbook.domain.model.reference.ReferenceCurrencyPair
import com.amond.kmpbook.domain.model.reference.ReferenceSourceCatalog
import com.amond.kmpbook.domain.model.reference.ReferenceSourceReturnFrame
import com.amond.kmpbook.domain.model.reference.ReferenceSourceSnapshot
import com.amond.kmpbook.domain.simulation.price.DeterministicRandom
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.expm1
import kotlin.math.ln
import kotlin.math.ln1p
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** Shared, product-independent engine for typed static, tactical, signed and MBS-IO composites. */
class CompositeReferenceBookEngine private constructor(private val seed: Long) {
    /**
     * Rebuilds the campaign-start composition anchor. Composite callers must provide the snapshot
     * produced after canonical ALT component bootstraps so the DAG is evaluated component-first.
     */
    fun canonicalBootstrapCompositionHash(
        definition: BenchmarkDefinition,
        sourceCatalog: ReferenceSourceCatalog,
        sourceSnapshotAtCampaignStart: ReferenceSourceSnapshot,
        atDate: LocalDate,
        at: Instant,
    ): String = initialBook(
        definitions = listOf(definition),
        sourceCatalog = sourceCatalog,
        sourceSnapshot = sourceSnapshotAtCampaignStart,
        atDate = atDate,
        at = at,
    ).states.getValue(definition.ref).bootstrapCompositionHash

    fun initialBook(
        definitions: Collection<BenchmarkDefinition>,
        sourceCatalog: ReferenceSourceCatalog,
        sourceSnapshot: ReferenceSourceSnapshot,
        atDate: LocalDate,
        at: Instant,
    ): CompositeReferenceBook {
        val byRef = validatedDefinitions(definitions, sourceCatalog)
        val states = linkedMapOf<BenchmarkRef, CompositeReferenceState>()
        byRef.toSortedMap().forEach { (ref, definition) ->
            val profile = requireNotNull(definition.compositeReferenceProfile)
            val positions = profile.sleeves.map { sleeve ->
                val sourceKey = sourceKey(sleeve.source)
                val available = sourceAvailable(sleeve.source, sourceSnapshot)
                val volatility = DeterministicRandom.keyed(
                    seed,
                    "composite-initial-vol:$ref:$sourceKey",
                ).nextDouble(.08, .24)
                val income = if (available) sourceIncomeYield(sleeve.source, sourceSnapshot) else 0.0
                val duration = if (available) {
                    sleeve.mbsInterestOnlyTerms?.modelParameters?.effectiveDurationYears
                        ?: sourceDuration(sleeve.source, sourceSnapshot)
                } else {
                    0.0
                }
                CompositeReferenceSleevePosition(
                    sleeveId = sleeve.sleeveId,
                    direction = sleeve.direction,
                    currentWeightMagnitude = 0.0,
                    targetWeightMagnitude = 0.0,
                    annualizedVariance = volatility * volatility,
                    trendSignal = DeterministicRandom.keyed(
                        seed,
                        "composite-initial-signal:$ref:$sourceKey:$atDate",
                    ).nextDouble(-.25, .25),
                    lastSourceLogReturn = 0.0,
                    sourceAvailable = available,
                    sourceAnnualIncomeYield = if (available) {
                        sleeve.mbsInterestOnlyTerms?.modelParameters?.couponStripYieldAnnual ?: income
                    } else {
                        0.0
                    },
                    sourceDurationYears = duration,
                    conditionalPrepaymentRateAnnual = if (available) {
                        sleeve.mbsInterestOnlyTerms
                            ?.modelParameters?.baseConditionalPrepaymentRateAnnual
                    } else {
                        null
                    },
                )
            }.sortedBy(CompositeReferenceSleevePosition::sleeveId)
            val targets = allocateTargets(profile, positions, preserveMembership = false)
            val allocated = positions.map { position ->
                val target = targets.magnitudes.getValue(position.sleeveId)
                position.copy(currentWeightMagnitude = target, targetWeightMagnitude = target)
            }
            states[ref] = buildState(
                ref = ref,
                profile = profile,
                positions = allocated,
                revision = 0L,
                lastSelectionDate = null,
                nextSelectionDate = CompositeScheduleResolver.nextDateAfterInstant(
                    profile.selectionSchedule,
                    definition.baseCurrency,
                    at,
                ),
                lastReweightDate = null,
                nextReweightDate = CompositeScheduleResolver.nextDateAfterInstant(
                    profile.reweightSchedule,
                    definition.baseCurrency,
                    at,
                ),
                lastMortgageRateAnnual = sourceSnapshot.mortgageRateAnnual,
                bootstrapCompositionHash = compositionHash(allocated),
                asOf = at,
            )
        }
        return CompositeReferenceBook(states)
    }

    fun advanceHour(
        book: CompositeReferenceBook,
        definitions: Collection<BenchmarkDefinition>,
        sourceCatalog: ReferenceSourceCatalog,
        input: CompositeReferenceAdvanceInput,
        from: Instant,
        to: Instant,
    ): CompositeReferenceBookAdvance {
        require(book.asOf == from)
        val elapsed = to - from
        require(elapsed.isPositive() && elapsed <= MAX_ADVANCE_DURATION)
        val yearFraction = elapsed.inWholeMilliseconds.toDouble() / MILLISECONDS_PER_YEAR
        val byRef = validatedDefinitions(definitions, sourceCatalog)
        require(byRef.keys == book.states.keys)
        val nextStates = linkedMapOf<BenchmarkRef, CompositeReferenceState>()
        val returns = linkedMapOf<BenchmarkRef, Double>()
        val incomeYields = linkedMapOf<BenchmarkRef, Double>()
        val durations = linkedMapOf<BenchmarkRef, Double>()
        val records = mutableListOf<CompositeReferenceRebalanceRecord>()
        book.states.toSortedMap().forEach { (ref, previous) ->
            val definition = byRef.getValue(ref)
            val profile = requireNotNull(definition.compositeReferenceProfile)
            require(previous.profileFingerprint == profileFingerprint(ref, profile))
            val sleeveById = profile.sleeves.associateBy(CompositeReferenceSleeve::sleeveId)
            val cashSubstitutedSleeveIds = previous.positions.filter { position ->
                position.sourceAvailable && !sourceAvailable(
                    sleeveById.getValue(position.sleeveId).source,
                    input.sourceFrame,
                )
            }.map(CompositeReferenceSleevePosition::sleeveId).sorted()
            val sourceReturns = linkedMapOf<String, Double>()
            val updatedMetrics = previous.positions.map { position ->
                val sleeve = sleeveById.getValue(position.sleeveId)
                val observation = sourceObservation(
                    ref = ref,
                    compositeCurrency = definition.baseCurrency,
                    sleeve = sleeve,
                    position = position,
                    sourceCatalog = sourceCatalog,
                    frame = input.sourceFrame,
                    previousMortgageRate = previous.lastMortgageRateAnnual,
                    mortgageRate = input.mortgageRateAnnual,
                    annualRiskFreeRate = input.annualRiskFreeRate,
                    yearFraction = yearFraction,
                    from = from,
                    to = to,
                )
                sourceReturns[position.sleeveId] = observation.logReturn
                val lookback = profile.riskLookbackTradingDays ?: DEFAULT_LOOKBACK_TRADING_DAYS
                val decay = exp(-TRADING_DAYS_PER_YEAR * yearFraction / lookback)
                val annualizedSquaredReturn = observation.logReturn * observation.logReturn /
                    yearFraction.coerceAtLeast(MIN_YEAR_FRACTION)
                position.copy(
                    annualizedVariance = (
                        decay * position.annualizedVariance +
                            (1.0 - decay) * annualizedSquaredReturn.coerceAtMost(
                                CompositeReferenceSleevePosition.MAX_VARIANCE,
                            )
                        ).coerceIn(
                        CompositeReferenceSleevePosition.MIN_VARIANCE,
                        CompositeReferenceSleevePosition.MAX_VARIANCE,
                    ),
                    trendSignal = (
                        decay * position.trendSignal +
                            (1.0 - decay) * observation.logReturn /
                            sqrt(yearFraction.coerceAtLeast(MIN_YEAR_FRACTION))
                        ).coerceIn(-100.0, 100.0),
                    lastSourceLogReturn = observation.logReturn,
                    sourceAvailable = observation.sourceAvailable,
                    sourceAnnualIncomeYield = observation.incomeYield,
                    sourceDurationYears = observation.durationYears,
                    conditionalPrepaymentRateAnnual = observation.conditionalPrepaymentRateAnnual,
                )
            }
            val simplePnl = previous.positions.sumOf { position ->
                position.signedCurrentWeight * expm1(sourceReturns.getValue(position.sleeveId))
            }
            val financingCost = (previous.grossExposure - 1.0).coerceAtLeast(0.0) *
                (input.annualRiskFreeRate + (profile.annualFinancingSpread ?: 0.0)) * yearFraction
            val borrowCost = profile.sleeves.sumOf { sleeve ->
                if (sleeve.direction == CompositeSleeveDirection.SHORT) {
                    val position = updatedMetrics.first { it.sleeveId == sleeve.sleeveId }
                    if (position.sourceAvailable) {
                        position.currentWeightMagnitude * requireNotNull(sleeve.annualBorrowSpread) *
                            yearFraction
                    } else {
                        0.0
                    }
                } else {
                    0.0
                }
            }
            val shortDividendExpense = updatedMetrics.sumOf { position ->
                if (position.direction == CompositeSleeveDirection.SHORT) {
                    position.currentWeightMagnitude * position.sourceAnnualIncomeYield * yearFraction
                } else {
                    0.0
                }
            }
            val portfolioLogReturn = ln(
                (1.0 + simplePnl - financingCost - borrowCost - shortDividendExpense)
                    .coerceAtLeast(MIN_NAV_FACTOR),
            ).coerceIn(-MAX_INTERVAL_LOG_MOVE, MAX_INTERVAL_LOG_MOVE)
            val navFactor = exp(portfolioLogReturn)
            var drifted = updatedMetrics.map { position ->
                position.copy(
                    currentWeightMagnitude = (
                        position.currentWeightMagnitude *
                            exp(sourceReturns.getValue(position.sleeveId)) / navFactor
                        ).coerceIn(0.0, MAX_POSITION_WEIGHT),
                )
            }
            val driftedGross = drifted.sumOf(
                CompositeReferenceSleevePosition::currentWeightMagnitude,
            )
            if (driftedGross > MAX_STATE_GROSS_EXPOSURE) {
                val scale = MAX_STATE_GROSS_EXPOSURE / driftedGross
                drifted = drifted.map { position ->
                    position.copy(currentWeightMagnitude = position.currentWeightMagnitude * scale)
                }
            }
            val selectionDue = CompositeScheduleResolver.crossesClose(
                definition.baseCurrency,
                previous.nextSelectionDate,
                from,
                to,
            )
            val reweightDue = CompositeScheduleResolver.crossesClose(
                definition.baseCurrency,
                previous.nextReweightDate,
                from,
                to,
            )
            var revision = previous.revision
            var lastSelectionDate = previous.lastSelectionDate
            var nextSelectionDate = previous.nextSelectionDate
            var lastReweightDate = previous.lastReweightDate
            var nextReweightDate = previous.nextReweightDate
            if (cashSubstitutedSleeveIds.isNotEmpty()) {
                require(!selectionDue && !reweightDue) {
                    "Source-to-cash transition must be observed after any coincident scheduled close."
                }
                revision += 1L
                val measures = portfolioMeasures(drifted)
                records += CompositeReferenceRebalanceRecord(
                    id = "composite-extraordinary-source-to-cash:${ref.benchmarkId}:v${ref.version}:" +
                        "${from.epochSeconds}:r$revision",
                    benchmarkRef = ref,
                    kind = CompositeReferenceActionKind.EXTRAORDINARY_SOURCE_TO_CASH,
                    effectiveDate = CompositeScheduleResolver.localDateAt(definition.baseCurrency, from),
                    effectiveAt = from,
                    addedSleeveIds = emptyList(),
                    removedSleeveIds = emptyList(),
                    cashSubstitutedSleeveIds = cashSubstitutedSleeveIds,
                    compositionHashBefore = previous.compositionHash,
                    compositionHashAfter = compositionHash(drifted),
                    turnoverRate = 0.0,
                    resultingGrossExposure = measures.gross,
                    resultingNetExposure = measures.net,
                    resultingDurationYears = measures.duration,
                    revision = revision,
                )
            } else if (selectionDue || reweightDue) {
                val kind = if (selectionDue) {
                    CompositeReferenceActionKind.SELECTION
                } else {
                    CompositeReferenceActionKind.REWEIGHT
                }
                val due = if (selectionDue) {
                    requireNotNull(previous.nextSelectionDate)
                } else {
                    requireNotNull(previous.nextReweightDate)
                }
                val before = drifted
                val targets = allocateTargets(
                    profile,
                    before,
                    preserveMembership = kind == CompositeReferenceActionKind.REWEIGHT,
                )
                val proposed = before.map { position ->
                    val target = targets.magnitudes.getValue(position.sleeveId)
                    position.copy(currentWeightMagnitude = target, targetWeightMagnitude = target)
                }
                val maximumDeviation = proposed.maxOf { proposedPosition ->
                    abs(
                        proposedPosition.signedTargetWeight -
                            before.first { it.sleeveId == proposedPosition.sleeveId }
                                .signedCurrentWeight,
                    )
                }
                val shouldApply = kind == CompositeReferenceActionKind.SELECTION ||
                    profile.driftThreshold == null ||
                    maximumDeviation >= profile.driftThreshold
                if (shouldApply) {
                    drifted = proposed
                    revision += 1L
                    if (selectionDue) {
                        lastSelectionDate = due
                        nextSelectionDate = CompositeScheduleResolver.nextDate(
                            profile.selectionSchedule,
                            definition.baseCurrency,
                            due,
                        )
                        if (reweightDue && previous.nextReweightDate == due) {
                            lastReweightDate = due
                            nextReweightDate = CompositeScheduleResolver.nextDate(
                                profile.reweightSchedule,
                                definition.baseCurrency,
                                due,
                            )
                        }
                    } else {
                        lastReweightDate = due
                        nextReweightDate = CompositeScheduleResolver.nextDate(
                            profile.reweightSchedule,
                            definition.baseCurrency,
                            due,
                        )
                    }
                    val beforeHash = compositionHash(previous.positions)
                    val afterHash = compositionHash(drifted)
                    val added = drifted.filter { it.targetWeightMagnitude > ACTIVE_EPSILON }
                        .map(CompositeReferenceSleevePosition::sleeveId)
                        .filter { id ->
                            before.first { it.sleeveId == id }.targetWeightMagnitude <= ACTIVE_EPSILON
                        }
                        .sorted()
                    val removed = before.filter { it.targetWeightMagnitude > ACTIVE_EPSILON }
                        .map(CompositeReferenceSleevePosition::sleeveId)
                        .filter { id ->
                            drifted.first { it.sleeveId == id }.targetWeightMagnitude <= ACTIVE_EPSILON
                        }
                        .sorted()
                    val turnover = .5 * drifted.sumOf { after ->
                        abs(
                            after.signedTargetWeight -
                                before.first { it.sleeveId == after.sleeveId }.signedCurrentWeight,
                        )
                    }
                    val measures = portfolioMeasures(drifted)
                    records += CompositeReferenceRebalanceRecord(
                        id = "composite-${kind.name.lowercase()}:${ref.benchmarkId}:v${ref.version}:" +
                            "$due:r$revision",
                        benchmarkRef = ref,
                        kind = kind,
                        effectiveDate = due,
                        effectiveAt = CompositeScheduleResolver.closeAt(definition.baseCurrency, due),
                        addedSleeveIds = if (kind == CompositeReferenceActionKind.SELECTION) {
                            added
                        } else {
                            emptyList()
                        },
                        removedSleeveIds = if (kind == CompositeReferenceActionKind.SELECTION) {
                            removed
                        } else {
                            emptyList()
                        },
                        cashSubstitutedSleeveIds = emptyList(),
                        compositionHashBefore = beforeHash,
                        compositionHashAfter = afterHash,
                        turnoverRate = turnover,
                        resultingGrossExposure = measures.gross,
                        resultingNetExposure = measures.net,
                        resultingDurationYears = measures.duration,
                        revision = revision,
                    )
                } else {
                    nextReweightDate = CompositeScheduleResolver.nextDate(
                        profile.reweightSchedule,
                        definition.baseCurrency,
                        due,
                    )
                }
            }
            val next = buildState(
                ref = ref,
                profile = profile,
                positions = drifted,
                revision = revision,
                lastSelectionDate = lastSelectionDate,
                nextSelectionDate = nextSelectionDate,
                lastReweightDate = lastReweightDate,
                nextReweightDate = nextReweightDate,
                lastMortgageRateAnnual = input.mortgageRateAnnual,
                bootstrapCompositionHash = previous.bootstrapCompositionHash,
                asOf = to,
            )
            nextStates[ref] = next
            returns[ref] = portfolioLogReturn
            incomeYields[ref] = next.estimatedAnnualIncomeYield
            durations[ref] = next.effectiveDurationYears
        }
        return CompositeReferenceBookAdvance(
            book = CompositeReferenceBook(nextStates),
            referenceLogReturns = returns,
            estimatedAnnualIncomeYields = incomeYields,
            effectiveDurationsYears = durations,
            rebalanceRecords = records.sortedWith(
                compareBy<CompositeReferenceRebalanceRecord> { it.benchmarkRef }
                    .thenBy(CompositeReferenceRebalanceRecord::revision),
            ),
        )
    }

    /**
     * Applies direct-instrument listing availability learned after interval pricing at zero P&L.
     * Current and target exposure stay unchanged but permanently represent base-currency cash.
     */
    fun reconcileAvailability(
        book: CompositeReferenceBook,
        definitions: Collection<BenchmarkDefinition>,
        sourceSnapshot: ReferenceSourceSnapshot,
        at: Instant,
    ): CompositeReferenceBookAdvance {
        require(book.asOf == at)
        val byRef = definitions.associateBy(BenchmarkDefinition::ref)
        require(byRef.size == definitions.size && byRef.keys == book.states.keys)
        val nextStates = linkedMapOf<BenchmarkRef, CompositeReferenceState>()
        val records = mutableListOf<CompositeReferenceRebalanceRecord>()
        book.states.toSortedMap().forEach { (ref, previous) ->
            val definition = byRef.getValue(ref)
            require(definition.engineKind == BenchmarkEngineKind.COMPOSITE_REFERENCE)
            val profile = requireNotNull(definition.compositeReferenceProfile)
            require(previous.profileFingerprint == profileFingerprint(ref, profile))
            val sleeveById = profile.sleeves.associateBy(CompositeReferenceSleeve::sleeveId)
            val transitionedIds = mutableListOf<String>()
            val positions = previous.positions.map { position ->
                val sleeve = sleeveById.getValue(position.sleeveId)
                val remainsAvailable = position.sourceAvailable &&
                    sourceAvailable(sleeve.source, sourceSnapshot)
                if (!remainsAvailable) {
                    if (position.sourceAvailable) transitionedIds += position.sleeveId
                    position.copy(
                        lastSourceLogReturn = if (position.sourceAvailable) {
                            0.0
                        } else {
                            position.lastSourceLogReturn
                        },
                        sourceAvailable = false,
                        sourceAnnualIncomeYield = 0.0,
                        sourceDurationYears = 0.0,
                        conditionalPrepaymentRateAnnual = null,
                    )
                } else if (sleeve.mbsInterestOnlyTerms != null) {
                    position.copy(
                        sourceAnnualIncomeYield = sleeve.mbsInterestOnlyTerms.modelParameters
                            .couponStripYieldAnnual,
                        sourceDurationYears = sleeve.mbsInterestOnlyTerms.modelParameters
                            .effectiveDurationYears,
                    )
                } else {
                    position.copy(
                        sourceAnnualIncomeYield = sourceIncomeYield(sleeve.source, sourceSnapshot),
                        sourceDurationYears = sourceDuration(sleeve.source, sourceSnapshot),
                    )
                }
            }
            val sortedTransitionedIds = transitionedIds.sorted()
            val revision = previous.revision + if (sortedTransitionedIds.isEmpty()) 0L else 1L
            val next = buildState(
                ref = ref,
                profile = profile,
                positions = positions,
                revision = revision,
                lastSelectionDate = previous.lastSelectionDate,
                nextSelectionDate = previous.nextSelectionDate,
                lastReweightDate = previous.lastReweightDate,
                nextReweightDate = previous.nextReweightDate,
                lastMortgageRateAnnual = previous.lastMortgageRateAnnual,
                bootstrapCompositionHash = previous.bootstrapCompositionHash,
                asOf = at,
            )
            if (sortedTransitionedIds.isNotEmpty()) {
                val measures = portfolioMeasures(next.positions)
                records += CompositeReferenceRebalanceRecord(
                    id = "composite-extraordinary-source-to-cash:${ref.benchmarkId}:" +
                        "v${ref.version}:${at.epochSeconds}:r$revision",
                    benchmarkRef = ref,
                    kind = CompositeReferenceActionKind.EXTRAORDINARY_SOURCE_TO_CASH,
                    effectiveDate = CompositeScheduleResolver.localDateAt(definition.baseCurrency, at),
                    effectiveAt = at,
                    addedSleeveIds = emptyList(),
                    removedSleeveIds = emptyList(),
                    cashSubstitutedSleeveIds = sortedTransitionedIds,
                    compositionHashBefore = previous.compositionHash,
                    compositionHashAfter = next.compositionHash,
                    turnoverRate = 0.0,
                    resultingGrossExposure = measures.gross,
                    resultingNetExposure = measures.net,
                    resultingDurationYears = measures.duration,
                    revision = revision,
                )
            }
            nextStates[ref] = next
        }
        val nextBook = CompositeReferenceBook(nextStates)
        return CompositeReferenceBookAdvance(
            book = nextBook,
            referenceLogReturns = nextBook.states.keys.associateWith { 0.0 },
            estimatedAnnualIncomeYields = nextBook.states.mapValues { it.value.estimatedAnnualIncomeYield },
            effectiveDurationsYears = nextBook.states.mapValues { it.value.effectiveDurationYears },
            rebalanceRecords = records,
        )
    }

    fun profileFingerprint(ref: BenchmarkRef, profile: CompositeReferenceProfile): String =
        stableHex(
            listOf(
                ref,
                profile.allocationModel,
                profile.grossExposureConstraint,
                profile.netExposureConstraint,
                profile.annualFinancingSpread,
                profile.annualFinancingSpreadOrigin,
                profile.targetVolatilityAnnual,
                profile.targetVolatilityOrigin,
                profile.riskLookbackTradingDays,
                profile.riskLookbackOrigin,
                profile.durationConstraint,
                profile.driftThreshold,
                profile.driftThresholdOrigin,
                canonicalSchedule(profile.selectionSchedule),
                canonicalSchedule(profile.reweightSchedule),
                profile.supportLevel,
                profile.provenance,
                profile.confidence,
                profile.officialSourceUrls.joinToString(","),
                profile.assumptionId,
                profile.sleeves.joinToString(separator = ";", transform = ::canonicalSleeve),
            ).joinToString("|"),
        )

    fun compositionHash(positions: List<CompositeReferenceSleevePosition>): String = stableHex(
        positions.sortedBy(CompositeReferenceSleevePosition::sleeveId).joinToString("|") {
            "${it.sleeveId}:${it.direction}:${it.targetWeightMagnitude.toBits()}:${it.sourceAvailable}"
        },
    )

    private fun buildState(
        ref: BenchmarkRef,
        profile: CompositeReferenceProfile,
        positions: List<CompositeReferenceSleevePosition>,
        revision: Long,
        lastSelectionDate: LocalDate?,
        nextSelectionDate: LocalDate?,
        lastReweightDate: LocalDate?,
        nextReweightDate: LocalDate?,
        lastMortgageRateAnnual: Double,
        bootstrapCompositionHash: String,
        asOf: Instant,
    ): CompositeReferenceState {
        val sorted = positions.sortedBy(CompositeReferenceSleevePosition::sleeveId)
        val measures = portfolioMeasures(sorted)
        return CompositeReferenceState(
            benchmarkRef = ref,
            positions = sorted,
            revision = revision,
            lastSelectionDate = lastSelectionDate,
            nextSelectionDate = nextSelectionDate,
            lastReweightDate = lastReweightDate,
            nextReweightDate = nextReweightDate,
            estimatedAnnualIncomeYield = measures.income,
            grossExposure = measures.gross,
            netExposure = measures.net,
            effectiveDurationYears = measures.duration,
            lastMortgageRateAnnual = lastMortgageRateAnnual,
            bootstrapCompositionHash = bootstrapCompositionHash,
            profileFingerprint = profileFingerprint(ref, profile),
            compositionHash = compositionHash(sorted),
            asOf = asOf,
        )
    }

    private fun allocateTargets(
        profile: CompositeReferenceProfile,
        positions: List<CompositeReferenceSleevePosition>,
        preserveMembership: Boolean,
    ): SignedExposureTargets {
        val positionsById = positions.associateBy(CompositeReferenceSleevePosition::sleeveId)
        val orderedIds = profile.sleeves.map(CompositeReferenceSleeve::sleeveId).sorted()
        val signs = profile.sleeves.associate { sleeve ->
            sleeve.sleeveId to if (sleeve.direction == CompositeSleeveDirection.LONG) 1 else -1
        }
        val minimum = profile.sleeves.associate { sleeve ->
            sleeve.sleeveId to (sleeve.minimumWeight ?: 0.0)
        }.toMutableMap()
        val maximum = profile.sleeves.associate { sleeve ->
            val prior = positionsById.getValue(sleeve.sleeveId)
            val membershipMaximum = if (
                preserveMembership && prior.targetWeightMagnitude <= ACTIVE_EPSILON
            ) {
                0.0
            } else {
                sleeve.maximumWeight ?: profile.grossExposureConstraint.maximum
            }
            sleeve.sleeveId to membershipMaximum
        }.toMutableMap()
        val raw = profile.sleeves.associate { sleeve ->
            val position = positionsById.getValue(sleeve.sleeveId)
            val directionSignal = if (sleeve.direction == CompositeSleeveDirection.LONG) {
                position.trendSignal
            } else {
                -position.trendSignal
            }
            val base = sleeve.targetWeight ?: sleeve.riskBudget ?: 1.0
            val value = when (profile.allocationModel) {
                CompositeAllocationModel.STATIC_TARGET,
                CompositeAllocationModel.TARGET_BAND,
                -> base
                CompositeAllocationModel.EQUAL_RISK_CONTRIBUTION ->
                    requireNotNull(sleeve.riskBudget) / sqrt(position.annualizedVariance)
                CompositeAllocationModel.TACTICAL_ALLOCATION,
                CompositeAllocationModel.ACTIVE_LONG_SHORT,
                CompositeAllocationModel.SYSTEMATIC_ALTERNATIVE,
                -> base * exp(directionSignal.coerceIn(-3.0, 3.0))
                CompositeAllocationModel.DURATION_HEDGE -> base
            }
            sleeve.sleeveId to value.coerceAtLeast(MIN_RAW_SCORE)
        }
        var allocated = SignedExposureAllocator.allocate(
            orderedIds = orderedIds,
            signs = signs,
            rawScores = raw,
            minimumWeights = minimum,
            maximumWeights = maximum,
            grossConstraint = profile.grossExposureConstraint,
            netConstraint = profile.netExposureConstraint,
        )
        profile.targetVolatilityAnnual?.takeIf {
            profile.grossExposureConstraint.target == null
        }?.let { targetVolatility ->
            val estimatedVariance = allocated.magnitudes.entries.sumOf { (id, weight) ->
                weight * weight * positionsById.getValue(id).annualizedVariance
            }
            val scale = targetVolatility / sqrt(estimatedVariance.coerceAtLeast(MIN_VARIANCE))
            val scaledGross = (allocated.grossExposure * scale).coerceIn(
                profile.grossExposureConstraint.minimum,
                profile.grossExposureConstraint.maximum,
            )
            val scaledNet = profile.netExposureConstraint.target ?: (
                allocated.netExposure * scale
                ).coerceIn(
                profile.netExposureConstraint.minimum,
                profile.netExposureConstraint.maximum,
            )
            allocated = SignedExposureAllocator.allocate(
                orderedIds = orderedIds,
                signs = signs,
                rawScores = raw,
                minimumWeights = minimum,
                maximumWeights = maximum,
                grossConstraint = com.amond.kmpbook.domain.model.fund.CompositeExposureConstraint(
                    target = scaledGross,
                    minimum = scaledGross,
                    maximum = scaledGross,
                    origin = com.amond.kmpbook.domain.model.fund.CompositeParameterOrigin
                        .CALIBRATED_ASSUMPTION,
                ),
                netConstraint = com.amond.kmpbook.domain.model.fund.CompositeExposureConstraint(
                    target = scaledNet,
                    minimum = scaledNet,
                    maximum = scaledNet,
                    origin = com.amond.kmpbook.domain.model.fund.CompositeParameterOrigin
                        .CALIBRATED_ASSUMPTION,
                ),
            )
        }
        return if (profile.allocationModel == CompositeAllocationModel.DURATION_HEDGE) {
            durationAdjustedTargets(profile, positionsById, allocated, minimum, maximum)
        } else {
            allocated
        }
    }

    private fun durationAdjustedTargets(
        profile: CompositeReferenceProfile,
        positionsById: Map<String, CompositeReferenceSleevePosition>,
        allocated: SignedExposureTargets,
        minimum: Map<String, Double>,
        maximum: Map<String, Double>,
    ): SignedExposureTargets {
        val longSleeves = profile.sleeves.filter { it.direction == CompositeSleeveDirection.LONG }
        require(longSleeves.size == 2 && longSleeves.size == profile.sleeves.size) {
            "DURATION_HEDGE requires exactly two long sleeves."
        }
        val first = longSleeves[0]
        val second = longSleeves[1]
        val firstDuration = positionsById.getValue(first.sleeveId).sourceDurationYears
        val secondDuration = positionsById.getValue(second.sleeveId).sourceDurationYears
        require(abs(firstDuration - secondDuration) > 1e-12) {
            "DURATION_HEDGE source durations must be distinct."
        }
        val targetDuration = requireNotNull(profile.durationConstraint).targetYears
            ?: (profile.durationConstraint.minimumYears + profile.durationConstraint.maximumYears) / 2.0
        val gross = allocated.grossExposure
        val firstWeight = (targetDuration - gross * secondDuration) /
            (firstDuration - secondDuration)
        require(firstWeight in minimum.getValue(first.sleeveId) - ACTIVE_EPSILON..
            maximum.getValue(first.sleeveId) + ACTIVE_EPSILON
        ) { "DURATION_HEDGE target is infeasible for the first sleeve band." }
        val secondWeight = gross - firstWeight
        require(secondWeight in minimum.getValue(second.sleeveId) - ACTIVE_EPSILON..
            maximum.getValue(second.sleeveId) + ACTIVE_EPSILON
        ) { "DURATION_HEDGE target is infeasible for the second sleeve band." }
        val magnitudes = allocated.magnitudes.toMutableMap()
        magnitudes[first.sleeveId] = firstWeight.coerceIn(
            minimum.getValue(first.sleeveId),
            maximum.getValue(first.sleeveId),
        )
        magnitudes[second.sleeveId] = secondWeight.coerceIn(
            minimum.getValue(second.sleeveId),
            maximum.getValue(second.sleeveId),
        )
        val resultingDuration = magnitudes.getValue(first.sleeveId) * firstDuration +
            magnitudes.getValue(second.sleeveId) * secondDuration
        require(resultingDuration in profile.durationConstraint.minimumYears - ACTIVE_EPSILON..
            profile.durationConstraint.maximumYears + ACTIVE_EPSILON
        ) { "DURATION_HEDGE allocation violates its duration constraint." }
        return SignedExposureTargets(magnitudes.toSortedMap().toMap(), gross, gross)
    }

    private fun sourceObservation(
        ref: BenchmarkRef,
        compositeCurrency: ReferenceCurrency,
        sleeve: CompositeReferenceSleeve,
        position: CompositeReferenceSleevePosition,
        sourceCatalog: ReferenceSourceCatalog,
        frame: ReferenceSourceReturnFrame,
        previousMortgageRate: Double,
        mortgageRate: Double,
        annualRiskFreeRate: Double,
        yearFraction: Double,
        from: Instant,
        to: Instant,
    ): ReferenceSourceObservation {
        val available = position.sourceAvailable && sourceAvailable(sleeve.source, frame)
        if (!available) {
            return ReferenceSourceObservation(
                logReturn = ln1p(annualRiskFreeRate * yearFraction),
                incomeYield = 0.0,
                durationYears = 0.0,
                conditionalPrepaymentRateAnnual = null,
                sourceAvailable = false,
            )
        }
        val sourceCurrency = sourceCatalog.currencyOf(sleeve.source)
        val fxReturn = if (sourceCurrency == compositeCurrency) {
            require(sleeve.hedgeRatioToCompositeBaseCurrency == null)
            0.0
        } else {
            val hedgeRatio = requireNotNull(sleeve.hedgeRatioToCompositeBaseCurrency)
            (1.0 - hedgeRatio) * frame.fxLogReturns.getValue(
                ReferenceCurrencyPair(sourceCurrency, compositeCurrency),
            )
        }
        val terms = sleeve.mbsInterestOnlyTerms
        if (terms != null) {
            val parameters = terms.modelParameters
            val previousCpr = position.conditionalPrepaymentRateAnnual
                ?: parameters.baseConditionalPrepaymentRateAnnual
            val rateDeclineInOnePercentUnits = (previousMortgageRate - mortgageRate) / .01
            val shock = parameters.annualConditionalPrepaymentRateVolatility * sqrt(yearFraction) *
                DeterministicRandom.keyed(
                    seed,
                    "composite-mbs-io-cpr:$ref:${sleeve.sleeveId}:${from.epochSeconds}:${to.epochSeconds}",
                ).nextGaussian()
            val cpr = (
                previousCpr + parameters.cprIncreasePerOnePercentMortgageRateDecline *
                    rateDeclineInOnePercentUnits + shock
                ).coerceIn(0.0, 1.0)
            val rateChange = mortgageRate - previousMortgageRate
            val logReturn = -parameters.effectiveDurationYears * rateChange -
                IO_PREPAYMENT_VALUE_SENSITIVITY * (cpr - previousCpr) + fxReturn
            return ReferenceSourceObservation(
                logReturn = logReturn.coerceIn(-MAX_INTERVAL_LOG_MOVE, MAX_INTERVAL_LOG_MOVE),
                incomeYield = parameters.couponStripYieldAnnual,
                durationYears = parameters.effectiveDurationYears,
                conditionalPrepaymentRateAnnual = cpr,
                sourceAvailable = true,
            )
        }
        val localReturn = sourceLogReturn(sleeve.source, frame)
        return ReferenceSourceObservation(
            logReturn = (localReturn + fxReturn).coerceIn(-MAX_INTERVAL_LOG_MOVE, MAX_INTERVAL_LOG_MOVE),
            incomeYield = sourceIncomeYield(sleeve.source, frame),
            durationYears = sourceDuration(sleeve.source, frame),
            conditionalPrepaymentRateAnnual = null,
            sourceAvailable = true,
        )
    }

    private fun portfolioMeasures(positions: List<CompositeReferenceSleevePosition>): ReferencePortfolioMeasures =
        ReferencePortfolioMeasures(
            gross = positions.sumOf(CompositeReferenceSleevePosition::currentWeightMagnitude),
            net = positions.sumOf(CompositeReferenceSleevePosition::signedCurrentWeight),
            income = positions.sumOf { position ->
                if (position.direction == CompositeSleeveDirection.LONG) {
                    position.currentWeightMagnitude * position.sourceAnnualIncomeYield
                } else {
                    0.0
                }
            }.coerceIn(0.0, 1.0),
            duration = positions.sumOf { it.signedCurrentWeight * it.sourceDurationYears },
        )

    private fun validatedDefinitions(
        definitions: Collection<BenchmarkDefinition>,
        sourceCatalog: ReferenceSourceCatalog,
    ): Map<BenchmarkRef, BenchmarkDefinition> {
        require(definitions.isNotEmpty())
        val byRef = definitions.associateBy(BenchmarkDefinition::ref)
        require(byRef.size == definitions.size)
        byRef.forEach { (ref, definition) ->
            require(definition.engineKind == BenchmarkEngineKind.COMPOSITE_REFERENCE)
            require(definition.baseCurrency == ReferenceCurrency.KRW ||
                definition.baseCurrency == ReferenceCurrency.USD
            ) { "Composite schedule venue is defined only for KRW and USD references." }
            val profile = requireNotNull(definition.compositeReferenceProfile)
            val longSleeves = profile.sleeves.filter {
                it.direction == CompositeSleeveDirection.LONG
            }
            val shortSleeves = profile.sleeves.filter {
                it.direction == CompositeSleeveDirection.SHORT
            }
            SignedExposureFeasibility.requireFeasible(
                longMinimum = longSleeves.sumOf { it.minimumWeight ?: 0.0 },
                longMaximum = longSleeves.sumOf {
                    it.maximumWeight ?: profile.grossExposureConstraint.maximum
                },
                shortMinimum = shortSleeves.sumOf { it.minimumWeight ?: 0.0 },
                shortMaximum = shortSleeves.sumOf {
                    it.maximumWeight ?: profile.grossExposureConstraint.maximum
                },
                grossConstraint = profile.grossExposureConstraint,
                netConstraint = profile.netExposureConstraint,
            )
            if (profile.allocationModel == CompositeAllocationModel.DURATION_HEDGE) {
                require(profile.sleeves.size == 2 && profile.sleeves.all {
                    it.direction == CompositeSleeveDirection.LONG
                }) { "DURATION_HEDGE requires exactly two long sleeves." }
            }
            profile.sleeves.forEach { sleeve ->
                require(sourceCatalog.contains(sleeve.source))
                if (sleeve.source.kind == CompositeReferenceSourceKind.BENCHMARK) {
                    val componentRef = requireNotNull(sleeve.source.benchmarkRef)
                    require(componentRef != ref)
                    val component = sourceCatalog.benchmarkDefinitions.getValue(componentRef)
                    require(component.engineKind != BenchmarkEngineKind.COMPOSITE_REFERENCE)
                    require(component.engineKind != BenchmarkEngineKind.COARSE_FACTOR_PROXY)
                }
                val sourceCurrency = sourceCatalog.currencyOf(sleeve.source)
                if (sourceCurrency == definition.baseCurrency) {
                    require(sleeve.hedgeRatioToCompositeBaseCurrency == null)
                } else {
                    require(sleeve.hedgeRatioToCompositeBaseCurrency == 0.0) {
                        "Composite FX hedging requires an explicit hedge-cost contract."
                    }
                }
            }
        }
        return byRef
    }

    private fun sourceKey(source: CompositeReferenceSource): String = when (source.kind) {
        CompositeReferenceSourceKind.BENCHMARK -> requireNotNull(source.benchmarkRef).toString()
        CompositeReferenceSourceKind.INSTRUMENT -> requireNotNull(source.instrumentId)
    }

    private fun sourceAvailable(
        source: CompositeReferenceSource,
        snapshot: ReferenceSourceSnapshot,
    ): Boolean = when (source.kind) {
        CompositeReferenceSourceKind.BENCHMARK -> true
        CompositeReferenceSourceKind.INSTRUMENT ->
            snapshot.instrumentAvailability.getValue(requireNotNull(source.instrumentId))
    }

    private fun sourceAvailable(
        source: CompositeReferenceSource,
        frame: ReferenceSourceReturnFrame,
    ): Boolean = when (source.kind) {
        CompositeReferenceSourceKind.BENCHMARK -> true
        CompositeReferenceSourceKind.INSTRUMENT ->
            frame.instrumentAvailability.getValue(requireNotNull(source.instrumentId))
    }

    private fun sourceLogReturn(source: CompositeReferenceSource, frame: ReferenceSourceReturnFrame): Double =
        when (source.kind) {
            CompositeReferenceSourceKind.BENCHMARK ->
                frame.benchmarkLogReturns.getValue(requireNotNull(source.benchmarkRef))
            CompositeReferenceSourceKind.INSTRUMENT ->
                frame.instrumentLogReturns.getValue(requireNotNull(source.instrumentId))
        }

    private fun sourceIncomeYield(source: CompositeReferenceSource, frame: ReferenceSourceReturnFrame): Double =
        when (source.kind) {
            CompositeReferenceSourceKind.BENCHMARK ->
                frame.benchmarkAnnualIncomeYields.getValue(requireNotNull(source.benchmarkRef))
            CompositeReferenceSourceKind.INSTRUMENT ->
                frame.instrumentAnnualIncomeYields.getValue(requireNotNull(source.instrumentId))
        }

    private fun sourceDuration(source: CompositeReferenceSource, frame: ReferenceSourceReturnFrame): Double =
        when (source.kind) {
            CompositeReferenceSourceKind.BENCHMARK ->
                frame.benchmarkDurationsYears[requireNotNull(source.benchmarkRef)] ?: 0.0
            CompositeReferenceSourceKind.INSTRUMENT ->
                frame.instrumentDurationsYears[requireNotNull(source.instrumentId)] ?: 0.0
        }

    private fun sourceIncomeYield(source: CompositeReferenceSource, snapshot: ReferenceSourceSnapshot): Double =
        when (source.kind) {
            CompositeReferenceSourceKind.BENCHMARK ->
                snapshot.benchmarkAnnualIncomeYields.getValue(requireNotNull(source.benchmarkRef))
            CompositeReferenceSourceKind.INSTRUMENT ->
                snapshot.instrumentAnnualIncomeYields.getValue(requireNotNull(source.instrumentId))
        }

    private fun sourceDuration(source: CompositeReferenceSource, snapshot: ReferenceSourceSnapshot): Double =
        when (source.kind) {
            CompositeReferenceSourceKind.BENCHMARK ->
                snapshot.benchmarkDurationsYears[requireNotNull(source.benchmarkRef)] ?: 0.0
            CompositeReferenceSourceKind.INSTRUMENT ->
                snapshot.instrumentDurationsYears[requireNotNull(source.instrumentId)] ?: 0.0
        }

    private fun stableHex(value: String): String =
        DeterministicRandom.stableHash64(value).toULong().toString(16).padStart(16, '0')

    private fun canonicalSchedule(
        schedule: com.amond.kmpbook.domain.model.fund.CompositeRebalanceSchedule,
    ): String = "${schedule.calendar}:${schedule.months.joinToString(",")}:${schedule.origin}"

    private fun canonicalSleeve(sleeve: CompositeReferenceSleeve): String {
        val terms = sleeve.mbsInterestOnlyTerms
        return listOf(
            sleeve.sleeveId,
            sleeve.source.kind,
            sleeve.source.benchmarkRef,
            sleeve.source.instrumentId,
            sleeve.direction,
            sleeve.role,
            sleeve.targetWeight,
            sleeve.minimumWeight,
            sleeve.maximumWeight,
            sleeve.targetWeightOrigin,
            sleeve.weightBandOrigin,
            sleeve.riskBudget,
            sleeve.riskBudgetOrigin,
            sleeve.annualBorrowSpread,
            sleeve.annualBorrowSpreadOrigin,
            sleeve.hedgeRatioToCompositeBaseCurrency,
            sleeve.hedgeRatioOrigin,
            terms?.prepaymentModel,
            terms?.termsProvenance,
            terms?.officialSourceUrls?.joinToString(","),
            terms?.modelParameters,
        ).joinToString(":")
    }

    companion object {
        fun forCampaignSeed(campaignSeed: Long): CompositeReferenceBookEngine =
            CompositeReferenceBookEngine(
                DeterministicRandom.mixSeed(campaignSeed, ENGINE_STREAM_ID),
            )

        private const val ENGINE_STREAM_ID: Long = 0x434F4D504F534954L
        private const val MILLISECONDS_PER_YEAR: Double = 31_557_600_000.0
        private const val TRADING_DAYS_PER_YEAR: Double = 252.0
        private const val DEFAULT_LOOKBACK_TRADING_DAYS: Int = 126
        private const val MIN_YEAR_FRACTION: Double = 1e-8
        private const val MIN_NAV_FACTOR: Double = .05
        private const val MAX_INTERVAL_LOG_MOVE: Double = 3.0
        private const val MAX_POSITION_WEIGHT: Double = 10.0
        private const val MAX_STATE_GROSS_EXPOSURE: Double = 10.0
        private const val MIN_RAW_SCORE: Double = 1e-12
        private const val MIN_VARIANCE: Double = 1e-8
        private const val ACTIVE_EPSILON: Double = 1e-12
        private const val IO_PREPAYMENT_VALUE_SENSITIVITY: Double = 1.5
        private val MAX_ADVANCE_DURATION = 1.hours
    }
}
