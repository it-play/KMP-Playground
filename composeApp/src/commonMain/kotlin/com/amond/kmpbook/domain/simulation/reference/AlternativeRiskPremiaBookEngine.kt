package com.amond.kmpbook.domain.simulation.reference

import com.amond.kmpbook.domain.model.fund.AlternativeRiskPremiaDriver
import com.amond.kmpbook.domain.model.fund.AlternativeRiskPremiaProfile
import com.amond.kmpbook.domain.model.fund.AlternativeRiskPremiaSignalDirectionPolicy
import com.amond.kmpbook.domain.model.fund.AlternativeRiskPremiaStrategyFamily
import com.amond.kmpbook.domain.model.fund.BenchmarkDefinition
import com.amond.kmpbook.domain.model.fund.BenchmarkEngineKind
import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import com.amond.kmpbook.domain.model.fund.CompositeExposureConstraint
import com.amond.kmpbook.domain.model.fund.CompositeParameterOrigin
import com.amond.kmpbook.domain.model.fund.CompositeReferenceSource
import com.amond.kmpbook.domain.model.fund.CompositeReferenceSourceKind
import com.amond.kmpbook.domain.model.market.ReferenceCurrency
import com.amond.kmpbook.domain.model.reference.AlternativeRiskPremiaAdvanceInput
import com.amond.kmpbook.domain.model.reference.AlternativeRiskPremiaActionKind
import com.amond.kmpbook.domain.model.reference.AlternativeRiskPremiaBook
import com.amond.kmpbook.domain.model.reference.AlternativeRiskPremiaBookAdvance
import com.amond.kmpbook.domain.model.reference.AlternativeRiskPremiaDriverPosition
import com.amond.kmpbook.domain.model.reference.AlternativeRiskPremiaRebalanceRecord
import com.amond.kmpbook.domain.model.reference.AlternativeRiskPremiaState
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

/** Component-first engine for market-neutral, credit-RV and macro-trend risk premia. */
class AlternativeRiskPremiaBookEngine private constructor(private val seed: Long) {
    /** Rebuilds the campaign-start ALT allocation used to anchor persisted ledger lineage. */
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
    ): AlternativeRiskPremiaBook {
        val byRef = validatedDefinitions(definitions, sourceCatalog)
        val states = linkedMapOf<BenchmarkRef, AlternativeRiskPremiaState>()
        byRef.toSortedMap().forEach { (ref, definition) ->
            val profile = requireNotNull(definition.alternativeRiskPremiaProfile)
            val positions = profile.drivers.map { driver ->
                val sourceKey = sourceKey(driver.source)
                val available = sourceAvailable(driver.source, sourceSnapshot)
                val volatility = DeterministicRandom.keyed(
                    seed,
                    "alternative-initial-vol:$ref:$sourceKey",
                ).nextDouble(.08, .22)
                AlternativeRiskPremiaDriverPosition(
                    driverId = driver.driverId,
                    strategyFamily = driver.strategyFamily,
                    currentSignedWeight = 0.0,
                    targetSignedWeight = 0.0,
                    annualizedVariance = volatility * volatility,
                    trendSignal = DeterministicRandom.keyed(
                        seed,
                        "alternative-initial-signal:$ref:$sourceKey:$atDate",
                    ).nextDouble(-.4, .4),
                    lastSourceLogReturn = 0.0,
                    sourceAvailable = available,
                    sourceAnnualIncomeYield = if (available) {
                        sourceIncomeYield(driver.source, sourceSnapshot)
                    } else {
                        0.0
                    },
                    sourceDurationYears = if (available) {
                        sourceDuration(driver.source, sourceSnapshot)
                    } else {
                        0.0
                    },
                )
            }.sortedBy(AlternativeRiskPremiaDriverPosition::driverId)
            val targets = allocateTargets(profile, positions)
            val allocated = positions.map { position ->
                val target = targets.getValue(position.driverId)
                position.copy(currentSignedWeight = target, targetSignedWeight = target)
            }
            states[ref] = buildState(
                ref = ref,
                profile = profile,
                positions = allocated,
                revision = 0L,
                lastReweightDate = null,
                nextReweightDate = CompositeScheduleResolver.nextDateAfterInstant(
                    profile.rebalanceSchedule,
                    definition.baseCurrency,
                    at,
                ),
                bootstrapCompositionHash = compositionHash(allocated),
                asOf = at,
            )
        }
        return AlternativeRiskPremiaBook(states)
    }

    fun advanceHour(
        book: AlternativeRiskPremiaBook,
        definitions: Collection<BenchmarkDefinition>,
        sourceCatalog: ReferenceSourceCatalog,
        input: AlternativeRiskPremiaAdvanceInput,
        from: Instant,
        to: Instant,
    ): AlternativeRiskPremiaBookAdvance {
        require(book.asOf == from)
        val elapsed = to - from
        require(elapsed.isPositive() && elapsed <= MAX_ADVANCE_DURATION)
        val yearFraction = elapsed.inWholeMilliseconds.toDouble() / MILLISECONDS_PER_YEAR
        val byRef = validatedDefinitions(definitions, sourceCatalog)
        require(byRef.keys == book.states.keys)
        val nextStates = linkedMapOf<BenchmarkRef, AlternativeRiskPremiaState>()
        val returns = linkedMapOf<BenchmarkRef, Double>()
        val incomes = linkedMapOf<BenchmarkRef, Double>()
        val durations = linkedMapOf<BenchmarkRef, Double>()
        val records = mutableListOf<AlternativeRiskPremiaRebalanceRecord>()
        book.states.toSortedMap().forEach { (ref, previous) ->
            val definition = byRef.getValue(ref)
            val profile = requireNotNull(definition.alternativeRiskPremiaProfile)
            require(previous.profileFingerprint == profileFingerprint(ref, profile))
            val driverById = profile.drivers.associateBy(AlternativeRiskPremiaDriver::driverId)
            val cashSubstitutedDriverIds = previous.positions.filter { position ->
                position.sourceAvailable && !sourceAvailable(
                    driverById.getValue(position.driverId).source,
                    input.sourceFrame,
                )
            }.map(AlternativeRiskPremiaDriverPosition::driverId).sorted()
            val observations = previous.positions.associate { position ->
                val driver = driverById.getValue(position.driverId)
                position.driverId to sourceObservation(
                    profileCurrency = definition.baseCurrency,
                    driver = driver,
                    position = position,
                    sourceCatalog = sourceCatalog,
                    frame = input.sourceFrame,
                    annualRiskFreeRate = input.annualRiskFreeRate,
                    yearFraction = yearFraction,
                )
            }
            val familyReturnAverages = previous.positions
                .groupBy(AlternativeRiskPremiaDriverPosition::strategyFamily)
                .mapValues { (_, members) ->
                    members.map { observations.getValue(it.driverId).logReturn }.average()
                }
            val sourceReturns = observations.mapValues { it.value.logReturn }
            val metrics = previous.positions.map { position ->
                val observation = observations.getValue(position.driverId)
                val decay = exp(
                    -TRADING_DAYS_PER_YEAR * yearFraction / profile.signalLookbackTradingDays,
                )
                val annualizedSquaredReturn = observation.logReturn * observation.logReturn /
                    yearFraction.coerceAtLeast(MIN_YEAR_FRACTION)
                val signalObservation = when (position.strategyFamily) {
                    AlternativeRiskPremiaStrategyFamily.EQUITY_MARKET_NEUTRAL,
                    AlternativeRiskPremiaStrategyFamily.CREDIT_RELATIVE_VALUE,
                    -> observation.logReturn - familyReturnAverages.getValue(position.strategyFamily)
                    AlternativeRiskPremiaStrategyFamily.GLOBAL_MACRO_TREND -> observation.logReturn
                }
                position.copy(
                    annualizedVariance = (
                        decay * position.annualizedVariance +
                            (1.0 - decay) * annualizedSquaredReturn.coerceAtMost(
                                com.amond.kmpbook.domain.model.reference
                                    .CompositeReferenceSleevePosition.MAX_VARIANCE,
                            )
                        ).coerceIn(
                        com.amond.kmpbook.domain.model.reference
                            .CompositeReferenceSleevePosition.MIN_VARIANCE,
                        com.amond.kmpbook.domain.model.reference
                            .CompositeReferenceSleevePosition.MAX_VARIANCE,
                    ),
                    trendSignal = (
                        decay * position.trendSignal +
                            (1.0 - decay) * signalObservation /
                            sqrt(yearFraction.coerceAtLeast(MIN_YEAR_FRACTION))
                        ).coerceIn(-100.0, 100.0),
                    lastSourceLogReturn = observation.logReturn,
                    sourceAvailable = observation.sourceAvailable,
                    sourceAnnualIncomeYield = observation.incomeYield,
                    sourceDurationYears = observation.durationYears,
                )
            }
            val simplePnl = previous.positions.sumOf { position ->
                position.currentSignedWeight * expm1(sourceReturns.getValue(position.driverId))
            }
            val financingCost = (previous.grossExposure - 1.0).coerceAtLeast(0.0) *
                (input.annualRiskFreeRate + profile.annualFinancingSpread) * yearFraction
            val shortBorrowCost = metrics.sumOf {
                if (it.sourceAvailable) (-it.currentSignedWeight).coerceAtLeast(0.0) else 0.0
            } * profile.annualShortBorrowSpread * yearFraction
            val implementationCost = previous.grossExposure *
                profile.annualImplementationCostRate * yearFraction
            val shortDividendExpense = metrics.sumOf { position ->
                (-position.currentSignedWeight).coerceAtLeast(0.0) *
                    position.sourceAnnualIncomeYield * yearFraction
            }
            val portfolioLogReturn = ln(
                (1.0 + simplePnl - financingCost - shortBorrowCost - implementationCost -
                    shortDividendExpense)
                    .coerceAtLeast(MIN_NAV_FACTOR),
            ).coerceIn(-MAX_INTERVAL_LOG_MOVE, MAX_INTERVAL_LOG_MOVE)
            val navFactor = exp(portfolioLogReturn)
            var drifted = metrics.map { position ->
                val sign = if (position.currentSignedWeight >= 0.0) 1.0 else -1.0
                val magnitude = abs(position.currentSignedWeight) *
                    exp(sourceReturns.getValue(position.driverId)) / navFactor
                position.copy(currentSignedWeight = sign * magnitude.coerceIn(0.0, MAX_POSITION_WEIGHT))
            }
            val driftedGross = drifted.sumOf { abs(it.currentSignedWeight) }
            if (driftedGross > MAX_STATE_GROSS_EXPOSURE) {
                val scale = MAX_STATE_GROSS_EXPOSURE / driftedGross
                drifted = drifted.map { position ->
                    position.copy(currentSignedWeight = position.currentSignedWeight * scale)
                }
            }
            var revision = previous.revision
            var lastReweightDate = previous.lastReweightDate
            var nextReweightDate = previous.nextReweightDate
            val reweightDue = CompositeScheduleResolver.crossesClose(
                definition.baseCurrency,
                previous.nextReweightDate,
                from,
                to,
            )
            if (cashSubstitutedDriverIds.isNotEmpty()) {
                require(!reweightDue) {
                    "Source-to-cash transition must be observed after any coincident scheduled close."
                }
                revision += 1L
                val measures = portfolioMeasures(drifted)
                records += AlternativeRiskPremiaRebalanceRecord(
                    id = "alternative-extraordinary-source-to-cash:${ref.benchmarkId}:v${ref.version}:" +
                        "${from.epochSeconds}:r$revision",
                    benchmarkRef = ref,
                    kind = AlternativeRiskPremiaActionKind.EXTRAORDINARY_SOURCE_TO_CASH,
                    effectiveDate = CompositeScheduleResolver.localDateAt(definition.baseCurrency, from),
                    effectiveAt = from,
                    cashSubstitutedDriverIds = cashSubstitutedDriverIds,
                    compositionHashBefore = previous.compositionHash,
                    compositionHashAfter = compositionHash(drifted),
                    turnoverRate = 0.0,
                    resultingGrossExposure = measures.gross,
                    resultingNetExposure = measures.net,
                    resultingDurationYears = measures.duration,
                    revision = revision,
                )
            } else if (reweightDue) {
                val due = requireNotNull(previous.nextReweightDate)
                val before = drifted
                val targets = allocateTargets(profile, before)
                drifted = before.map { position ->
                    val target = targets.getValue(position.driverId)
                    position.copy(currentSignedWeight = target, targetSignedWeight = target)
                }
                revision += 1L
                lastReweightDate = due
                nextReweightDate = CompositeScheduleResolver.nextDate(
                    profile.rebalanceSchedule,
                    definition.baseCurrency,
                    due,
                )
                val measures = portfolioMeasures(drifted)
                val turnover = .5 * drifted.sumOf { after ->
                    abs(after.targetSignedWeight - before.first { it.driverId == after.driverId }.currentSignedWeight)
                }
                records += AlternativeRiskPremiaRebalanceRecord(
                    id = "alternative-reweight:${ref.benchmarkId}:v${ref.version}:$due:r$revision",
                    benchmarkRef = ref,
                    kind = AlternativeRiskPremiaActionKind.REWEIGHT,
                    effectiveDate = due,
                    effectiveAt = CompositeScheduleResolver.closeAt(definition.baseCurrency, due),
                    cashSubstitutedDriverIds = emptyList(),
                    compositionHashBefore = compositionHash(previous.positions),
                    compositionHashAfter = compositionHash(drifted),
                    turnoverRate = turnover,
                    resultingGrossExposure = measures.gross,
                    resultingNetExposure = measures.net,
                    resultingDurationYears = measures.duration,
                    revision = revision,
                )
            }
            val next = buildState(
                ref = ref,
                profile = profile,
                positions = drifted,
                revision = revision,
                lastReweightDate = lastReweightDate,
                nextReweightDate = nextReweightDate,
                bootstrapCompositionHash = previous.bootstrapCompositionHash,
                asOf = to,
            )
            nextStates[ref] = next
            returns[ref] = portfolioLogReturn
            incomes[ref] = next.estimatedAnnualIncomeYield
            durations[ref] = next.effectiveDurationYears
        }
        return AlternativeRiskPremiaBookAdvance(
            book = AlternativeRiskPremiaBook(nextStates),
            referenceLogReturns = returns,
            estimatedAnnualIncomeYields = incomes,
            effectiveDurationsYears = durations,
            rebalanceRecords = records.sortedWith(
                compareBy<AlternativeRiskPremiaRebalanceRecord> { it.benchmarkRef }
                    .thenBy(AlternativeRiskPremiaRebalanceRecord::revision),
            ),
        )
    }

    /**
     * Applies listing availability learned after the interval's price calculation without adding
     * another return. The unchanged exposure becomes base-currency cash at [at], and the sticky
     * transition is recorded after any scheduled action already produced for that same instant.
     */
    fun reconcileAvailability(
        book: AlternativeRiskPremiaBook,
        definitions: Collection<BenchmarkDefinition>,
        sourceSnapshot: ReferenceSourceSnapshot,
        at: Instant,
    ): AlternativeRiskPremiaBookAdvance {
        require(book.asOf == at)
        val byRef = definitions.associateBy(BenchmarkDefinition::ref)
        require(byRef.size == definitions.size && byRef.keys == book.states.keys)
        val nextStates = linkedMapOf<BenchmarkRef, AlternativeRiskPremiaState>()
        val records = mutableListOf<AlternativeRiskPremiaRebalanceRecord>()
        book.states.toSortedMap().forEach { (ref, previous) ->
            val definition = byRef.getValue(ref)
            require(definition.engineKind == BenchmarkEngineKind.ALTERNATIVE_RISK_PREMIA)
            val profile = requireNotNull(definition.alternativeRiskPremiaProfile)
            require(previous.profileFingerprint == profileFingerprint(ref, profile))
            val driverById = profile.drivers.associateBy(AlternativeRiskPremiaDriver::driverId)
            val transitionedIds = mutableListOf<String>()
            val positions = previous.positions.map { position ->
                val source = driverById.getValue(position.driverId).source
                val remainsAvailable = position.sourceAvailable && sourceAvailable(source, sourceSnapshot)
                if (!remainsAvailable) {
                    if (position.sourceAvailable) transitionedIds += position.driverId
                    position.copy(
                        lastSourceLogReturn = if (position.sourceAvailable) {
                            0.0
                        } else {
                            position.lastSourceLogReturn
                        },
                        sourceAvailable = false,
                        sourceAnnualIncomeYield = 0.0,
                        sourceDurationYears = 0.0,
                    )
                } else {
                    position.copy(
                        sourceAnnualIncomeYield = sourceIncomeYield(source, sourceSnapshot),
                        sourceDurationYears = sourceDuration(source, sourceSnapshot),
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
                lastReweightDate = previous.lastReweightDate,
                nextReweightDate = previous.nextReweightDate,
                bootstrapCompositionHash = previous.bootstrapCompositionHash,
                asOf = at,
            )
            if (sortedTransitionedIds.isNotEmpty()) {
                val measures = portfolioMeasures(next.positions)
                records += AlternativeRiskPremiaRebalanceRecord(
                    id = "alternative-extraordinary-source-to-cash:${ref.benchmarkId}:" +
                        "v${ref.version}:${at.epochSeconds}:r$revision",
                    benchmarkRef = ref,
                    kind = AlternativeRiskPremiaActionKind.EXTRAORDINARY_SOURCE_TO_CASH,
                    effectiveDate = CompositeScheduleResolver.localDateAt(definition.baseCurrency, at),
                    effectiveAt = at,
                    cashSubstitutedDriverIds = sortedTransitionedIds,
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
        val nextBook = AlternativeRiskPremiaBook(nextStates)
        return AlternativeRiskPremiaBookAdvance(
            book = nextBook,
            referenceLogReturns = nextBook.states.keys.associateWith { 0.0 },
            estimatedAnnualIncomeYields = nextBook.states.mapValues { it.value.estimatedAnnualIncomeYield },
            effectiveDurationsYears = nextBook.states.mapValues { it.value.effectiveDurationYears },
            rebalanceRecords = records,
        )
    }

    fun profileFingerprint(ref: BenchmarkRef, profile: AlternativeRiskPremiaProfile): String =
        stableHex(
            listOf(
                ref,
                profile.strategyFamilies.joinToString(","),
                profile.signalModel,
                profile.longGrossExposureConstraint,
                profile.shortGrossExposureConstraint,
                profile.netExposureConstraint,
                profile.targetVolatilityAnnual,
                profile.targetVolatilityOrigin,
                profile.signalLookbackTradingDays,
                profile.signalLookbackOrigin,
                canonicalSchedule(profile.rebalanceSchedule),
                profile.annualFinancingSpread,
                profile.annualFinancingSpreadOrigin,
                profile.annualShortBorrowSpread,
                profile.annualShortBorrowSpreadOrigin,
                profile.annualImplementationCostRate,
                profile.annualImplementationCostOrigin,
                profile.supportLevel,
                profile.provenance,
                profile.confidence,
                profile.officialSourceUrls.joinToString(","),
                profile.assumptionId,
                profile.drivers.joinToString(";") { driver ->
                    listOf(
                        driver.driverId,
                        driver.source.kind,
                        driver.source.benchmarkRef,
                        driver.source.instrumentId,
                        driver.strategyFamily,
                        driver.signalDirectionPolicy,
                        driver.targetRiskBudget,
                        driver.riskBudgetOrigin,
                        driver.hedgeRatioToProfileBaseCurrency,
                        driver.hedgeRatioOrigin,
                    ).joinToString(":")
                },
            ).joinToString("|"),
        )

    fun compositionHash(positions: List<AlternativeRiskPremiaDriverPosition>): String = stableHex(
        positions.sortedBy(AlternativeRiskPremiaDriverPosition::driverId).joinToString("|") {
            "${it.driverId}:${it.strategyFamily}:${it.targetSignedWeight.toBits()}:${it.sourceAvailable}"
        },
    )

    private fun buildState(
        ref: BenchmarkRef,
        profile: AlternativeRiskPremiaProfile,
        positions: List<AlternativeRiskPremiaDriverPosition>,
        revision: Long,
        lastReweightDate: LocalDate?,
        nextReweightDate: LocalDate?,
        bootstrapCompositionHash: String,
        asOf: Instant,
    ): AlternativeRiskPremiaState {
        val sorted = positions.sortedBy(AlternativeRiskPremiaDriverPosition::driverId)
        val measures = portfolioMeasures(sorted)
        return AlternativeRiskPremiaState(
            benchmarkRef = ref,
            positions = sorted,
            revision = revision,
            lastReweightDate = lastReweightDate,
            nextReweightDate = nextReweightDate,
            estimatedAnnualIncomeYield = measures.income,
            grossExposure = measures.gross,
            netExposure = measures.net,
            effectiveDurationYears = measures.duration,
            bootstrapCompositionHash = bootstrapCompositionHash,
            profileFingerprint = profileFingerprint(ref, profile),
            compositionHash = compositionHash(sorted),
            asOf = asOf,
        )
    }

    private fun allocateTargets(
        profile: AlternativeRiskPremiaProfile,
        positions: List<AlternativeRiskPremiaDriverPosition>,
    ): Map<String, Double> {
        val byId = positions.associateBy(AlternativeRiskPremiaDriverPosition::driverId)
        val orderedIds = profile.drivers.map(AlternativeRiskPremiaDriver::driverId).sorted()
        val signs = profile.drivers.associate { driver ->
            val signal = byId.getValue(driver.driverId).trendSignal
            val sign = when (driver.signalDirectionPolicy) {
                AlternativeRiskPremiaSignalDirectionPolicy.LONG_ONLY -> 1
                AlternativeRiskPremiaSignalDirectionPolicy.SHORT_ONLY -> -1
                AlternativeRiskPremiaSignalDirectionPolicy.DYNAMIC_LONG_SHORT ->
                    if (signal >= 0.0) 1 else -1
            }
            driver.driverId to sign
        }.toMutableMap()
        val driverById = profile.drivers.associateBy(AlternativeRiskPremiaDriver::driverId)
        val dynamicPositions = positions.filter { position ->
            driverById.getValue(position.driverId).signalDirectionPolicy ==
                AlternativeRiskPremiaSignalDirectionPolicy.DYNAMIC_LONG_SHORT
        }
        if (profile.longGrossExposureConstraint.maximum > EPSILON &&
            signs.values.none { it > 0 }
        ) {
            val id = dynamicPositions.maxWith(
                compareBy<AlternativeRiskPremiaDriverPosition> { it.trendSignal }
                    .thenByDescending(AlternativeRiskPremiaDriverPosition::driverId),
            ).driverId
            signs[id] = 1
        }
        if (profile.shortGrossExposureConstraint.maximum > EPSILON &&
            signs.values.none { it < 0 }
        ) {
            val id = dynamicPositions.minWith(
                compareBy<AlternativeRiskPremiaDriverPosition> { it.trendSignal }
                    .thenBy(AlternativeRiskPremiaDriverPosition::driverId),
            ).driverId
            signs[id] = -1
        }
        val raw = profile.drivers.associate { driver ->
            val position = byId.getValue(driver.driverId)
            driver.driverId to (
                requireNotNull(driver.targetRiskBudget) /
                    sqrt(position.annualizedVariance) *
                    (.25 + abs(position.trendSignal).coerceAtMost(4.0))
                ).coerceAtLeast(MIN_RAW_SCORE)
        }
        val groupTargets = resolveAlternativeGroupTargets(profile, signs, raw, byId)
        val grossConstraint = CompositeExposureConstraint(
            target = groupTargets.first + groupTargets.second,
            minimum = groupTargets.first + groupTargets.second,
            maximum = groupTargets.first + groupTargets.second,
            origin = CompositeParameterOrigin.CALIBRATED_ASSUMPTION,
        )
        val netTarget = groupTargets.first - groupTargets.second
        val netConstraint = CompositeExposureConstraint(
            target = netTarget,
            minimum = netTarget,
            maximum = netTarget,
            origin = CompositeParameterOrigin.CALIBRATED_ASSUMPTION,
        )
        val maximum = orderedIds.associateWith { id ->
            if (signs.getValue(id) > 0) groupTargets.first else groupTargets.second
        }
        val allocated = SignedExposureAllocator.allocate(
            orderedIds = orderedIds,
            signs = signs,
            rawScores = raw,
            minimumWeights = orderedIds.associateWith { 0.0 },
            maximumWeights = maximum,
            grossConstraint = grossConstraint,
            netConstraint = netConstraint,
        )
        return allocated.magnitudes.mapValues { (id, magnitude) -> signs.getValue(id) * magnitude }
    }

    private fun resolveAlternativeGroupTargets(
        profile: AlternativeRiskPremiaProfile,
        signs: Map<String, Int>,
        raw: Map<String, Double>,
        positions: Map<String, AlternativeRiskPremiaDriverPosition>,
    ): Pair<Double, Double> {
        var long = profile.longGrossExposureConstraint.target
            ?: (profile.longGrossExposureConstraint.minimum +
                profile.longGrossExposureConstraint.maximum) / 2.0
        var short = profile.shortGrossExposureConstraint.target
            ?: (profile.shortGrossExposureConstraint.minimum +
                profile.shortGrossExposureConstraint.maximum) / 2.0
        profile.targetVolatilityAnnual?.takeIf {
            profile.longGrossExposureConstraint.target == null &&
                profile.shortGrossExposureConstraint.target == null
        }?.let { targetVolatility ->
            val rawTotal = raw.values.sum().coerceAtLeast(MIN_RAW_SCORE)
            val estimatedVariance = raw.entries.sumOf { (id, score) ->
                val normalized = score / rawTotal
                normalized * normalized * positions.getValue(id).annualizedVariance
            }
            val scale = targetVolatility / sqrt(estimatedVariance.coerceAtLeast(MIN_VARIANCE))
            long = (long * scale).coerceIn(
                profile.longGrossExposureConstraint.minimum,
                profile.longGrossExposureConstraint.maximum,
            )
            short = (short * scale).coerceIn(
                profile.shortGrossExposureConstraint.minimum,
                profile.shortGrossExposureConstraint.maximum,
            )
        }
        if (signs.values.none { it > 0 }) long = 0.0
        if (signs.values.none { it < 0 }) short = 0.0
        val desiredNet = profile.netExposureConstraint.target ?: (long - short).coerceIn(
            profile.netExposureConstraint.minimum,
            profile.netExposureConstraint.maximum,
        )
        val currentNet = long - short
        if (abs(currentNet - desiredNet) > EPSILON) {
            val halfDifference = (desiredNet - currentNet) / 2.0
            long = (long + halfDifference).coerceIn(
                profile.longGrossExposureConstraint.minimum,
                profile.longGrossExposureConstraint.maximum,
            )
            short = (short - halfDifference).coerceIn(
                profile.shortGrossExposureConstraint.minimum,
                profile.shortGrossExposureConstraint.maximum,
            )
        }
        require(long - short in profile.netExposureConstraint.minimum - EPSILON..
            profile.netExposureConstraint.maximum + EPSILON)
        return long to short
    }

    private fun sourceObservation(
        profileCurrency: ReferenceCurrency,
        driver: AlternativeRiskPremiaDriver,
        position: AlternativeRiskPremiaDriverPosition,
        sourceCatalog: ReferenceSourceCatalog,
        frame: ReferenceSourceReturnFrame,
        annualRiskFreeRate: Double,
        yearFraction: Double,
    ): ReferenceSourceObservation {
        val available = position.sourceAvailable && sourceAvailable(driver.source, frame)
        if (!available) {
            return ReferenceSourceObservation(
                logReturn = ln1p(annualRiskFreeRate * yearFraction),
                incomeYield = 0.0,
                durationYears = 0.0,
                conditionalPrepaymentRateAnnual = null,
                sourceAvailable = false,
            )
        }
        val localReturn = sourceLogReturn(driver.source, frame)
        val sourceCurrency = sourceCatalog.currencyOf(driver.source)
        val fxReturn = if (sourceCurrency == profileCurrency) {
            require(driver.hedgeRatioToProfileBaseCurrency == null)
            0.0
        } else {
            val hedgeRatio = requireNotNull(driver.hedgeRatioToProfileBaseCurrency)
            (1.0 - hedgeRatio) * frame.fxLogReturns.getValue(
                ReferenceCurrencyPair(sourceCurrency, profileCurrency),
            )
        }
        return ReferenceSourceObservation(
            logReturn = (localReturn + fxReturn).coerceIn(-MAX_INTERVAL_LOG_MOVE, MAX_INTERVAL_LOG_MOVE),
            incomeYield = sourceIncomeYield(driver.source, frame),
            durationYears = sourceDuration(driver.source, frame),
            conditionalPrepaymentRateAnnual = null,
            sourceAvailable = true,
        )
    }

    private fun portfolioMeasures(
        positions: List<AlternativeRiskPremiaDriverPosition>,
    ): ReferencePortfolioMeasures = ReferencePortfolioMeasures(
        gross = positions.sumOf { abs(it.currentSignedWeight) },
        net = positions.sumOf(AlternativeRiskPremiaDriverPosition::currentSignedWeight),
        income = positions.sumOf { position ->
            position.currentSignedWeight.coerceAtLeast(0.0) * position.sourceAnnualIncomeYield
        }.coerceIn(0.0, 1.0),
        duration = positions.sumOf { it.currentSignedWeight * it.sourceDurationYears },
    )

    private fun validatedDefinitions(
        definitions: Collection<BenchmarkDefinition>,
        sourceCatalog: ReferenceSourceCatalog,
    ): Map<BenchmarkRef, BenchmarkDefinition> {
        require(definitions.isNotEmpty())
        val byRef = definitions.associateBy(BenchmarkDefinition::ref)
        require(byRef.size == definitions.size)
        byRef.forEach { (ref, definition) ->
            require(definition.engineKind == BenchmarkEngineKind.ALTERNATIVE_RISK_PREMIA)
            require(definition.baseCurrency == ReferenceCurrency.KRW ||
                definition.baseCurrency == ReferenceCurrency.USD
            ) { "Alternative-risk-premia schedule venue is defined only for KRW and USD references." }
            val profile = requireNotNull(definition.alternativeRiskPremiaProfile)
            requireDirectionFeasibility(profile)
            val longMinimum = profile.longGrossExposureConstraint.target
                ?: profile.longGrossExposureConstraint.minimum
            val longMaximum = profile.longGrossExposureConstraint.target
                ?: profile.longGrossExposureConstraint.maximum
            val shortMinimum = profile.shortGrossExposureConstraint.target
                ?: profile.shortGrossExposureConstraint.minimum
            val shortMaximum = profile.shortGrossExposureConstraint.target
                ?: profile.shortGrossExposureConstraint.maximum
            SignedExposureFeasibility.requireFeasible(
                longMinimum = longMinimum,
                longMaximum = longMaximum,
                shortMinimum = shortMinimum,
                shortMaximum = shortMaximum,
                grossConstraint = com.amond.kmpbook.domain.model.fund.CompositeExposureConstraint(
                    target = null,
                    minimum = longMinimum + shortMinimum,
                    maximum = longMaximum + shortMaximum,
                    origin = CompositeParameterOrigin.CALIBRATED_ASSUMPTION,
                ),
                netConstraint = profile.netExposureConstraint,
            )
            profile.drivers.forEach { driver ->
                require(sourceCatalog.contains(driver.source))
                if (driver.source.kind == CompositeReferenceSourceKind.BENCHMARK) {
                    val componentRef = requireNotNull(driver.source.benchmarkRef)
                    require(componentRef != ref)
                    val kind = sourceCatalog.benchmarkDefinitions.getValue(componentRef).engineKind
                    require(kind != BenchmarkEngineKind.ALTERNATIVE_RISK_PREMIA)
                    require(kind != BenchmarkEngineKind.COMPOSITE_REFERENCE)
                    require(kind != BenchmarkEngineKind.COARSE_FACTOR_PROXY)
                }
                val sourceCurrency = sourceCatalog.currencyOf(driver.source)
                if (sourceCurrency == definition.baseCurrency) {
                    require(driver.hedgeRatioToProfileBaseCurrency == null)
                } else {
                    require(driver.hedgeRatioToProfileBaseCurrency == 0.0) {
                        "Alternative-risk-premia FX hedging requires an explicit hedge-cost contract."
                    }
                }
            }
        }
        return byRef
    }

    private fun requireDirectionFeasibility(profile: AlternativeRiskPremiaProfile) {
        val needsLong = profile.longGrossExposureConstraint.maximum > EPSILON
        val needsShort = profile.shortGrossExposureConstraint.maximum > EPSILON
        val longCapable = profile.drivers.filter { driver ->
            driver.signalDirectionPolicy != AlternativeRiskPremiaSignalDirectionPolicy.SHORT_ONLY
        }
        val shortCapable = profile.drivers.filter { driver ->
            driver.signalDirectionPolicy != AlternativeRiskPremiaSignalDirectionPolicy.LONG_ONLY
        }
        if (needsLong) require(longCapable.isNotEmpty()) {
            "Positive long exposure requires a LONG_ONLY or DYNAMIC_LONG_SHORT driver."
        }
        if (needsShort) require(shortCapable.isNotEmpty()) {
            "Positive short exposure requires a SHORT_ONLY or DYNAMIC_LONG_SHORT driver."
        }
        if (needsLong && needsShort) {
            require(longCapable.any { longDriver ->
                shortCapable.any { shortDriver -> shortDriver.driverId != longDriver.driverId }
            }) { "Simultaneous long and short exposure requires two direction-compatible drivers." }
        }
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

    companion object {
        fun forCampaignSeed(campaignSeed: Long): AlternativeRiskPremiaBookEngine =
            AlternativeRiskPremiaBookEngine(
                DeterministicRandom.mixSeed(campaignSeed, ENGINE_STREAM_ID),
            )

        private const val ENGINE_STREAM_ID: Long = 0x414C545249534B50L
        private const val MILLISECONDS_PER_YEAR: Double = 31_557_600_000.0
        private const val TRADING_DAYS_PER_YEAR: Double = 252.0
        private const val MIN_YEAR_FRACTION: Double = 1e-8
        private const val MIN_NAV_FACTOR: Double = .05
        private const val MAX_INTERVAL_LOG_MOVE: Double = 3.0
        private const val MAX_POSITION_WEIGHT: Double = 10.0
        private const val MAX_STATE_GROSS_EXPOSURE: Double = 10.0
        private const val MIN_RAW_SCORE: Double = 1e-12
        private const val MIN_VARIANCE: Double = 1e-8
        private const val EPSILON: Double = 1e-8
        private val MAX_ADVANCE_DURATION = 1.hours
    }
}
