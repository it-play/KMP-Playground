package com.amond.kmpbook.domain.simulation.fund

import com.amond.kmpbook.domain.methodology.EquityMethodologyCandidate
import com.amond.kmpbook.domain.methodology.EquityMethodologyCorporateActionDecision
import com.amond.kmpbook.domain.methodology.EquityMethodologyCorporateActionInput
import com.amond.kmpbook.domain.methodology.EquityMethodologyConstraintInput
import com.amond.kmpbook.domain.methodology.EquityMethodologyRegistry
import com.amond.kmpbook.domain.methodology.EquityMethodologyRemovalInput
import com.amond.kmpbook.domain.methodology.EquityMethodologyReconstitutionResult
import com.amond.kmpbook.domain.methodology.EquityMethodologyScheduledAction
import com.amond.kmpbook.domain.methodology.EquityMethodologySelection
import com.amond.kmpbook.domain.methodology.EquityMethodologySelectionInput
import com.amond.kmpbook.domain.methodology.EquityMethodologySignals
import com.amond.kmpbook.domain.methodology.EquityMethodologyWeightingInput
import com.amond.kmpbook.domain.methodology.StandardEquityMethodologySignalIds
import com.amond.kmpbook.domain.model.fund.BenchmarkDefinition
import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import com.amond.kmpbook.domain.model.fund.EquityMethodologyProfile
import com.amond.kmpbook.domain.model.fund.EquityMethodologyPathState
import com.amond.kmpbook.domain.model.fund.FundReferenceUniverse
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioAdvance
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioBook
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioBookAdvance
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioState
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioActionKind
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioCorporateAction
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioCorporateActionConsiderationKind
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioCorporateActionKind
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioCompositionHasher
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioLimits
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioPlan
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioPosition
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioRecord
import com.amond.kmpbook.domain.model.fund.MethodologyEquitySector
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.market.Sector
import com.amond.kmpbook.domain.simulation.fund.reconstitution.CanonicalScheduledReconstitution
import com.amond.kmpbook.domain.simulation.fund.reconstitution.ReconstitutedReferenceCandidates
import com.amond.kmpbook.domain.simulation.market.MacroEnvironment
import com.amond.kmpbook.domain.simulation.price.DeterministicRandom
import com.amond.kmpbook.domain.time.GameCalendar
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * 등록된 버전형 주식 방법론을 비거래 기준자산에 적용하는 provider-neutral 결정론적 host다.
 *
 * 경로 의존 상태는 모두 [ReferencePortfolioState]에 있고, 이 객체가 보유하는 캐시는 seed와 연도만으로
 * 다시 만들 수 있는 불변 파생 데이터다. 따라서 provisional/final 가격 패스가 같은 입력을 계산해도
 * 구성안·수익률·원장이 달라지지 않는다.
 * 상태의 포지션은 방법론/index의 reference portfolio이며 실제 펀드의 현재·미래 holdings나
 * 플레이어가 주문할 수 있는 기업 목록이 아니다. 복제 방식과 법적 구조는 상품 계약 검증·라우팅
 * 메타데이터이며, 보수·추적오차·레버리지·FX hedge·옵션 효과는 기준수익률 뒤의 상품 계층에서
 * 별도로 계산한다.
 */
class ReferencePortfolioEngine private constructor(
    private val seed: Long,
    private val methodologyRegistry: EquityMethodologyRegistry,
) {
    private val ordinaryReferenceEquities: List<SimulatedReferenceEquity> =
        buildReferenceEquities() + buildKoreanReferenceEquities()
    private val ordinaryReferenceEquitiesByUniverse: Map<FundReferenceUniverse, List<SimulatedReferenceEquity>> =
        ordinaryReferenceEquities.groupBy(SimulatedReferenceEquity::referenceUniverse)
    private val ordinaryReferenceEquityIdsByUniverse: Map<FundReferenceUniverse, Set<String>> =
        ordinaryReferenceEquitiesByUniverse.mapValues { (_, equities) ->
            equities.mapTo(linkedSetOf(), SimulatedReferenceEquity::assetId)
        }
    private val ordinaryReferenceEquityIds: Set<String> = ordinaryReferenceEquities
        .mapTo(linkedSetOf(), SimulatedReferenceEquity::assetId)
    private val spinOffReferenceEquities: List<SimulatedReferenceEquity> =
        ordinaryReferenceEquitiesByUniverse.values.flatMap(::buildSpinOffReferenceEquities)
    private val spinOffChildAssetIdsByUniverse: Map<FundReferenceUniverse, Set<String>> =
        spinOffReferenceEquities.groupBy(SimulatedReferenceEquity::referenceUniverse)
            .mapValues { (_, equities) -> equities.mapTo(linkedSetOf(), SimulatedReferenceEquity::assetId) }
    private val spinOffChildAssetIds: Set<String> = spinOffReferenceEquities
        .mapTo(linkedSetOf(), SimulatedReferenceEquity::assetId)
    private val referenceEquities: List<SimulatedReferenceEquity> =
        ordinaryReferenceEquities + spinOffReferenceEquities
    private val referenceEquityById = referenceEquities.associateBy(SimulatedReferenceEquity::assetId)
    private val referenceIdentityById = referenceEquities.associate { equity ->
        equity.assetId to ReferenceAssetIdentity(
            assetId = equity.assetId,
            displaySymbol = equity.displaySymbol,
            displayName = equity.displayName,
            sector = equity.sector,
            methodologySector = equity.methodologySector,
        )
    }
    private val annualSnapshotByIdCache = mutableMapOf<Int, Map<String, SimulatedReferenceEquitySnapshot>>()
    private val selectionSnapshotByIdCache =
        mutableMapOf<Pair<EquityMethodologyProfile, LocalDate>, Map<String, SimulatedReferenceEquitySnapshot>>()
    private val bootstrapMarketReturnCache = mutableMapOf<LocalDate, Double>()
    private val bootstrapSectorReturnsCache = mutableMapOf<LocalDate, DoubleArray>()
    private val selectionTradingFractionCache = mutableMapOf<Pair<EquityMethodologyProfile, LocalDate>, Double>()
    private val selectionMarketReturnCache = mutableMapOf<LocalDate, Double>()
    private val selectionSectorReturnsCache = mutableMapOf<LocalDate, DoubleArray>()
    private val corporateActionByDateCache =
        mutableMapOf<Pair<EquityMethodologyProfile, LocalDate>, ReferencePortfolioCorporateAction?>()
    private val unavailableScheduledAssetIdsCache =
        mutableMapOf<Pair<EquityMethodologyProfile, LocalDate>, Set<String>>()
    private val preflightedProfiles = mutableSetOf<EquityMethodologyProfile>()
    private val compiledMethodologies = mutableMapOf<BenchmarkRef, CompiledEquityMethodology>()
    private val canonicalStateAtCache =
        mutableMapOf<Pair<BenchmarkRef, Instant>, ReferencePortfolioState>()

    /** 저장 상태가 이 캠페인 seed의 비거래 기준자산 원본을 참조하는지 확인한다. */
    internal fun hasCanonicalReferenceIdentity(position: ReferencePortfolioPosition): Boolean =
        referenceAssetIdentity(position.assetId) != null

    internal fun hasCanonicalReferenceAssetId(assetId: String): Boolean =
        assetId in referenceEquityById

    internal fun isCanonicalCorporateAction(
        definition: BenchmarkDefinition,
        action: ReferencePortfolioCorporateAction,
    ): Boolean = canonicalCorporateActionOn(compile(definition), action.announcementDate) == action

    internal fun canonicalCorporateActionsThrough(
        definition: BenchmarkDefinition,
        throughDate: LocalDate,
    ): List<ReferencePortfolioCorporateAction> {
        val methodology = compile(definition)
        require(throughDate >= methodology.profile.effectiveFrom)
        val lastDate = minOf(throughDate, GameCalendar.CAMPAIGN_END_DATE)
        return buildList {
            var date = methodology.profile.effectiveFrom
            while (date <= lastDate) {
                canonicalCorporateActionOn(methodology, date)?.let(::add)
                date = date.plus(1, DateTimeUnit.DAY)
            }
        }
    }

    internal fun canonicalCorporateActionDecision(
        definition: BenchmarkDefinition,
        action: ReferencePortfolioCorporateAction,
        currentAssetIds: Set<String>,
    ): EquityMethodologyCorporateActionDecision? = runCatching {
        val methodology = compile(definition)
        require(canonicalCorporateActionOn(methodology, action.announcementDate) == action)
        corporateActionDecision(
            methodology = methodology,
            event = action,
            currentIds = currentAssetIds,
        )
    }.getOrNull()

    internal fun canonicalCorporateActionTargetWeights(
        definition: BenchmarkDefinition,
        action: ReferencePortfolioCorporateAction,
        currentPositions: List<ReferencePortfolioPosition>,
        kind: ReferencePortfolioActionKind,
        weightReferenceMarketValues: Map<String, Double>? = null,
    ): Map<String, Double>? = runCatching {
        val methodology = compile(definition)
        require(canonicalCorporateActionOn(methodology, action.announcementDate) == action)
        val currentIds = currentPositions.mapTo(linkedSetOf(), ReferencePortfolioPosition::assetId)
        val positions = when (kind) {
            ReferencePortfolioActionKind.CONSTITUENT_MERGER,
            ReferencePortfolioActionKind.SPIN_OFF_ADDITION,
            ReferencePortfolioActionKind.TERMINAL_REMOVAL,
            -> {
                val expectedEventKind = when (kind) {
                    ReferencePortfolioActionKind.CONSTITUENT_MERGER ->
                        ReferencePortfolioCorporateActionKind.MERGER
                    ReferencePortfolioActionKind.SPIN_OFF_ADDITION ->
                        ReferencePortfolioCorporateActionKind.SPIN_OFF
                    ReferencePortfolioActionKind.TERMINAL_REMOVAL ->
                        ReferencePortfolioCorporateActionKind.TERMINAL_REMOVAL
                }
                require(action.kind == expectedEventKind)
                val decision = requireNotNull(
                    corporateActionDecision(methodology, action, currentIds),
                )
                positionsForCorporateAction(
                    currentPositions = currentPositions,
                    event = action,
                    decision = decision,
                    effectiveDate = action.effectiveDate,
                    methodology = methodology,
                    weightReferenceMarketValues = weightReferenceMarketValues,
                )
            }
            ReferencePortfolioActionKind.SPIN_OFF_REMOVAL -> {
                require(action.kind == ReferencePortfolioCorporateActionKind.SPIN_OFF)
                val childId = requireNotNull(action.secondaryAssetId)
                require(childId in currentIds && currentPositions.size > 1)
                currentPositions.filterNot { position -> position.assetId == childId }
                    .normalizeBothWeights()
            }
            else -> error("Only corporate-action plans have canonical corporate target weights.")
        }
        buildMap {
            positions.forEach { position -> put(position.assetId, position.targetWeight) }
        }
    }.getOrNull()

    internal fun canonicalCorporateActionTransitionSteps(
        definition: BenchmarkDefinition,
        action: ReferencePortfolioCorporateAction,
    ) = runCatching {
        val methodology = compile(definition)
        require(canonicalCorporateActionOn(methodology, action.announcementDate) == action)
        corporateActionTransitionSteps(methodology, action)
    }.getOrNull()

    internal fun canonicalWeightingTargetWeights(
        definition: BenchmarkDefinition,
        plan: ReferencePortfolioPlan,
    ): Map<String, Double>? = runCatching {
        require(
            plan.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION ||
                plan.kind == ReferencePortfolioActionKind.SCHEDULED_REWEIGHT ||
                plan.kind == ReferencePortfolioActionKind.CONSTRAINT_REWEIGHT,
        )
        val methodology = compile(definition)
        require(plan.benchmarkRef == definition.ref)
        val marketValues = requireNotNull(plan.weightReferenceMarketValues)
        val snapshots = snapshotMapForKnownDataAt(plan.weightReferenceDate, methodology)
        val selected = plan.positions.map { position ->
            RankedReferenceCandidate(
                snapshot = requireNotNull(snapshots[position.assetId]),
                compositeRank = position.selectionRank,
            )
        }
        targetWeights(
            methodology = methodology,
            selected = selected,
            actionKind = plan.kind,
            observationDate = plan.weightReferenceDate,
            effectiveDate = plan.effectiveDate,
            rawFloatMarketValues = marketValues,
        )
    }.getOrNull()

    /** Stateless compatibility projection; path-dependent validators must use the overload below. */
    internal fun canonicalScheduledSelectionRanks(
        definition: BenchmarkDefinition,
        plan: ReferencePortfolioPlan,
    ): Map<String, Int>? {
        if (compile(definition).policy.usesPathState) return null
        return canonicalScheduledReconstitution(
            definition = definition,
            plan = plan,
            previousPathState = EquityMethodologyPathState.EMPTY,
        )?.selectionRanks
    }

    internal fun canonicalScheduledReconstitution(
        definition: BenchmarkDefinition,
        plan: ReferencePortfolioPlan,
        previousPathState: EquityMethodologyPathState,
        knownAt: Instant? = null,
    ): CanonicalScheduledReconstitution? = runCatching {
        require(plan.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION)
        require(plan.benchmarkRef == definition.ref)
        val methodology = compile(definition)
        val action = requireNotNull(scheduledActionOn(methodology, plan.effectiveDate))
        require(action.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION)
        require(action.selectionDate == plan.selectionDate)
        require(action.weightReferenceDate == plan.weightReferenceDate)
        val canonicalState = knownAt?.let { at ->
            canonicalReplayedStateAt(definition, at)
        }
        val canonicalPlan = if (canonicalState == null) {
            plan
        } else {
            requireNotNull(
                canonicalState.pendingPlans.singleOrNull { pending ->
                    pending.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION &&
                        pending.effectiveDate == action.effectiveDate
                },
            )
        }
        require(canonicalPlan.selectionDate == action.selectionDate)
        require(canonicalPlan.weightReferenceDate == action.weightReferenceDate)
        val availabilityDate = requireNotNull(canonicalPlan.selectionAvailabilityDate)
        val reconstitution = selectConstituents(
            methodology = methodology,
            action = action,
            incumbentAssetIds = requireNotNull(canonicalPlan.selectionIncumbentAssetIds).toSet(),
            previousPathState = canonicalState?.methodologyPathState ?: previousPathState,
            unavailableOnDate = availabilityDate,
        )
        val canonicalWeightReferenceMarketValues = if (
            methodology.policy.usesSelectionSnapshotMarketValuesForScheduledReconstitution
        ) {
            canonicalScheduledReconstitutionWeightReferenceMarketValues(
                methodology = methodology,
                action = action,
                reconstitution = reconstitution,
            )
        } else {
            null
        }
        val canonicalTargetWeights = canonicalWeightReferenceMarketValues?.let { marketValues ->
            targetWeights(
                methodology = methodology,
                selected = reconstitution.candidates,
                actionKind = action.kind,
                observationDate = action.weightReferenceDate,
                effectiveDate = action.effectiveDate,
                rawFloatMarketValues = marketValues,
            )
        }
        if (canonicalState != null && canonicalWeightReferenceMarketValues != null) {
            require(canonicalPlan.weightReferenceMarketValues == canonicalWeightReferenceMarketValues) {
                "Canonical replay produced a scheduled weighting basis outside the snapshot contract."
            }
        }
        val canonicalTransitionPlans = canonicalState?.pendingPlans.orEmpty()
            .filter { pending ->
                pending.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION_TRANSITION &&
                    pending.selectionDate == action.selectionDate &&
                    pending.weightReferenceDate == action.weightReferenceDate
            }
            .sortedBy(ReferencePortfolioPlan::effectiveDate)
        CanonicalScheduledReconstitution(
            selectionRanks = buildMap {
                reconstitution.candidates.sortedBy { candidate -> candidate.snapshot.definition.assetId }
                .forEach { candidate ->
                    put(candidate.snapshot.definition.assetId, candidate.compositeRank)
                }
            },
            referenceMarketValueMultipliers = reconstitution.referenceMarketValueMultipliers,
            nextPathState = reconstitution.nextPathState,
            selectionAvailabilityDate = availabilityDate,
            weightReferenceMarketValues = canonicalWeightReferenceMarketValues,
            targetWeights = canonicalTargetWeights,
            canonicalFinalPositions = canonicalState?.let {
                buildList { addAll(canonicalPlan.positions) }
            },
            canonicalTransitionPositionsByEffectiveDate = buildMap {
                canonicalTransitionPlans.forEach { transition ->
                    put(
                        transition.effectiveDate,
                        buildList { addAll(transition.positions) },
                    )
                }
            },
        )
    }.getOrNull()

    private fun canonicalScheduledReconstitutionWeightReferenceMarketValues(
        methodology: CompiledEquityMethodology,
        action: EquityMethodologyScheduledAction,
        reconstitution: ReconstitutedReferenceCandidates,
    ): Map<String, Double> {
        require(methodology.policy.usesSelectionSnapshotMarketValuesForScheduledReconstitution)
        val rawMarketValues = deterministicSelectionSnapshotMarketValues(
            methodology = methodology,
            action = action,
            selected = reconstitution.candidates,
        )
        val multipliedMarketValues = applyReconstitutionMarketValueMultipliers(
            reconstitution = reconstitution,
            rawFloatMarketValues = rawMarketValues,
        )
        return referenceMarketValuesForWeighting(
            methodology = methodology,
            selected = reconstitution.candidates,
            actionKind = action.kind,
            observationDate = action.weightReferenceDate,
            effectiveDate = action.effectiveDate,
            rawFloatMarketValues = multipliedMarketValues,
        )
    }

    private fun canonicalReplayedStateAt(
        definition: BenchmarkDefinition,
        at: Instant,
    ): ReferencePortfolioState = canonicalStateAtCache.getOrPut(definition.ref to at) {
        val methodology = compile(definition)
        val referenceDate = methodology.schedule.marketDate(at)
        val replayed = initialStateForReplay(
            portfolioId = portfolioIdFor(definition.ref),
            definition = definition,
            atDate = referenceDate,
            at = at,
        )
        if (methodology.schedule.isTradingDate(referenceDate) &&
            methodology.schedule.hasPassedRegularOpen(referenceDate, at)
        ) {
            applyBootstrapDuePlans(replayed, methodology, referenceDate)
        } else {
            replayed
        }
    }

    /** Projects the exact effective path state from one cached, bounded canonical replay. */
    internal fun canonicalMethodologyPathStateAt(
        definition: BenchmarkDefinition,
        at: Instant,
    ): EquityMethodologyPathState {
        val methodology = compile(definition)
        if (!methodology.policy.usesPathState) return EquityMethodologyPathState.EMPTY
        return canonicalReplayedStateAt(definition, at).methodologyPathState
    }

    internal fun referenceAssetIdentity(assetId: String): ReferenceAssetIdentity? =
        referenceIdentityById[assetId]

    /** Creates exactly one campaign state for each distinct benchmark version. */
    fun initialBook(
        definitions: Collection<BenchmarkDefinition>,
        referenceDates: Map<BenchmarkRef, LocalDate>,
        at: Instant,
    ): ReferencePortfolioBook = buildInitialBook(
        definitions = definitions,
        referenceDates = referenceDates,
        at = at,
        performFullCampaignPreflight = true,
    )

    /** Rebuilds a canonical book whose identical campaign already passed game-creation preflight. */
    internal fun initialBookForReplay(
        definitions: Collection<BenchmarkDefinition>,
        referenceDates: Map<BenchmarkRef, LocalDate>,
        at: Instant,
    ): ReferencePortfolioBook = buildInitialBook(
        definitions = definitions,
        referenceDates = referenceDates,
        at = at,
        performFullCampaignPreflight = false,
    )

    private fun buildInitialBook(
        definitions: Collection<BenchmarkDefinition>,
        referenceDates: Map<BenchmarkRef, LocalDate>,
        at: Instant,
        performFullCampaignPreflight: Boolean,
    ): ReferencePortfolioBook {
        val definitionsByRef = definitionsByRef(definitions)
        require(referenceDates.keys == definitionsByRef.keys) {
            "Initial reference dates must exactly match the benchmark definition set."
        }
        val states = linkedMapOf<String, ReferencePortfolioState>()
        definitionsByRef.values.sortedBy(BenchmarkDefinition::ref).forEach { definition ->
            if (performFullCampaignPreflight) preflightScenario(compile(definition))
            val portfolioId = portfolioIdFor(definition.ref)
            states[portfolioId] = initialStateForReplay(
                portfolioId = portfolioId,
                definition = definition,
                atDate = referenceDates.getValue(definition.ref),
                at = at,
            )
        }
        return ReferencePortfolioBook(states)
    }

    /**
     * Advances every unique benchmark once. Products consume the resulting return by benchmark
     * reference; they never invoke the constituent engine independently.
     */
    fun advanceHour(
        book: ReferencePortfolioBook,
        definitions: Collection<BenchmarkDefinition>,
        referenceDates: Map<BenchmarkRef, LocalDate>,
        referenceTradingFractions: Map<BenchmarkRef, Double>,
        from: Instant,
        to: Instant,
        macro: MacroEnvironment,
    ): ReferencePortfolioBookAdvance {
        val definitionsByRef = definitionsByRef(definitions)
        val expectedRefs = book.states.values.mapTo(linkedSetOf(), ReferencePortfolioState::benchmarkRef)
        require(definitionsByRef.keys == expectedRefs) {
            "The benchmark definition set must exactly match the reference-portfolio book."
        }
        require(referenceDates.keys == expectedRefs)
        require(referenceTradingFractions.keys == expectedRefs)

        val nextStates = linkedMapOf<String, ReferencePortfolioState>()
        val returns = linkedMapOf<BenchmarkRef, Double>()
        val records = mutableListOf<ReferencePortfolioRecord>()
        book.states.toSortedMap().forEach { (portfolioId, state) ->
            val ref = state.benchmarkRef
            val advance = advanceHour(
                state = state,
                definition = definitionsByRef.getValue(ref),
                referenceDate = referenceDates.getValue(ref),
                from = from,
                to = to,
                referenceTradingFraction = referenceTradingFractions.getValue(ref),
                macro = macro,
            )
            nextStates[portfolioId] = advance.state
            returns[ref] = advance.grossReferenceLogReturn
            records.addAll(advance.records)
        }
        return ReferencePortfolioBookAdvance(
            book = ReferencePortfolioBook(nextStates),
            grossReferenceLogReturns = returns,
            records = records,
        )
    }

    private fun definitionsByRef(
        definitions: Collection<BenchmarkDefinition>,
    ): Map<BenchmarkRef, BenchmarkDefinition> {
        require(definitions.isNotEmpty()) { "At least one benchmark definition is required." }
        val grouped = definitions.groupBy(BenchmarkDefinition::ref)
        grouped.forEach { (ref, duplicates) ->
            require(duplicates.all { it == duplicates.first() }) {
                "Conflicting definitions were supplied for benchmark $ref."
            }
        }
        return grouped.mapValues { (_, duplicates) -> duplicates.first() }
    }

    private fun compile(definition: BenchmarkDefinition): CompiledEquityMethodology {
        val compiled = compiledMethodologies.getOrPut(definition.ref) {
            BenchmarkMethodologyCompiler.compile(definition, methodologyRegistry)
        }
        require(compiled.definition == definition) {
            "Conflicting definitions were supplied for benchmark ${definition.ref}."
        }
        return compiled
    }

    private fun initialScheduledAction(
        methodology: CompiledEquityMethodology,
    ): EquityMethodologyScheduledAction {
        val action = methodology.schedule.initialScheduledAction(methodology.profile)
        require(action.effectiveDate == methodology.profile.effectiveFrom) {
            "The initial scheduled action must be effective on the methodology effectiveFrom date."
        }
        requireCanonicalScheduledAction(methodology, action)
        return action
    }

    private fun scheduledActionOn(
        methodology: CompiledEquityMethodology,
        effectiveDate: LocalDate,
    ): EquityMethodologyScheduledAction? {
        val action = methodology.schedule.scheduledActionOn(methodology.profile, effectiveDate)
            ?: return null
        require(action.effectiveDate == effectiveDate) {
            "A scheduledActionOn result must be effective on the requested date."
        }
        return action
    }

    private fun nextScheduledAction(
        methodology: CompiledEquityMethodology,
        afterExclusive: LocalDate,
        kind: ReferencePortfolioActionKind? = null,
    ): EquityMethodologyScheduledAction {
        require(
            kind != ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION ||
                methodology.policy.hasRecurringScheduledReconstitution,
        ) {
            "A non-recurring methodology cannot be queried for another scheduled reconstitution."
        }
        val action = queryNextScheduledAction(
            methodology = methodology,
            afterExclusive = afterExclusive,
            kind = kind,
        )
        if (kind == null) {
            val scheduledKinds = buildList {
                if (methodology.policy.hasRecurringScheduledReconstitution) {
                    add(ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION)
                }
                if (methodology.policy.hasRecurringScheduledReweight) {
                    add(ReferencePortfolioActionKind.SCHEDULED_REWEIGHT)
                }
            }
            require(scheduledKinds.isNotEmpty()) {
                "A methodology must expose at least one recurring scheduled-action lane."
            }
            val nextByLane = scheduledKinds.map { scheduledKind ->
                queryNextScheduledAction(
                    methodology = methodology,
                    afterExclusive = afterExclusive,
                    kind = scheduledKind,
                )
            }
            require(
                nextByLane.map(EquityMethodologyScheduledAction::effectiveDate).distinct().size ==
                    nextByLane.size,
            ) {
                "The two scheduled methodology lanes cannot share one effective date."
            }
            val expected = nextByLane.minBy(EquityMethodologyScheduledAction::effectiveDate)
            require(action == expected) {
                "An unfiltered next scheduled action must be the earliest action from either lane."
            }
        }
        return action
    }

    private fun nextScheduledReconstitution(
        methodology: CompiledEquityMethodology,
        afterExclusive: LocalDate,
    ): EquityMethodologyScheduledAction? =
        if (methodology.policy.hasRecurringScheduledReconstitution) {
            nextScheduledAction(
                methodology = methodology,
                afterExclusive = afterExclusive,
                kind = ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION,
            )
        } else {
            null
        }

    private fun queryNextScheduledAction(
        methodology: CompiledEquityMethodology,
        afterExclusive: LocalDate,
        kind: ReferencePortfolioActionKind?,
    ): EquityMethodologyScheduledAction {
        val action = methodology.schedule.nextScheduledAction(
            profile = methodology.profile,
            afterExclusive = afterExclusive,
            kind = kind,
        )
        require(action.effectiveDate > afterExclusive) {
            "A next scheduled action must advance beyond afterExclusive."
        }
        require(kind == null || action.kind == kind) {
            "A next scheduled action must have the requested kind."
        }
        requireCanonicalScheduledAction(methodology, action)
        return action
    }

    private fun requireCanonicalScheduledAction(
        methodology: CompiledEquityMethodology,
        action: EquityMethodologyScheduledAction,
    ) {
        require(methodology.schedule.isTradingDate(action.selectionDate)) {
            "A scheduled selection date must be a methodology trading date."
        }
        require(methodology.schedule.isTradingDate(action.weightReferenceDate)) {
            "A scheduled weight-reference date must be a methodology trading date."
        }
        require(methodology.schedule.isTradingDate(action.effectiveDate)) {
            "A scheduled effective date must be a methodology trading date."
        }
        require(scheduledActionOn(methodology, action.effectiveDate) == action) {
            "A provider-scheduled action must round-trip through scheduledActionOn."
        }
    }

    private fun scheduledReconstitutionTransitionSteps(
        methodology: CompiledEquityMethodology,
        action: EquityMethodologyScheduledAction,
    ) = methodology.policy.scheduledReconstitutionTransitionSteps(
        profile = methodology.profile,
        action = action,
    ).also { steps ->
        require(action.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION || steps.isEmpty())
        require(steps.size <= MAX_SCHEDULED_RECONSTITUTION_TRANSITION_STEPS)
        require(steps == steps.sortedBy { step -> step.effectiveDate })
        require(steps.map { step -> step.effectiveDate }.distinct().size == steps.size)
        require(steps.zipWithNext().all { (left, right) ->
            left.completionFraction < right.completionFraction
        })
        require(steps.all { step ->
            step.effectiveDate > action.weightReferenceDate &&
                step.effectiveDate < action.effectiveDate &&
                methodology.schedule.isTradingDate(step.effectiveDate)
        })
    }

    private fun corporateActionTransitionSteps(
        methodology: CompiledEquityMethodology,
        event: ReferencePortfolioCorporateAction,
    ) = methodology.policy.corporateActionTransitionSteps(
        profile = methodology.profile,
        event = event,
    ).also { steps ->
        require(
            event.kind != ReferencePortfolioCorporateActionKind.SPIN_OFF || steps.isEmpty(),
        ) { "A spin-off cannot use the replacement-transition lane." }
        require(steps.size <= MAX_CORPORATE_ACTION_TRANSITION_STEPS)
        require(steps == steps.sortedBy { step -> step.effectiveDate })
        require(steps.map { step -> step.effectiveDate }.distinct().size == steps.size)
        require(steps.zipWithNext().all { (left, right) ->
            left.completionFraction < right.completionFraction
        })
        if (steps.isNotEmpty()) {
            require(event.kind != ReferencePortfolioCorporateActionKind.SPIN_OFF)
            require(steps.first().effectiveDate == event.effectiveDate)
            require(steps.last().completionFraction == 1.0)
            require(steps.dropLast(1).all { step -> step.completionFraction < 1.0 })
            require(steps.all { step -> methodology.schedule.isTradingDate(step.effectiveDate) })
        }
    }

    private fun nextExtraordinaryReviewDate(
        methodology: CompiledEquityMethodology,
        afterExclusive: LocalDate,
    ): LocalDate? {
        val reviewDate = methodology.policy.nextExtraordinaryRemovalReviewDate(
            methodology.profile,
            afterExclusive,
        ) ?: return null
        require(reviewDate > afterExclusive) {
            "An extraordinary-removal review date must advance beyond afterExclusive."
        }
        require(methodology.schedule.isTradingDate(reviewDate)) {
            "An extraordinary-removal review must occur on a methodology trading date."
        }
        return reviewDate
    }

    /** Replays one canonical starting state without the game-creation full-campaign preflight. */
    internal fun initialStateForReplay(
        portfolioId: String,
        definition: BenchmarkDefinition,
        atDate: LocalDate,
        at: Instant,
    ): ReferencePortfolioState {
        val methodology = compile(definition)
        val profile = methodology.profile
        val schedule = methodology.schedule
        require(atDate == schedule.marketDate(at)) {
            "The initial reference date must come from the compiled methodology schedule."
        }
        require(atDate >= profile.effectiveFrom) { "벤치마크 방법론 시행일 전에 구성을 만들 수 없습니다." }

        /*
         * A campaign starts without a pre-campaign rebalance ledger. Stamping the latest
         * quarterly date onto March weights would make the persisted schedule disagree with
         * both weights and reference market values, so replay the index-plan lifecycle while
         * deliberately keeping revision zero.
         */
        val initialAction = initialScheduledAction(methodology)
        require(initialAction.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION) {
            "The reference-portfolio host requires an initial scheduled reconstitution."
        }
        val firstReconstitution = initialAction.effectiveDate
        val firstWeightReference = initialAction.weightReferenceDate
        val reconstitution = selectConstituents(
            methodology = methodology,
            action = initialAction,
            incumbentAssetIds = emptySet(),
            previousPathState = EquityMethodologyPathState.EMPTY,
        )
        val firstReferenceValues = deterministicSelectionSnapshotMarketValues(
            methodology = methodology,
            action = initialAction,
            selected = reconstitution.candidates,
        )
        val multipliedFirstReferenceValues = applyReconstitutionMarketValueMultipliers(
            reconstitution = reconstitution,
            rawFloatMarketValues = firstReferenceValues,
        )
        val firstAdjustedReferenceValues = referenceMarketValuesForWeighting(
            methodology = methodology,
            selected = reconstitution.candidates,
            actionKind = initialAction.kind,
            observationDate = firstWeightReference,
            effectiveDate = firstReconstitution,
            rawFloatMarketValues = multipliedFirstReferenceValues,
        )
        val firstPersistedReferenceValues = removeReconstitutionMarketValueMultipliers(
            reconstitution = reconstitution,
            multipliedFloatMarketValues = firstAdjustedReferenceValues,
        )
        val firstReferencePositions = positionsForSelection(
            selected = reconstitution.candidates,
            rawFloatMarketValues = firstAdjustedReferenceValues,
            persistedReferenceMarketValues = firstPersistedReferenceValues,
            methodology = methodology,
            actionKind = initialAction.kind,
            observationDate = firstWeightReference,
            effectiveDate = firstReconstitution,
            previousPositions = emptyMap(),
        )
        val firstEffectivePositions = advanceBootstrapRange(
            methodology = methodology,
            positions = firstReferencePositions,
            firstDateExclusive = firstWeightReference,
            lastDateExclusive = firstReconstitution,
        )
        var state = ReferencePortfolioState(
            portfolioId = portfolioId,
            benchmarkRef = definition.ref,
            positions = firstEffectivePositions,
            methodologyPathState = reconstitution.nextPathState,
            revision = 0L,
            lastReconstitutionDate = firstReconstitution,
            lastRebalanceDate = firstReconstitution,
            nextReconstitutionDate = nextScheduledReconstitution(
                methodology = methodology,
                afterExclusive = firstReconstitution,
            )?.effectiveDate,
            nextRebalanceDate = nextScheduledAction(
                methodology = methodology,
                afterExclusive = firstReconstitution,
            ).effectiveDate,
            pendingPlans = emptyList(),
            lastTurnoverRate = 0.0,
            estimatedAnnualIncomeYield = portfolioIncomeYield(
                firstEffectivePositions,
                snapshotMapForKnownDataAt(firstReconstitution, methodology),
            ),
            asOf = at,
            lastAppliedActionKind = ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION,
        )

        val lastReplayDate = lastCompletedTradingDate(methodology, atDate, at)
        var replayDate = firstReconstitution
        while (replayDate <= lastReplayDate) {
            if (schedule.isTradingDate(replayDate)) {
                state = applyBootstrapDuePlans(state, methodology, replayDate)
                val snapshots = snapshotMapForKnownDataAt(replayDate, methodology)
                val trackedAssetIds = buildSet {
                    state.positions.mapTo(this, ReferencePortfolioPosition::assetId)
                    state.pendingPlans.forEach { plan ->
                        plan.positions.mapTo(this, ReferencePortfolioPosition::assetId)
                    }
                }
                val assetReturns = bootstrapAssetReturns(
                    assetIds = trackedAssetIds,
                    snapshots = snapshots,
                    date = replayDate,
                )
                val positions = advancePositions(state.positions, assetReturns)
                val plans = state.pendingPlans.map { plan ->
                    plan.copy(positions = advancePositions(plan.positions, assetReturns))
                }
                state = state.copy(
                    positions = positions,
                    pendingPlans = plans,
                    estimatedAnnualIncomeYield = portfolioIncomeYield(positions, snapshots),
                )
                state = schedulePlansAtClose(state, methodology, replayDate)
            }
            replayDate = replayDate.plus(1, DateTimeUnit.DAY)
        }
        return state.copy(asOf = at, revision = 0L)
    }

    private fun lastCompletedTradingDate(
        methodology: CompiledEquityMethodology,
        atDate: LocalDate,
        at: Instant,
    ): LocalDate {
        val schedule = methodology.schedule
        var candidate = if (schedule.hasReachedRegularClose(atDate, at)) {
            atDate
        } else {
            atDate.minus(1, DateTimeUnit.DAY)
        }
        var searchedDates = 0
        while (!schedule.isTradingDate(candidate)) {
            require(++searchedDates <= MAX_NON_TRADING_DATE_SEARCH_DAYS) {
                "The methodology schedule has no trading date in the supported search window."
            }
            candidate = candidate.minus(1, DateTimeUnit.DAY)
        }
        return candidate
    }

    /** Fails at game creation instead of discovering an impossible future rebalance years later. */
    private fun preflightScenario(methodology: CompiledEquityMethodology) {
        val profile = methodology.profile
        if (profile in preflightedProfiles) return
        require(profile.effectiveFrom.year in (REFERENCE_BASE_YEAR + 1)..MAX_SCENARIO_YEAR) {
            "기준자산 시나리오는 ${REFERENCE_BASE_YEAR + 1}~${MAX_SCENARIO_YEAR}년을 지원합니다."
        }
        var action = initialScheduledAction(methodology)
        require(action.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION)
        var incumbentIds = emptySet<String>()
        var incumbentRankById = emptyMap<String, Int>()
        var methodologyPathState = EquityMethodologyPathState.EMPTY
        var previousEffectiveDate: LocalDate? = null
        var scheduledActionCount = 0
        while (action.effectiveDate <= GameCalendar.CAMPAIGN_END_DATE) {
            require(++scheduledActionCount <= MAX_PREFLIGHT_SCHEDULED_ACTIONS) {
                "The methodology returned too many scheduled actions during campaign preflight."
            }
            previousEffectiveDate?.let { afterExclusive ->
                incumbentIds = preflightExtraordinaryReviews(
                    assetIds = incumbentIds,
                    methodology = methodology,
                    afterExclusive = afterExclusive,
                    beforeExclusive = action.effectiveDate,
                )
                incumbentRankById = incumbentRankById.filterKeys(incumbentIds::contains)
            }
            val reconstitution = when (action.kind) {
                ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION -> {
                    scheduledReconstitutionTransitionSteps(methodology, action)
                    selectConstituents(
                        methodology = methodology,
                        action = action,
                        incumbentAssetIds = incumbentIds,
                        previousPathState = methodologyPathState,
                    )
                }

                ReferencePortfolioActionKind.SCHEDULED_REWEIGHT -> {
                    require(incumbentIds.isNotEmpty()) {
                        "A scheduled reweight cannot precede the initial reconstitution."
                    }
                    val snapshots = snapshotMapForKnownDataAt(action.weightReferenceDate, methodology)
                    ReconstitutedReferenceCandidates(
                        candidates = incumbentIds.map { assetId ->
                            RankedReferenceCandidate(
                                snapshot = requireNotNull(snapshots[assetId]),
                                compositeRank = requireNotNull(incumbentRankById[assetId]),
                            )
                        }.sortedBy(RankedReferenceCandidate::compositeRank),
                        referenceMarketValueMultipliers = incumbentIds.associateWith { 1.0 },
                        nextPathState = methodologyPathState,
                    )
                }

                else -> error("Only provider-scheduled actions may be returned by the schedule.")
            }
            val rawFloatMarketValues = reconstitution.candidates.associate { candidate ->
                candidate.snapshot.definition.assetId to candidate.snapshot.floatMarketCap
            }
            val multipliedReferenceValues = if (
                action.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION
            ) {
                applyReconstitutionMarketValueMultipliers(
                    reconstitution = reconstitution,
                    rawFloatMarketValues = rawFloatMarketValues,
                )
            } else {
                rawFloatMarketValues
            }
            val adjustedReferenceValues = referenceMarketValuesForWeighting(
                methodology = methodology,
                selected = reconstitution.candidates,
                actionKind = action.kind,
                observationDate = action.weightReferenceDate,
                effectiveDate = action.effectiveDate,
                rawFloatMarketValues = multipliedReferenceValues,
            )
            targetWeights(
                methodology = methodology,
                selected = reconstitution.candidates,
                actionKind = action.kind,
                observationDate = action.weightReferenceDate,
                effectiveDate = action.effectiveDate,
                rawFloatMarketValues = adjustedReferenceValues,
            )
            incumbentIds = reconstitution.candidates.mapTo(linkedSetOf()) {
                it.snapshot.definition.assetId
            }
            incumbentRankById = reconstitution.candidates.associate { candidate ->
                candidate.snapshot.definition.assetId to candidate.compositeRank
            }
            methodologyPathState = reconstitution.nextPathState
            previousEffectiveDate = action.effectiveDate
            action = nextScheduledAction(
                methodology = methodology,
                afterExclusive = action.effectiveDate,
            )
        }
        previousEffectiveDate?.let { afterExclusive ->
            preflightExtraordinaryReviews(
                assetIds = incumbentIds,
                methodology = methodology,
                afterExclusive = afterExclusive,
                beforeExclusive = GameCalendar.CAMPAIGN_END_DATE.plus(1, DateTimeUnit.DAY),
            )
        }
        preflightedProfiles += profile
    }

    private fun preflightExtraordinaryReviews(
        assetIds: Set<String>,
        methodology: CompiledEquityMethodology,
        afterExclusive: LocalDate,
        beforeExclusive: LocalDate,
    ): Set<String> {
        var remainingIds = assetIds
        var reviewDate = nextExtraordinaryReviewDate(methodology, afterExclusive)
        var reviewCount = 0
        while (reviewDate != null && reviewDate < beforeExclusive) {
            require(++reviewCount <= MAX_PREFLIGHT_EXTRAORDINARY_REVIEWS) {
                "The methodology returned too many extraordinary-removal reviews during preflight."
            }
            val decision = extraordinaryRemovalDecision(
                methodology = methodology,
                assetIds = remainingIds,
                observationDate = reviewDate,
            )
            if (decision != null && decision.first < beforeExclusive &&
                decision.first <= GameCalendar.CAMPAIGN_END_DATE
            ) {
                val removableCount = (
                    remainingIds.size - methodology.constraints.minimumConstituentCount
                    ).coerceAtLeast(0)
                val removedIds = decision.second.asSequence()
                    .filter(remainingIds::contains)
                    .sorted()
                    .take(removableCount)
                    .toSet()
                remainingIds = remainingIds - removedIds
                require(
                    remainingIds.size in methodology.constraints.minimumConstituentCount..
                        methodology.constraints.maximumConstituentCount,
                ) {
                    "${decision.first} 특별 구성 변경 후 종목 수가 provider 제약을 벗어납니다."
                }
            }
            reviewDate = nextExtraordinaryReviewDate(methodology, reviewDate)
        }
        return remainingIds
    }

    private fun requireCapCapacity(
        assetIds: Set<String>,
        methodology: CompiledEquityMethodology,
        context: String,
    ) {
        val constraints = methodology.constraints
        require(assetIds.size in constraints.minimumConstituentCount..constraints.maximumConstituentCount) {
            "$context 구성종목 수가 provider 제약을 벗어납니다."
        }
        val individualCap = constraints.individualWeightCap ?: 1.0
        val groupCap = constraints.sectorWeightCap ?: 1.0
        val totalCapacity = assetIds.groupBy { assetId ->
            referenceEquityById.getValue(assetId).methodologySector
        }.values.sumOf { assets ->
            min(groupCap, assets.size * individualCap)
        }
        require(totalCapacity >= 1.0 - WEIGHT_ALLOCATION_EPSILON) {
            "$context 종목·섹터 상한으로 100% 비중을 만들 수 없습니다."
        }
    }

    private fun simulatedReferenceMarketValueAt(
        methodology: CompiledEquityMethodology,
        snapshot: SimulatedReferenceEquitySnapshot,
        snapshotYear: Int,
        date: LocalDate,
    ): Double {
        require(date.year == snapshotYear + 1) {
            "연말 기준 스냅샷은 바로 다음 해의 비중 기준일에만 사용합니다."
        }
        val tradingFraction = selectionTradingFractionCache.getOrPut(methodology.profile to date) {
            var tradingDays = 0
            var cursor = LocalDate(date.year, 1, 1)
            while (cursor <= date) {
                if (methodology.schedule.isTradingDate(cursor)) tradingDays += 1
                cursor = cursor.plus(1, DateTimeUnit.DAY)
            }
            tradingDays / TRADING_DAYS_PER_YEAR
        }
        val marketReturn = selectionMarketReturnCache.getOrPut(date) {
            val volatility = BOOTSTRAP_MARKET_ANNUAL_VOLATILITY * sqrt(tradingFraction)
            val random = DeterministicRandom.keyed(seed, "fund-selection-market:$date")
            BOOTSTRAP_MARKET_ANNUAL_DRIFT * tradingFraction -
                0.5 * volatility * volatility + volatility * random.nextGaussian()
        }
        val sectorReturns = selectionSectorReturnsCache.getOrPut(date) {
            val volatility = BOOTSTRAP_SECTOR_ANNUAL_VOLATILITY * sqrt(tradingFraction)
            DoubleArray(MethodologyEquitySector.entries.size) { ordinal ->
                val sector = MethodologyEquitySector.entries[ordinal]
                val random = DeterministicRandom.keyed(seed, "fund-selection-sector:$sector:$date")
                -0.5 * volatility * volatility + volatility * random.nextGaussian()
            }
        }
        val asset = snapshot.definition
        val residualVolatility = asset.annualVolatility * BOOTSTRAP_RESIDUAL_SHARE * sqrt(tradingFraction)
        val residualRandom = DeterministicRandom.keyed(
            seed,
            "fund-selection-reference-return:${asset.assetId}:$date",
        )
        val residual = -0.5 * residualVolatility * residualVolatility +
            residualVolatility * residualRandom.nextGaussian()
        val styleCarry = (
            (asset.quality - 0.5) * BOOTSTRAP_QUALITY_ANNUAL_PREMIUM +
                (asset.value - 0.5) * BOOTSTRAP_VALUE_ANNUAL_PREMIUM +
                (snapshot.indicatedDividendYield - REFERENCE_DIVIDEND_YIELD) *
                BOOTSTRAP_DIVIDEND_ANNUAL_PREMIUM
            ) * tradingFraction
        val logReturn = (
            asset.beta * marketReturn +
                SECTOR_LOADING * sectorReturns[asset.methodologySector.ordinal] +
                residual +
                styleCarry
            ).coerceIn(-MAX_SELECTION_PERIOD_LOG_MOVE, MAX_SELECTION_PERIOD_LOG_MOVE)
        return (snapshot.floatMarketCap * exp(logReturn))
            .coerceIn(MIN_REFERENCE_MARKET_CAP, MAX_REFERENCE_MARKET_CAP)
    }

    private fun simulatedReferenceMarketValueBetween(
        methodology: CompiledEquityMethodology,
        snapshot: SimulatedReferenceEquitySnapshot,
        fromDate: LocalDate,
        throughDate: LocalDate,
    ): Double {
        require(throughDate >= fromDate)
        var value = snapshot.floatMarketCap
        var cursor = fromDate.plus(1, DateTimeUnit.DAY)
        while (cursor <= throughDate) {
            if (methodology.schedule.isTradingDate(cursor)) {
                value = (value * exp(bootstrapConstituentLogReturn(snapshot, cursor)))
                    .coerceIn(MIN_REFERENCE_MARKET_CAP, MAX_REFERENCE_MARKET_CAP)
            }
            cursor = cursor.plus(1, DateTimeUnit.DAY)
        }
        return value
    }

    private fun deterministicSelectionSnapshotMarketValues(
        methodology: CompiledEquityMethodology,
        action: EquityMethodologyScheduledAction,
        selected: List<RankedReferenceCandidate>,
    ): Map<String, Double> = selected.associate { candidate ->
        candidate.snapshot.definition.assetId to simulatedReferenceMarketValueBetween(
            methodology = methodology,
            snapshot = candidate.snapshot,
            fromDate = action.selectionDate,
            throughDate = action.weightReferenceDate,
        )
    }

    private fun advanceBootstrapRange(
        methodology: CompiledEquityMethodology,
        positions: List<ReferencePortfolioPosition>,
        firstDateExclusive: LocalDate,
        lastDateExclusive: LocalDate,
    ): List<ReferencePortfolioPosition> {
        var result = positions
        var cursor = firstDateExclusive.plus(1, DateTimeUnit.DAY)
        while (cursor < lastDateExclusive) {
            if (methodology.schedule.isTradingDate(cursor)) {
                val snapshots = snapshotMapForKnownDataAt(cursor, methodology)
                result = advancePositions(
                    positions = result,
                    assetReturns = bootstrapAssetReturns(
                        assetIds = result.mapTo(linkedSetOf(), ReferencePortfolioPosition::assetId),
                        snapshots = snapshots,
                        date = cursor,
                    ),
                )
            }
            cursor = cursor.plus(1, DateTimeUnit.DAY)
        }
        return result
    }

    private fun bootstrapAssetReturns(
        assetIds: Set<String>,
        snapshots: Map<String, SimulatedReferenceEquitySnapshot>,
        date: LocalDate,
    ): Map<String, Double> = assetIds.sorted().associateWith { assetId ->
        bootstrapConstituentLogReturn(
            snapshot = requireNotNull(snapshots[assetId]) { "기준자산 후보군에 ${assetId}가 없습니다." },
            date = date,
        )
    }

    private fun bootstrapConstituentLogReturn(
        snapshot: SimulatedReferenceEquitySnapshot,
        date: LocalDate,
    ): Double {
        val asset = snapshot.definition
        val marketReturn = bootstrapMarketReturnCache.getOrPut(date) {
            val volatility = BOOTSTRAP_MARKET_ANNUAL_VOLATILITY / sqrt(TRADING_DAYS_PER_YEAR)
            val random = DeterministicRandom.keyed(seed, "fund-bootstrap-market:$date")
            BOOTSTRAP_MARKET_ANNUAL_DRIFT / TRADING_DAYS_PER_YEAR -
                0.5 * volatility * volatility + volatility * random.nextGaussian()
        }
        val sectorReturns = bootstrapSectorReturnsCache.getOrPut(date) {
            val volatility = BOOTSTRAP_SECTOR_ANNUAL_VOLATILITY / sqrt(TRADING_DAYS_PER_YEAR)
            DoubleArray(MethodologyEquitySector.entries.size) { ordinal ->
                val sector = MethodologyEquitySector.entries[ordinal]
                val random = DeterministicRandom.keyed(seed, "fund-bootstrap-sector:$sector:$date")
                -0.5 * volatility * volatility + volatility * random.nextGaussian()
            }
        }
        val sectorReturn = sectorReturns[asset.methodologySector.ordinal]

        val residualVolatility = asset.annualVolatility * BOOTSTRAP_RESIDUAL_SHARE /
            sqrt(TRADING_DAYS_PER_YEAR)
        val residualRandom = DeterministicRandom.keyed(
            seed,
            "fund-bootstrap-reference-return:${asset.assetId}:$date",
        )
        val residual = -0.5 * residualVolatility * residualVolatility +
            residualVolatility * residualRandom.nextGaussian()
        val styleCarry = (
            (asset.quality - 0.5) * BOOTSTRAP_QUALITY_ANNUAL_PREMIUM +
                (asset.value - 0.5) * BOOTSTRAP_VALUE_ANNUAL_PREMIUM +
                (snapshot.indicatedDividendYield - REFERENCE_DIVIDEND_YIELD) *
                BOOTSTRAP_DIVIDEND_ANNUAL_PREMIUM
            ) / TRADING_DAYS_PER_YEAR
        return (asset.beta * marketReturn + SECTOR_LOADING * sectorReturn + residual + styleCarry)
            .coerceIn(-MAX_BOOTSTRAP_DAILY_LOG_MOVE, MAX_BOOTSTRAP_DAILY_LOG_MOVE)
    }

    private fun applyBootstrapDuePlans(
        state: ReferencePortfolioState,
        methodology: CompiledEquityMethodology,
        referenceDate: LocalDate,
    ): ReferencePortfolioState {
        var result = state
        var appliedCount = 0
        while (result.pendingPlans.any { it.effectiveDate <= referenceDate }) {
            require(++appliedCount <= ReferencePortfolioState.MAX_PENDING_PLANS)
            val plan = result.pendingPlans.filter { it.effectiveDate <= referenceDate }.minWith(PLAN_ORDER)
            val turnover = oneWayTurnover(result.positions, plan.positions)
            val unappliedPlans = result.pendingPlans - plan
            val remainingPlans = reconcilePendingPlans(
                currentPositions = plan.positions,
                currentMethodologyPathState = plan.methodologyPathState,
                plans = unappliedPlans,
                afterEffectiveDate = plan.effectiveDate,
                methodology = methodology,
                selectionInvalidationDate = appliedSelectionInvalidationDate(
                    appliedPlan = plan,
                    remainingPlans = unappliedPlans,
                ),
                baselineEffectiveDate = plan.effectiveDate,
            )
            result = result.copy(
                positions = plan.positions,
                methodologyPathState = plan.methodologyPathState,
                revision = 0L,
                lastReconstitutionDate = if (plan.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION) {
                    plan.effectiveDate
                } else {
                    result.lastReconstitutionDate
                },
                lastRebalanceDate = plan.effectiveDate,
                nextReconstitutionDate = if (plan.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION) {
                    nextScheduledReconstitution(
                        methodology = methodology,
                        afterExclusive = plan.effectiveDate,
                    )?.effectiveDate
                } else {
                    result.nextReconstitutionDate
                },
                nextRebalanceDate = if (
                    plan.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION ||
                    plan.kind == ReferencePortfolioActionKind.SCHEDULED_REWEIGHT ||
                    result.nextRebalanceDate <= plan.effectiveDate
                ) {
                    nextScheduledAction(methodology, plan.effectiveDate).effectiveDate
                } else {
                    result.nextRebalanceDate
                },
                pendingPlans = remainingPlans,
                lastTurnoverRate = turnover,
                estimatedAnnualIncomeYield = portfolioIncomeYield(
                    plan.positions,
                    snapshotMapForKnownDataAt(referenceDate, methodology),
                ),
                lastAppliedActionKind = plan.kind,
            )
        }
        return result
    }

    private fun reconcilePendingPlans(
        currentPositions: List<ReferencePortfolioPosition>,
        currentMethodologyPathState: EquityMethodologyPathState,
        plans: List<ReferencePortfolioPlan>,
        afterEffectiveDate: LocalDate,
        methodology: CompiledEquityMethodology,
        rebaseAsOfDate: LocalDate = afterEffectiveDate,
        selectionInvalidationDate: LocalDate? = null,
        baselineEffectiveDate: LocalDate? = null,
    ): List<ReferencePortfolioPlan> {
        var baselinePositions = currentPositions
        var baselineMethodologyPathState = currentMethodologyPathState
        var latestBaselineEffectiveDate = baselineEffectiveDate
        val sortedPlans = plans.filter { it.effectiveDate >= afterEffectiveDate }
            .sortedWith(PLAN_ORDER)
        val scheduledFinalPlan = sortedPlans.singleOrNull { plan ->
            plan.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION
        }
        val pendingCorporateSelectionInvalidationDate = sortedPlans.asSequence()
            .mapNotNull(ReferencePortfolioPlan::corporateAction)
            .filter { event -> invalidatesPendingScheduledSelection(sortedPlans, event) }
            .maxOfOrNull(ReferencePortfolioCorporateAction::effectiveDate)
        val canonicalSelectionInvalidationDate = listOfNotNull(
            selectionInvalidationDate,
            pendingCorporateSelectionInvalidationDate,
        ).maxOrNull()
        val scheduledSelectionWasInvalidated = scheduledFinalPlan?.let { finalPlan ->
            canonicalSelectionInvalidationDate != null &&
                canonicalSelectionInvalidationDate >
                requireNotNull(finalPlan.selectionAvailabilityDate)
        } == true
        val reconciledScheduledFinalPlan = scheduledFinalPlan?.let { finalPlan ->
            if (scheduledSelectionWasInvalidated) {
                recompileScheduledReconstitutionPlan(
                    currentPositions = currentPositions,
                    plan = finalPlan,
                    methodology = methodology,
                    previousPathState = currentMethodologyPathState,
                    unavailableOnDate = requireNotNull(canonicalSelectionInvalidationDate),
                )
            } else {
                finalPlan
            }
        }
        val scheduledTransitionSteps = reconciledScheduledFinalPlan?.let { finalPlan ->
            val action = requireNotNull(scheduledActionOn(methodology, finalPlan.effectiveDate))
            scheduledReconstitutionTransitionSteps(methodology, action)
        }.orEmpty()
        val firstPendingTransitionDate = sortedPlans.firstOrNull { plan ->
            plan.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION_TRANSITION
        }?.effectiveDate
        var previousTransitionCompletionFraction = firstPendingTransitionDate?.let { firstDate ->
            scheduledTransitionSteps.lastOrNull { step -> step.effectiveDate < firstDate }
                ?.completionFraction
        } ?: 0.0
        val stagedCorporateStepsByEventId = sortedPlans.asSequence()
            .mapNotNull(ReferencePortfolioPlan::corporateAction)
            .distinctBy(ReferencePortfolioCorporateAction::eventId)
            .mapNotNull { event ->
                corporateActionTransitionSteps(methodology, event)
                    .takeIf { steps -> steps.isNotEmpty() }
                    ?.let { steps -> event.eventId to steps }
            }.toMap()
        val stagedCorporateFinalPlansByEventId = sortedPlans.asSequence()
            .mapNotNull { candidate ->
                val event = candidate.corporateAction ?: return@mapNotNull null
                val steps = stagedCorporateStepsByEventId[event.eventId] ?: return@mapNotNull null
                candidate.takeIf { plan ->
                    plan.kind == corporateActionPlanKind(event) &&
                        plan.effectiveDate == steps.last().effectiveDate
                }?.let { plan -> event.eventId to plan }
            }.toMap()
        val firstPendingCorporateTransitionDateByEventId = sortedPlans.asSequence()
            .filter { plan ->
                plan.kind == ReferencePortfolioActionKind.CORPORATE_ACTION_TRANSITION
            }.groupBy { plan -> requireNotNull(plan.corporateAction).eventId }
            .mapValues { (_, eventPlans) -> eventPlans.minOf(ReferencePortfolioPlan::effectiveDate) }
        val previousCorporateCompletionFractionByEventId = mutableMapOf<String, Double>()
        val result = mutableListOf<ReferencePortfolioPlan>()
        sortedPlans.forEach { plan ->
                val currentIds = baselinePositions.mapTo(linkedSetOf(), ReferencePortfolioPosition::assetId)
                val plannedIds = plan.positions.mapTo(linkedSetOf(), ReferencePortfolioPosition::assetId)
                val rebased = when (plan.kind) {
                    ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION_TRANSITION -> {
                        val finalPlan = requireNotNull(reconciledScheduledFinalPlan) {
                            "A staged reconstitution transition requires its final plan."
                        }
                        val step = requireNotNull(scheduledTransitionSteps.singleOrNull { candidate ->
                            candidate.effectiveDate == plan.effectiveDate
                        }) { "A staged reconstitution transition requires a canonical step." }
                        val incrementalCompletionFraction =
                            (step.completionFraction - previousTransitionCompletionFraction) /
                                (1.0 - previousTransitionCompletionFraction)
                        val rebuiltPositions = blendReconstitutionTransitionPositions(
                            initialPositions = baselinePositions,
                            finalPositions = finalPlan.positions,
                            previousTransitionPositions = baselinePositions,
                            completionFraction = incrementalCompletionFraction,
                            effectiveDate = plan.effectiveDate,
                        )
                        previousTransitionCompletionFraction = step.completionFraction
                        val rebuiltIds = rebuiltPositions.mapTo(linkedSetOf()) { position ->
                            position.assetId
                        }
                        plan.copy(
                            positions = rebuiltPositions,
                            addedAssetIds = (rebuiltIds - currentIds).sorted(),
                            removedAssetIds = (currentIds - rebuiltIds).sorted(),
                            transitionBaselineWeights = baselinePositions.associate { position ->
                                position.assetId to position.currentWeight
                            },
                        )
                    }

                    ReferencePortfolioActionKind.CORPORATE_ACTION_TRANSITION -> {
                        val event = requireNotNull(plan.corporateAction)
                        val steps = requireNotNull(stagedCorporateStepsByEventId[event.eventId]) {
                            "A corporate-action transition requires canonical execution steps."
                        }
                        val finalPlan = requireNotNull(
                            stagedCorporateFinalPlansByEventId[event.eventId],
                        ) { "A corporate-action transition requires its final completion plan." }
                        val step = requireNotNull(steps.singleOrNull { candidate ->
                            candidate.effectiveDate == plan.effectiveDate &&
                                candidate.completionFraction < 1.0
                        }) { "A corporate-action transition requires a canonical partial step." }
                        val previousCompletionFraction =
                            previousCorporateCompletionFractionByEventId.getOrPut(event.eventId) {
                                val firstPendingDate = requireNotNull(
                                    firstPendingCorporateTransitionDateByEventId[event.eventId],
                                )
                                steps.lastOrNull { candidate ->
                                    candidate.effectiveDate < firstPendingDate
                                }?.completionFraction ?: 0.0
                            }
                        val incrementalCompletionFraction =
                            (step.completionFraction - previousCompletionFraction) /
                                (1.0 - previousCompletionFraction)
                        val rebuiltPositions = blendReconstitutionTransitionPositions(
                            initialPositions = baselinePositions,
                            finalPositions = finalPlan.positions,
                            previousTransitionPositions = baselinePositions,
                            completionFraction = incrementalCompletionFraction,
                            effectiveDate = plan.effectiveDate,
                        )
                        previousCorporateCompletionFractionByEventId[event.eventId] =
                            step.completionFraction
                        val rebuiltIds = rebuiltPositions.mapTo(linkedSetOf()) { position ->
                            position.assetId
                        }
                        plan.copy(
                            positions = rebuiltPositions,
                            addedAssetIds = (rebuiltIds - currentIds).sorted(),
                            removedAssetIds = (currentIds - rebuiltIds).sorted(),
                            transitionBaselineWeights = baselinePositions.associate { position ->
                                position.assetId to position.currentWeight
                            },
                        )
                    }

                    ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION -> when {
                        reconciledScheduledFinalPlan == null -> null
                        methodology.policy
                            .usesSelectionSnapshotMarketValuesForScheduledReconstitution ||
                            scheduledSelectionWasInvalidated -> {
                            val baselineById = baselinePositions.associateBy(
                                ReferencePortfolioPosition::assetId,
                            )
                            val completedPositions =
                                reconciledScheduledFinalPlan.positions.map { position ->
                                    baselineById[position.assetId]?.let { baseline ->
                                        position.copy(enteredOn = baseline.enteredOn)
                                    } ?: position
                                }
                            val completedIds = completedPositions.mapTo(linkedSetOf()) { position ->
                                position.assetId
                            }
                            reconciledScheduledFinalPlan.copy(
                                positions = completedPositions,
                                addedAssetIds = (completedIds - currentIds).sorted(),
                                removedAssetIds = (currentIds - completedIds).sorted(),
                            )
                        }
                        plannedIds == currentIds -> plan
                        else -> recompileScheduledReconstitutionPlan(
                            currentPositions = baselinePositions,
                            plan = plan,
                            methodology = methodology,
                            previousPathState = baselineMethodologyPathState,
                            unavailableOnDate = rebaseAsOfDate,
                        )
                    }

                    ReferencePortfolioActionKind.SCHEDULED_REWEIGHT,
                    ReferencePortfolioActionKind.CONSTRAINT_REWEIGHT,
                    -> when {
                        plannedIds == currentIds -> plan
                        currentIds.all { it in plannedIds } -> {
                            val retainedPositions = plan.positions.filter { it.assetId in currentIds }
                            val fixedMarketValues = requireNotNull(plan.weightReferenceMarketValues)
                            val retainedMarketValues = buildMap {
                                retainedPositions.forEach { position ->
                                    put(position.assetId, fixedMarketValues.getValue(position.assetId))
                                }
                            }
                            val fixedPositions = cappedPositionsForExistingBasket(
                                positions = retainedPositions,
                                methodology = methodology,
                                actionKind = plan.kind,
                                referenceDate = plan.weightReferenceDate,
                                effectiveDate = plan.effectiveDate,
                                rawFloatMarketValues = retainedMarketValues,
                            )
                            plan.copy(
                                positions = preservePendingPlanDrift(
                                    fixedPositions = fixedPositions,
                                    previousPlanPositions = plan.positions,
                                    weightReferenceMarketValues = retainedMarketValues,
                                ),
                                addedAssetIds = emptyList(),
                                removedAssetIds = emptyList(),
                                weightReferenceMarketValues = retainedMarketValues,
                            )
                        }
                        else -> null
                    }

                    ReferencePortfolioActionKind.EXTRAORDINARY_REMOVAL -> {
                        val removals = plan.removedAssetIds.filterTo(linkedSetOf()) { it in currentIds }
                        if (removals.isEmpty() || removals.size >= baselinePositions.size) {
                            null
                        } else {
                            plan.copy(
                                positions = baselinePositions.filterNot { it.assetId in removals }
                                    .normalizeBothWeights(),
                                addedAssetIds = emptyList(),
                                removedAssetIds = removals.sorted(),
                            )
                        }
                    }

                    ReferencePortfolioActionKind.CONSTITUENT_MERGER,
                    ReferencePortfolioActionKind.SPIN_OFF_ADDITION,
                    ReferencePortfolioActionKind.SPIN_OFF_REMOVAL,
                    ReferencePortfolioActionKind.TERMINAL_REMOVAL,
                    -> {
                        val event = requireNotNull(plan.corporateAction)
                        if (stagedCorporateFinalPlansByEventId[event.eventId]?.id == plan.id) {
                            val baselineById = baselinePositions.associateBy(
                                ReferencePortfolioPosition::assetId,
                            )
                            val completedPositions = plan.positions.map { position ->
                                baselineById[position.assetId]?.let { baseline ->
                                    position.copy(enteredOn = baseline.enteredOn)
                                } ?: position
                            }
                            val completedIds = completedPositions.mapTo(linkedSetOf()) { position ->
                                position.assetId
                            }
                            plan.copy(
                                positions = completedPositions,
                                addedAssetIds = (completedIds - currentIds).sorted(),
                                removedAssetIds = (currentIds - completedIds).sorted(),
                            )
                        } else {
                            rebaseCorporateActionPlan(
                                currentPositions = baselinePositions,
                                plan = plan,
                                methodology = methodology,
                                resetExistingSpinOffWeightBasis =
                                    plan.kind == ReferencePortfolioActionKind.SPIN_OFF_ADDITION &&
                                        latestBaselineEffectiveDate == plan.effectiveDate,
                            )
                        }
                    }
                }
                if (rebased != null) {
                    val rebasedWithCanonicalPath = if (
                        rebased.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION
                    ) {
                        rebased
                    } else {
                        rebased.copy(methodologyPathState = baselineMethodologyPathState)
                    }
                    result += rebasedWithCanonicalPath
                    baselinePositions = rebasedWithCanonicalPath.positions
                    baselineMethodologyPathState = rebasedWithCanonicalPath.methodologyPathState
                    latestBaselineEffectiveDate = rebasedWithCanonicalPath.effectiveDate
                }
            }
        return result
    }

    private fun appliedSelectionInvalidationDate(
        appliedPlan: ReferencePortfolioPlan,
        remainingPlans: List<ReferencePortfolioPlan>,
    ): LocalDate? = appliedPlan.corporateAction?.effectiveDate?.takeIf {
        invalidatesPendingScheduledSelection(
            plans = remainingPlans,
            event = requireNotNull(appliedPlan.corporateAction),
        )
    }

    private fun invalidatesPendingScheduledSelection(
        plans: List<ReferencePortfolioPlan>,
        event: ReferencePortfolioCorporateAction,
    ): Boolean {
        if (event.kind == ReferencePortfolioCorporateActionKind.SPIN_OFF) return false
        val finalPlan = plans.singleOrNull { plan ->
            plan.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION
        } ?: return false
        if (event.effectiveDate > finalPlan.effectiveDate) return false
        if (event.primaryAssetId in requireNotNull(finalPlan.selectionIncumbentAssetIds)) return true
        return plans.any { pending ->
            pending.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION_TRANSITION &&
                pending.effectiveDate >= event.effectiveDate &&
                pending.positions.any { position -> position.assetId == event.primaryAssetId }
        } || finalPlan.positions.any { position -> position.assetId == event.primaryAssetId }
    }

    private fun recompileScheduledReconstitutionPlan(
        currentPositions: List<ReferencePortfolioPosition>,
        plan: ReferencePortfolioPlan,
        methodology: CompiledEquityMethodology,
        previousPathState: EquityMethodologyPathState,
        unavailableOnDate: LocalDate,
    ): ReferencePortfolioPlan {
        val action = requireNotNull(scheduledActionOn(methodology, plan.effectiveDate))
        require(action.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION)
        val selectionAvailabilityDate = maxOf(
            requireNotNull(plan.selectionAvailabilityDate),
            unavailableOnDate,
        )
        val reconstitution = selectConstituents(
            methodology = methodology,
            action = action,
            incumbentAssetIds = requireNotNull(plan.selectionIncumbentAssetIds).toSet(),
            previousPathState = previousPathState,
            unavailableOnDate = selectionAvailabilityDate,
        )
        val currentById = currentPositions.associateBy(ReferencePortfolioPosition::assetId)
        val weightReferenceMarketValues: Map<String, Double>
        val persistedReferenceMarketValues: Map<String, Double>
        if (methodology.policy.usesSelectionSnapshotMarketValuesForScheduledReconstitution) {
            weightReferenceMarketValues =
                canonicalScheduledReconstitutionWeightReferenceMarketValues(
                    methodology = methodology,
                    action = action,
                    reconstitution = reconstitution,
                )
            persistedReferenceMarketValues = removeReconstitutionMarketValueMultipliers(
                reconstitution = reconstitution,
                multipliedFloatMarketValues = weightReferenceMarketValues,
            )
        } else {
            val originalReconstitution = selectConstituents(
                methodology = methodology,
                action = action,
                incumbentAssetIds = requireNotNull(plan.selectionIncumbentAssetIds).toSet(),
                previousPathState = previousPathState,
                unavailableOnDate = requireNotNull(plan.selectionAvailabilityDate),
            )
            val originalMarketValues = requireNotNull(plan.weightReferenceMarketValues)
            val originalMultipliers = originalReconstitution.referenceMarketValueMultipliers
            val replacementCandidates = reconstitution.candidates.filter { candidate ->
                candidate.snapshot.definition.assetId !in originalMarketValues
            }
            val replacementRawMarketValues = deterministicSelectionSnapshotMarketValues(
                methodology = methodology,
                action = action,
                selected = replacementCandidates,
            )
            val replacementMultipliedMarketValues = replacementRawMarketValues.mapValues {
                (assetId, rawMarketValue) ->
                rawMarketValue * reconstitution.referenceMarketValueMultipliers.getValue(assetId)
            }
            val adjustedReplacementMarketValues = if (replacementCandidates.isEmpty()) {
                emptyMap()
            } else {
                referenceMarketValuesForWeighting(
                    methodology = methodology,
                    selected = replacementCandidates,
                    actionKind = action.kind,
                    observationDate = action.weightReferenceDate,
                    effectiveDate = action.effectiveDate,
                    rawFloatMarketValues = replacementMultipliedMarketValues,
                )
            }
            weightReferenceMarketValues = buildMap {
                reconstitution.candidates.sortedBy { candidate ->
                    candidate.snapshot.definition.assetId
                }.forEach { candidate ->
                        val assetId = candidate.snapshot.definition.assetId
                        put(
                            assetId,
                            originalMarketValues[assetId]?.let { originalMarketValue ->
                                val originalMultiplier = requireNotNull(originalMultipliers[assetId])
                                originalMarketValue *
                                reconstitution.referenceMarketValueMultipliers.getValue(assetId) /
                                    originalMultiplier
                            } ?: adjustedReplacementMarketValues.getValue(assetId),
                        )
                    }
            }
            persistedReferenceMarketValues = buildMap {
                reconstitution.candidates.sortedBy { candidate ->
                    candidate.snapshot.definition.assetId
                }.forEach { candidate ->
                        val assetId = candidate.snapshot.definition.assetId
                        val originalMultiplier = originalMultipliers[assetId]
                        val persistedMarketValue = if (originalMultiplier != null) {
                            originalMarketValues.getValue(assetId) / originalMultiplier
                        } else {
                            adjustedReplacementMarketValues.getValue(assetId) /
                                reconstitution.referenceMarketValueMultipliers.getValue(assetId)
                        }
                        require(persistedMarketValue.isFinite() && persistedMarketValue > 0.0)
                        put(assetId, persistedMarketValue)
                    }
            }
        }
        val fixedPositions = positionsForSelection(
            selected = reconstitution.candidates,
            rawFloatMarketValues = weightReferenceMarketValues,
            persistedReferenceMarketValues = persistedReferenceMarketValues,
            methodology = methodology,
            actionKind = action.kind,
            observationDate = action.weightReferenceDate,
            effectiveDate = action.effectiveDate,
            previousPositions = currentById,
        )
        val positions = preservePendingPlanDrift(
            fixedPositions = fixedPositions,
            previousPlanPositions = plan.positions,
            weightReferenceMarketValues = persistedReferenceMarketValues,
        )
        val currentIds = currentById.keys
        val nextIds = positions.mapTo(linkedSetOf(), ReferencePortfolioPosition::assetId)
        return plan.copy(
            positions = positions,
            addedAssetIds = (nextIds - currentIds).sorted(),
            removedAssetIds = (currentIds - nextIds).sorted(),
            weightReferenceMarketValues = buildMap {
                positions.forEach { position ->
                    put(position.assetId, weightReferenceMarketValues.getValue(position.assetId))
                }
            },
            methodologyPathState = reconstitution.nextPathState,
            selectionAvailabilityDate = selectionAvailabilityDate,
        )
    }

    /**
     * Re-selection can change a pending annual basket after its weighting close. Existing plan
     * lines keep their observed post-reference drift. A replacement absent from the original
     * plan activates at this rebase with a 1.0 pre-activation drift factor because the public
     * methodology does not publish a reserve-security share assignment convention.
     */
    private fun preservePendingPlanDrift(
        fixedPositions: List<ReferencePortfolioPosition>,
        previousPlanPositions: List<ReferencePortfolioPosition>,
        weightReferenceMarketValues: Map<String, Double>,
    ): List<ReferencePortfolioPosition> {
        val previousById = previousPlanPositions.associateBy(ReferencePortfolioPosition::assetId)
        val driftedMarketValues = fixedPositions.associate { position ->
            val fixedValue = weightReferenceMarketValues.getValue(position.assetId)
            val observedValue = previousById[position.assetId]?.referenceFloatMarketValue
                ?: fixedValue
            position.assetId to observedValue
        }
        val rawCurrentWeights = fixedPositions.associate { position ->
            val fixedValue = weightReferenceMarketValues.getValue(position.assetId)
            position.assetId to position.targetWeight *
                (driftedMarketValues.getValue(position.assetId) / fixedValue)
        }
        val currentWeightTotal = rawCurrentWeights.values.sum()
        require(currentWeightTotal.isFinite() && currentWeightTotal > 0.0)
        return fixedPositions.map { position ->
            position.copy(
                currentWeight = rawCurrentWeights.getValue(position.assetId) / currentWeightTotal,
                referenceFloatMarketValue = driftedMarketValues.getValue(position.assetId),
            )
        }.repairCurrentWeightRounding().sortedBy(ReferencePortfolioPosition::assetId)
    }

    private fun rebaseCorporateActionPlan(
        currentPositions: List<ReferencePortfolioPosition>,
        plan: ReferencePortfolioPlan,
        methodology: CompiledEquityMethodology,
        resetExistingSpinOffWeightBasis: Boolean,
    ): ReferencePortfolioPlan? {
        require(
            !resetExistingSpinOffWeightBasis ||
                plan.kind == ReferencePortfolioActionKind.SPIN_OFF_ADDITION,
        )
        val event = requireNotNull(plan.corporateAction)
        val currentIds = currentPositions.mapTo(linkedSetOf(), ReferencePortfolioPosition::assetId)
        val decision: EquityMethodologyCorporateActionDecision?
        val fixedPositions = when (plan.kind) {
            ReferencePortfolioActionKind.CONSTITUENT_MERGER,
            ReferencePortfolioActionKind.SPIN_OFF_ADDITION,
            ReferencePortfolioActionKind.TERMINAL_REMOVAL,
            -> {
                decision = corporateActionDecision(
                    methodology = methodology,
                    event = event,
                    currentIds = currentIds,
                ) ?: return null
                positionsForCorporateAction(
                    currentPositions = currentPositions,
                    event = event,
                    decision = requireNotNull(decision),
                    effectiveDate = plan.effectiveDate,
                    methodology = methodology,
                    weightReferenceMarketValues = plan.weightReferenceMarketValues,
                )
            }
            ReferencePortfolioActionKind.SPIN_OFF_REMOVAL -> {
                decision = null
                val childId = requireNotNull(event.secondaryAssetId)
                if (childId !in currentIds || currentPositions.size <= 1) return null
                currentPositions.filterNot { position -> position.assetId == childId }.normalizeBothWeights()
            }
            else -> error("Only corporate-action plans can be rebased here.")
        }
        val weightReferenceMarketValues = if (decision?.addedAssetIds?.isNotEmpty() == true) {
            val previousValues = if (resetExistingSpinOffWeightBasis) {
                plan.weightReferenceMarketValues?.filterKeys { assetId -> assetId !in currentIds }
            } else {
                plan.weightReferenceMarketValues
            }
            corporateActionWeightReferenceMarketValues(
                positions = fixedPositions,
                previousValues = previousValues,
            )
        } else {
            null
        }
        val positions = if (weightReferenceMarketValues != null) {
            val currentById = currentPositions.associateBy(ReferencePortfolioPosition::assetId)
            val previousPlanById = plan.positions.associateBy(ReferencePortfolioPosition::assetId)
            val observedPositions = fixedPositions.map { position ->
                when {
                    position.assetId !in currentById ->
                        previousPlanById[position.assetId] ?: position
                    event.kind == ReferencePortfolioCorporateActionKind.SPIN_OFF &&
                        position.assetId == event.primaryAssetId -> position
                    else -> currentById.getValue(position.assetId)
                }
            }
            preservePendingPlanDrift(
                fixedPositions = fixedPositions,
                previousPlanPositions = observedPositions,
                weightReferenceMarketValues = weightReferenceMarketValues,
            )
        } else {
            fixedPositions
        }
        val nextIds = positions.mapTo(linkedSetOf(), ReferencePortfolioPosition::assetId)
        return plan.copy(
            positions = positions,
            addedAssetIds = (nextIds - currentIds).sorted(),
            removedAssetIds = (currentIds - nextIds).sorted(),
            weightReferenceMarketValues = weightReferenceMarketValues,
        )
    }

    internal fun advanceHour(
        state: ReferencePortfolioState,
        definition: BenchmarkDefinition,
        referenceDate: LocalDate,
        from: Instant,
        to: Instant,
        referenceTradingFraction: Double,
        macro: MacroEnvironment,
    ): ReferencePortfolioAdvance {
        val methodology = compile(definition)
        val schedule = methodology.schedule
        require(state.benchmarkRef == definition.ref)
        require(from >= state.asOf && to > from)
        require(referenceDate == schedule.marketDate(from)) {
            "The hourly reference date must come from the compiled methodology schedule."
        }
        require(referenceTradingFraction in 0.0..1.0)

        val mayApplyDuePlan = referenceTradingFraction > 0.0 ||
            schedule.isTradingDate(referenceDate) && schedule.intersectsRegularSession(from, to)
        val applied = if (mayApplyDuePlan) {
            applyDuePlans(state, methodology, referenceDate)
        } else {
            state to emptyList()
        }
        val openingState = applied.first
        val records = applied.second

        if (referenceTradingFraction == 0.0) {
            var nextState = openingState.copy(asOf = to)
            if (schedule.isTradingDate(referenceDate) && schedule.reachesRegularClose(from, to)) {
                nextState = schedulePlansAtClose(nextState, methodology, referenceDate)
            }
            return ReferencePortfolioAdvance(
                state = nextState,
                grossReferenceLogReturn = 0.0,
                records = records,
            )
        }

        val snapshots = snapshotMapForKnownDataAt(referenceDate, methodology)
        val trackedAssetIds = buildSet {
            openingState.positions.mapTo(this, ReferencePortfolioPosition::assetId)
            openingState.pendingPlans.forEach { plan ->
                plan.positions.mapTo(this, ReferencePortfolioPosition::assetId)
            }
        }
        val marketReturn = methodologyMarketReturn(methodology, macro)
        val assetReturns = trackedAssetIds.associateWith { assetId ->
            constituentLogReturn(
                snapshot = requireNotNull(snapshots[assetId]) { "기준자산 후보군에 ${assetId}가 없습니다." },
                from = from,
                fraction = referenceTradingFraction,
                marketReturn = marketReturn,
                macro = macro,
            )
        }
        val grossFactor = openingState.positions.sumOf { position ->
            position.currentWeight * exp(assetReturns.getValue(position.assetId))
        }.coerceAtLeast(MIN_PORTFOLIO_FACTOR)
        val grossLogReturn = ln(grossFactor)
            .coerceIn(-MAX_HOURLY_PORTFOLIO_LOG_MOVE, MAX_HOURLY_PORTFOLIO_LOG_MOVE)

        val driftedPositions = advancePositions(openingState.positions, assetReturns)
        val driftedPlans = openingState.pendingPlans.map { plan ->
            plan.copy(positions = advancePositions(plan.positions, assetReturns))
        }
        var nextState = openingState.copy(
            positions = driftedPositions,
            pendingPlans = driftedPlans,
            estimatedAnnualIncomeYield = portfolioIncomeYield(driftedPositions, snapshots),
            asOf = to,
        )
        if (schedule.reachesRegularClose(from, to)) {
            nextState = schedulePlansAtClose(nextState, methodology, referenceDate)
        }
        return ReferencePortfolioAdvance(
            state = nextState,
            grossReferenceLogReturn = grossLogReturn,
            records = records,
        )
    }

    private fun applyDuePlans(
        state: ReferencePortfolioState,
        methodology: CompiledEquityMethodology,
        referenceDate: LocalDate,
    ): Pair<ReferencePortfolioState, List<ReferencePortfolioRecord>> {
        var current = state
        val records = mutableListOf<ReferencePortfolioRecord>()
        var appliedCount = 0
        while (current.pendingPlans.any { it.effectiveDate <= referenceDate }) {
            require(++appliedCount <= ReferencePortfolioState.MAX_PENDING_PLANS)
            val applied = applyOneDuePlan(current, methodology, referenceDate)
            current = applied.first
            records += applied.second
        }
        return current to records
    }

    private fun applyOneDuePlan(
        state: ReferencePortfolioState,
        methodology: CompiledEquityMethodology,
        referenceDate: LocalDate,
    ): Pair<ReferencePortfolioState, ReferencePortfolioRecord> {
        val plan = state.pendingPlans.filter { it.effectiveDate <= referenceDate }.minWith(PLAN_ORDER)
        val effectiveDate = plan.effectiveDate
        val previousPositions = state.positions
        val turnover = oneWayTurnover(previousPositions, plan.positions)
        val revision = state.revision + 1L
        val nextReconstitution = if (plan.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION) {
            nextScheduledReconstitution(
                methodology = methodology,
                afterExclusive = effectiveDate,
            )?.effectiveDate
        } else {
            state.nextReconstitutionDate
        }
        val nextScheduledRebalance = if (
            plan.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION ||
            plan.kind == ReferencePortfolioActionKind.SCHEDULED_REWEIGHT ||
            state.nextRebalanceDate <= effectiveDate
        ) {
            nextScheduledAction(
                methodology = methodology,
                afterExclusive = effectiveDate,
            ).effectiveDate
        } else {
            state.nextRebalanceDate
        }
        val unappliedPlans = state.pendingPlans - plan
        val remainingPlans = reconcilePendingPlans(
            currentPositions = plan.positions,
            currentMethodologyPathState = plan.methodologyPathState,
            plans = unappliedPlans,
            afterEffectiveDate = effectiveDate,
            methodology = methodology,
            selectionInvalidationDate = appliedSelectionInvalidationDate(
                appliedPlan = plan,
                remainingPlans = unappliedPlans,
            ),
            baselineEffectiveDate = effectiveDate,
        )
        val nextState = state.copy(
            positions = plan.positions,
            methodologyPathState = plan.methodologyPathState,
            revision = revision,
            lastReconstitutionDate = if (plan.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION) {
                effectiveDate
            } else {
                state.lastReconstitutionDate
            },
            lastRebalanceDate = effectiveDate,
            nextReconstitutionDate = nextReconstitution,
            nextRebalanceDate = nextScheduledRebalance,
            pendingPlans = remainingPlans,
            lastTurnoverRate = turnover,
            estimatedAnnualIncomeYield = portfolioIncomeYield(
                plan.positions,
                snapshotMapForKnownDataAt(referenceDate, methodology),
            ),
            lastAppliedActionKind = plan.kind,
        )
        val record = ReferencePortfolioRecord(
            id = "reference-rebalance:${state.portfolioId}:$effectiveDate:$revision",
            portfolioId = state.portfolioId,
            benchmarkRef = state.benchmarkRef,
            kind = plan.kind,
            selectionDate = plan.selectionDate,
            weightReferenceDate = plan.weightReferenceDate,
            effectiveDate = effectiveDate,
            addedAssetIds = plan.addedAssetIds,
            removedAssetIds = plan.removedAssetIds,
            beforeCompositionHash = ReferencePortfolioCompositionHasher.hash(previousPositions),
            afterCompositionHash = ReferencePortfolioCompositionHasher.hash(plan.positions),
            turnoverRate = turnover,
            resultingConstituentCount = plan.positions.size,
            revision = revision,
            corporateAction = plan.corporateAction,
        )
        return nextState to record
    }

    private fun schedulePlansAtClose(
        state: ReferencePortfolioState,
        methodology: CompiledEquityMethodology,
        referenceDate: LocalDate,
    ): ReferencePortfolioState {
        val profile = methodology.profile
        val schedule = methodology.schedule
        var plans = state.pendingPlans
        var planAdded = false
        var pendingRebaseDate = referenceDate
        var pendingSelectionInvalidationDate: LocalDate? = null
        var pendingSelectionDate = state.pendingSelectionDate
        var pendingSelectionIncumbentAssetIds = state.pendingSelectionIncumbentAssetIds

        state.nextReconstitutionDate?.let { nextReconstitutionDate ->
            val reconstitutionAction = requireNotNull(
                scheduledActionOn(methodology, nextReconstitutionDate),
            ) { "The next reconstitution state date is not a provider-scheduled action." }
            require(reconstitutionAction.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION)
            if (referenceDate == reconstitutionAction.selectionDate && pendingSelectionDate == null) {
                pendingSelectionDate = referenceDate
                pendingSelectionIncumbentAssetIds =
                    state.positions.map(ReferencePortfolioPosition::assetId).sorted()
            }
            if (
                referenceDate == reconstitutionAction.weightReferenceDate &&
                plans.none {
                    it.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION ||
                        it.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION_TRANSITION
                }
            ) {
                plans = plans + createAnnualPlans(
                    state.copy(
                        pendingPlans = plans,
                        pendingSelectionDate = pendingSelectionDate,
                        pendingSelectionIncumbentAssetIds = pendingSelectionIncumbentAssetIds,
                    ),
                    methodology,
                    reconstitutionAction,
                )
                pendingSelectionDate = null
                pendingSelectionIncumbentAssetIds = null
                planAdded = true
            }
        }

        val nextScheduledAction = requireNotNull(
            scheduledActionOn(methodology, state.nextRebalanceDate),
        ) { "The next scheduled state date is not a provider-scheduled action." }
        if (
            nextScheduledAction.kind == ReferencePortfolioActionKind.SCHEDULED_REWEIGHT &&
            referenceDate == nextScheduledAction.weightReferenceDate &&
            plans.none { it.kind == ReferencePortfolioActionKind.SCHEDULED_REWEIGHT }
        ) {
            plans = plans + createReweightPlan(
                state = state.copy(pendingPlans = plans),
                methodology = methodology,
                kind = ReferencePortfolioActionKind.SCHEDULED_REWEIGHT,
                referenceDate = nextScheduledAction.weightReferenceDate,
                effectiveDate = nextScheduledAction.effectiveDate,
            )
            planAdded = true
        }

        canonicalCorporateActionOn(methodology, referenceDate)?.takeIf { event ->
            event.effectiveDate <= GameCalendar.CAMPAIGN_END_DATE &&
                plans.none { pending -> pending.corporateAction?.eventId == event.eventId }
        }?.let { event ->
            val transitionSteps = corporateActionTransitionSteps(methodology, event)
            val kind = if (transitionSteps.isEmpty()) {
                corporateActionPlanKind(event)
            } else {
                ReferencePortfolioActionKind.CORPORATE_ACTION_TRANSITION
            }
            val firstEffectiveDate = transitionSteps.firstOrNull()?.effectiveDate
                ?: event.effectiveDate
            val prospectivePlanId = referencePortfolioPlanId(
                portfolioId = state.portfolioId,
                kind = kind,
                weightReferenceDate = event.announcementDate,
                effectiveDate = firstEffectiveDate,
                corporateAction = event,
            )
            val projectedPositions = projectedPositionsBefore(
                currentPositions = state.positions,
                plans = plans,
                effectiveDate = firstEffectiveDate,
                kind = kind,
                planId = prospectivePlanId,
            )
            val invalidatesPendingSelection = invalidatesPendingScheduledSelection(plans, event)
            createCorporateActionPlans(
                state = state.copy(pendingPlans = plans),
                methodology = methodology,
                event = event,
                baselinePositions = projectedPositions,
            ).takeIf { corporatePlans -> corporatePlans.isNotEmpty() }?.let { corporatePlans ->
                plans = plans + corporatePlans
                planAdded = true
            }
            if (invalidatesPendingSelection) {
                planAdded = true
                if (event.effectiveDate > pendingRebaseDate) pendingRebaseDate = event.effectiveDate
                pendingSelectionInvalidationDate = event.effectiveDate
            }
        }

        val isExtraordinaryReviewDate = nextExtraordinaryReviewDate(
            methodology = methodology,
            afterExclusive = referenceDate.minus(1, DateTimeUnit.DAY),
        ) == referenceDate
        if (isExtraordinaryReviewDate &&
            plans.none { it.kind == ReferencePortfolioActionKind.EXTRAORDINARY_REMOVAL }
        ) {
            createExtraordinaryRemovalPlan(
                state = state.copy(pendingPlans = plans),
                methodology = methodology,
                referenceDate = referenceDate,
            )?.takeIf { deletionPlan -> deletionPlan.effectiveDate <= GameCalendar.CAMPAIGN_END_DATE }
                ?.let { deletionPlan ->
                    // A deletion changes the constituent set. Any still-pending daily cap whose
                    // effective date is not earlier was calculated for the obsolete basket and is
                    // superseded. This also prevents two plans becoming due on the same next-month
                    // opening after a penultimate-day T+2 breach.
                    plans = plans.filterNot { pending ->
                        pending.kind == ReferencePortfolioActionKind.CONSTRAINT_REWEIGHT &&
                            pending.effectiveDate >= deletionPlan.effectiveDate
                    } + deletionPlan
                    planAdded = true
                }
        }

        if (
            plans.none { it.kind == ReferencePortfolioActionKind.CONSTRAINT_REWEIGHT } &&
            plans.none { it.kind == ReferencePortfolioActionKind.EXTRAORDINARY_REMOVAL }
        ) {
            val effectiveDate = methodology.policy.constraintReweightEffectiveDate(
                EquityMethodologyConstraintInput(
                    profile = profile,
                    observationDate = referenceDate,
                    currentWeights = state.positions.associate { it.assetId to it.currentWeight },
                ),
            )
            if (effectiveDate != null && effectiveDate <= GameCalendar.CAMPAIGN_END_DATE) {
                require(effectiveDate > referenceDate && schedule.isTradingDate(effectiveDate)) {
                    "The methodology returned an invalid constraint-reweight effective date."
                }
                plans = plans + createReweightPlan(
                    state = state.copy(pendingPlans = plans),
                    methodology = methodology,
                    kind = ReferencePortfolioActionKind.CONSTRAINT_REWEIGHT,
                    referenceDate = referenceDate,
                    effectiveDate = effectiveDate,
                )
                planAdded = true
            }
        }

        if (planAdded) {
            plans = reconcilePendingPlans(
                currentPositions = state.positions,
                currentMethodologyPathState = state.methodologyPathState,
                plans = plans,
                afterEffectiveDate = state.lastRebalanceDate,
                methodology = methodology,
                rebaseAsOfDate = pendingRebaseDate,
                selectionInvalidationDate = pendingSelectionInvalidationDate,
            )
        }
        plans = resetNextStagedReconstitutionAtClose(
            plans = plans,
            methodology = methodology,
            referenceDate = referenceDate,
        )
        plans = resetNextStagedCorporateActionAtClose(
            plans = plans,
            methodology = methodology,
            referenceDate = referenceDate,
        )
        return state.copy(
            pendingPlans = plans.sortedWith(PLAN_ORDER),
            pendingSelectionDate = pendingSelectionDate,
            pendingSelectionIncumbentAssetIds = pendingSelectionIncumbentAssetIds,
        )
    }

    /**
     * KRX's three-session transition resets each step from the preceding close. Pending plans
     * otherwise keep drifting from the original review close and miss the published 30/70/100
     * completion points at the following opens.
     */
    private fun resetNextStagedReconstitutionAtClose(
        plans: List<ReferencePortfolioPlan>,
        methodology: CompiledEquityMethodology,
        referenceDate: LocalDate,
    ): List<ReferencePortfolioPlan> {
        val finalPlan = plans.singleOrNull { plan ->
            plan.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION
        } ?: return plans
        val finalAction = scheduledActionOn(methodology, finalPlan.effectiveDate) ?: return plans
        val transitionSteps = scheduledReconstitutionTransitionSteps(methodology, finalAction)
        if (transitionSteps.isEmpty()) return plans
        val nextTradingDate = methodology.schedule.addTradingDays(referenceDate, 1)
        val stagedDates = transitionSteps.mapTo(linkedSetOf()) { step -> step.effectiveDate }
            .apply { add(finalAction.effectiveDate) }
        if (nextTradingDate !in stagedDates) return plans
        return plans.map { plan ->
            if (plan.effectiveDate != nextTradingDate ||
                plan.selectionDate != finalAction.selectionDate ||
                plan.weightReferenceDate != finalAction.weightReferenceDate ||
                plan.kind != ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION_TRANSITION &&
                plan.kind != ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION
            ) {
                plan
            } else {
                plan.copy(
                    positions = plan.positions.map { position ->
                        position.copy(currentWeight = position.targetWeight)
                    },
                )
            }
        }
    }

    /** Resets a provider-defined corporate replacement step from the preceding session close. */
    private fun resetNextStagedCorporateActionAtClose(
        plans: List<ReferencePortfolioPlan>,
        methodology: CompiledEquityMethodology,
        referenceDate: LocalDate,
    ): List<ReferencePortfolioPlan> {
        val nextTradingDate = methodology.schedule.addTradingDays(referenceDate, 1)
        val stagedEventIds = plans.asSequence()
            .mapNotNull(ReferencePortfolioPlan::corporateAction)
            .distinctBy(ReferencePortfolioCorporateAction::eventId)
            .filter { event ->
                corporateActionTransitionSteps(methodology, event).any { step ->
                    step.effectiveDate == nextTradingDate
                }
            }.map(ReferencePortfolioCorporateAction::eventId)
            .toSet()
        if (stagedEventIds.isEmpty()) return plans
        return plans.map { plan ->
            if (plan.effectiveDate != nextTradingDate ||
                plan.corporateAction?.eventId !in stagedEventIds
            ) {
                plan
            } else {
                plan.copy(
                    positions = plan.positions.map { position ->
                        position.copy(currentWeight = position.targetWeight)
                    },
                )
            }
        }
    }

    private fun createAnnualPlans(
        state: ReferencePortfolioState,
        methodology: CompiledEquityMethodology,
        action: EquityMethodologyScheduledAction,
    ): List<ReferencePortfolioPlan> {
        require(action.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION)
        val selectionDate = action.selectionDate
        val weightReferenceDate = action.weightReferenceDate
        val effectiveDate = action.effectiveDate
        require(state.pendingSelectionDate == selectionDate) {
            "The annual plan is missing its selection-close incumbent snapshot."
        }
        val selectionIncumbentAssetIds = requireNotNull(state.pendingSelectionIncumbentAssetIds)
        val reconstitution = selectConstituents(
            methodology = methodology,
            action = action,
            incumbentAssetIds = selectionIncumbentAssetIds.toSet(),
            previousPathState = state.methodologyPathState,
            unavailableOnDate = weightReferenceDate,
        )
        val currentById = state.positions.associateBy(ReferencePortfolioPosition::assetId)
        val rawWeightReferenceMarketValues = if (
            methodology.policy.usesSelectionSnapshotMarketValuesForScheduledReconstitution
        ) {
            deterministicSelectionSnapshotMarketValues(
                methodology = methodology,
                action = action,
                selected = reconstitution.candidates,
            )
        } else {
            reconstitution.candidates.associate { candidate ->
                val assetId = candidate.snapshot.definition.assetId
                assetId to (
                    currentById[assetId]?.referenceFloatMarketValue
                        ?: simulatedReferenceMarketValueBetween(
                            snapshot = candidate.snapshot,
                            methodology = methodology,
                            fromDate = selectionDate,
                            throughDate = weightReferenceDate,
                        )
                    )
            }
        }
        val multipliedWeightReferenceMarketValues = applyReconstitutionMarketValueMultipliers(
            reconstitution = reconstitution,
            rawFloatMarketValues = rawWeightReferenceMarketValues,
        )
        val weightReferenceMarketValues = referenceMarketValuesForWeighting(
            methodology = methodology,
            selected = reconstitution.candidates,
            actionKind = action.kind,
            observationDate = weightReferenceDate,
            effectiveDate = effectiveDate,
            rawFloatMarketValues = multipliedWeightReferenceMarketValues,
        )
        val persistedReferenceMarketValues = removeReconstitutionMarketValueMultipliers(
            reconstitution = reconstitution,
            multipliedFloatMarketValues = weightReferenceMarketValues,
        )
        val positions = positionsForSelection(
            selected = reconstitution.candidates,
            rawFloatMarketValues = weightReferenceMarketValues,
            persistedReferenceMarketValues = persistedReferenceMarketValues,
            methodology = methodology,
            actionKind = action.kind,
            observationDate = weightReferenceDate,
            effectiveDate = effectiveDate,
            previousPositions = currentById,
        )
        val finalPlan = newPlan(
            state = state,
            kind = ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION,
            selectionDate = selectionDate,
            weightReferenceDate = weightReferenceDate,
            effectiveDate = effectiveDate,
            positions = positions,
            weightReferenceMarketValues = buildMap {
                positions.forEach { position ->
                    put(position.assetId, weightReferenceMarketValues.getValue(position.assetId))
                }
            },
            selectionIncumbentAssetIds = selectionIncumbentAssetIds,
            selectionAvailabilityDate = weightReferenceDate,
            methodologyPathState = reconstitution.nextPathState,
        )
        val transitionSteps = scheduledReconstitutionTransitionSteps(methodology, action)
        if (transitionSteps.isEmpty()) return listOf(finalPlan)

        val initialPositions = projectedPositionsBefore(
            currentPositions = state.positions,
            plans = state.pendingPlans,
            effectiveDate = transitionSteps.first().effectiveDate,
            kind = ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION_TRANSITION,
            planId = referencePortfolioPlanId(
                portfolioId = state.portfolioId,
                kind = ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION_TRANSITION,
                weightReferenceDate = weightReferenceDate,
                effectiveDate = transitionSteps.first().effectiveDate,
                corporateAction = null,
            ),
        )
        var baselinePositions = initialPositions
        val transitionPlans = transitionSteps.map { step ->
            val blendedPositions = blendReconstitutionTransitionPositions(
                initialPositions = initialPositions,
                finalPositions = finalPlan.positions,
                previousTransitionPositions = baselinePositions,
                completionFraction = step.completionFraction,
                effectiveDate = step.effectiveDate,
            )
            newPlan(
                state = state,
                kind = ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION_TRANSITION,
                selectionDate = selectionDate,
                weightReferenceDate = weightReferenceDate,
                effectiveDate = step.effectiveDate,
                positions = blendedPositions,
                baselinePositions = baselinePositions,
                transitionBaselineWeights = baselinePositions.associate { position ->
                    position.assetId to position.currentWeight
                },
            ).also { plan -> baselinePositions = plan.positions }
        }
        val transitionBaselineById = baselinePositions.associateBy(ReferencePortfolioPosition::assetId)
        val completedPositions = finalPlan.positions.map { position ->
            transitionBaselineById[position.assetId]?.let { transitionPosition ->
                position.copy(enteredOn = transitionPosition.enteredOn)
            } ?: position
        }
        val completedPlan = newPlan(
            state = state,
            kind = ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION,
            selectionDate = selectionDate,
            weightReferenceDate = weightReferenceDate,
            effectiveDate = effectiveDate,
            positions = completedPositions,
            weightReferenceMarketValues = finalPlan.weightReferenceMarketValues,
            selectionIncumbentAssetIds = selectionIncumbentAssetIds,
            selectionAvailabilityDate = weightReferenceDate,
            baselinePositions = baselinePositions,
            methodologyPathState = reconstitution.nextPathState,
        )
        return transitionPlans + completedPlan
    }

    private fun blendReconstitutionTransitionPositions(
        initialPositions: List<ReferencePortfolioPosition>,
        finalPositions: List<ReferencePortfolioPosition>,
        previousTransitionPositions: List<ReferencePortfolioPosition>,
        completionFraction: Double,
        effectiveDate: LocalDate,
    ): List<ReferencePortfolioPosition> {
        require(completionFraction in 0.0..1.0 && completionFraction != 0.0 && completionFraction != 1.0)
        val initialById = initialPositions.associateBy(ReferencePortfolioPosition::assetId)
        val finalById = finalPositions.associateBy(ReferencePortfolioPosition::assetId)
        val previousById = previousTransitionPositions.associateBy(ReferencePortfolioPosition::assetId)
        val finalMaximumRank = finalPositions.maxOf(ReferencePortfolioPosition::selectionRank)
        val outgoingRanks = (initialById.keys - finalById.keys).sorted()
            .withIndex().associate { (index, assetId) -> assetId to finalMaximumRank + index + 1 }
        return (initialById.keys + finalById.keys).sorted().map { assetId ->
            val initial = initialById[assetId]
            val final = finalById[assetId]
            val blendedWeight =
                (initial?.currentWeight ?: 0.0) * (1.0 - completionFraction) +
                    (final?.targetWeight ?: 0.0) * completionFraction
            require(blendedWeight > 0.0)
            ReferencePortfolioPosition(
                assetId = assetId,
                currentWeight = blendedWeight,
                targetWeight = blendedWeight,
                referenceFloatMarketValue = previousById[assetId]?.referenceFloatMarketValue
                    ?: initial?.referenceFloatMarketValue
                    ?: requireNotNull(final).referenceFloatMarketValue,
                enteredOn = previousById[assetId]?.enteredOn ?: initial?.enteredOn ?: effectiveDate,
                selectionRank = final?.selectionRank ?: outgoingRanks.getValue(assetId),
            )
        }.repairBothWeightRounding()
    }

    private fun createReweightPlan(
        state: ReferencePortfolioState,
        methodology: CompiledEquityMethodology,
        kind: ReferencePortfolioActionKind,
        referenceDate: LocalDate,
        effectiveDate: LocalDate,
    ): ReferencePortfolioPlan {
        require(kind == ReferencePortfolioActionKind.SCHEDULED_REWEIGHT || kind == ReferencePortfolioActionKind.CONSTRAINT_REWEIGHT)
        val planId = referencePortfolioPlanId(
            portfolioId = state.portfolioId,
            kind = kind,
            weightReferenceDate = referenceDate,
            effectiveDate = effectiveDate,
            corporateAction = null,
        )
        val baselinePositions = projectedPositionsBefore(
            currentPositions = state.positions,
            plans = state.pendingPlans,
            effectiveDate = effectiveDate,
            kind = kind,
            planId = planId,
        )
        val snapshots = snapshotMapForKnownDataAt(referenceDate, methodology)
        val selected = baselinePositions.map { position ->
            RankedReferenceCandidate(
                snapshot = requireNotNull(snapshots[position.assetId]),
                compositeRank = position.selectionRank,
            )
        }
        val persistedReferenceMarketValues = baselinePositions.associate {
            it.assetId to it.referenceFloatMarketValue
        }
        val weightReferenceMarketValues = if (
            kind == ReferencePortfolioActionKind.CONSTRAINT_REWEIGHT
        ) {
            // RIC review starts from the live ideal portfolio weights. They are the immutable
            // solver input, while the position ledger keeps carrying the underlying raw FMC.
            baselinePositions.associate { position ->
                position.assetId to position.currentWeight
            }
        } else {
            referenceMarketValuesForWeighting(
                methodology = methodology,
                selected = selected,
                actionKind = kind,
                observationDate = referenceDate,
                effectiveDate = effectiveDate,
                rawFloatMarketValues = persistedReferenceMarketValues,
            )
        }
        val positions = positionsForSelection(
            selected = selected,
            rawFloatMarketValues = weightReferenceMarketValues,
            persistedReferenceMarketValues = if (
                kind == ReferencePortfolioActionKind.CONSTRAINT_REWEIGHT
            ) {
                persistedReferenceMarketValues
            } else {
                weightReferenceMarketValues
            },
            methodology = methodology,
            actionKind = kind,
            observationDate = referenceDate,
            effectiveDate = effectiveDate,
            previousPositions = baselinePositions.associateBy(ReferencePortfolioPosition::assetId),
        )
        return newPlan(
            state = state,
            kind = kind,
            selectionDate = referenceDate,
            weightReferenceDate = referenceDate,
            effectiveDate = effectiveDate,
            positions = positions,
            weightReferenceMarketValues = weightReferenceMarketValues,
            baselinePositions = baselinePositions,
        )
    }

    private fun createExtraordinaryRemovalPlan(
        state: ReferencePortfolioState,
        methodology: CompiledEquityMethodology,
        referenceDate: LocalDate,
    ): ReferencePortfolioPlan? {
        val decision = extraordinaryRemovalDecision(
            methodology = methodology,
            assetIds = state.positions.mapTo(linkedSetOf(), ReferencePortfolioPosition::assetId),
            observationDate = referenceDate,
        ) ?: return null
        val requestedRemovalIds = decision.second
        val removedIds = state.positions.asSequence()
            .map(ReferencePortfolioPosition::assetId)
            .filter(requestedRemovalIds::contains)
            .take((state.positions.size - methodology.constraints.minimumConstituentCount).coerceAtLeast(0))
            .toSortedSet()
        if (removedIds.isEmpty()) return null
        val positions = state.positions.filterNot { it.assetId in removedIds }
            .normalizeBothWeights()
        return newPlan(
            state = state,
            kind = ReferencePortfolioActionKind.EXTRAORDINARY_REMOVAL,
            selectionDate = referenceDate,
            weightReferenceDate = referenceDate,
            effectiveDate = decision.first,
            positions = positions,
        )
    }

    private fun createCorporateActionPlans(
        state: ReferencePortfolioState,
        methodology: CompiledEquityMethodology,
        event: ReferencePortfolioCorporateAction,
        baselinePositions: List<ReferencePortfolioPosition> = state.positions,
    ): List<ReferencePortfolioPlan> {
        val currentIds = baselinePositions.mapTo(linkedSetOf(), ReferencePortfolioPosition::assetId)
        val decision = corporateActionDecision(
            methodology = methodology,
            event = event,
            currentIds = currentIds,
        ) ?: return emptyList()
        val finalPositions = positionsForCorporateAction(
            currentPositions = baselinePositions,
            event = event,
            decision = decision,
            effectiveDate = event.effectiveDate,
            methodology = methodology,
        )
        val kind = corporateActionPlanKind(event)
        val weightReferenceMarketValues = if (decision.addedAssetIds.isNotEmpty()) {
            corporateActionWeightReferenceMarketValues(finalPositions)
        } else {
            null
        }
        val transitionSteps = corporateActionTransitionSteps(methodology, event)
        if (transitionSteps.isNotEmpty()) {
            require(event.kind != ReferencePortfolioCorporateActionKind.SPIN_OFF)
            require(decision.removedAssetIds == setOf(event.primaryAssetId))
            require(decision.addedAssetIds.size == 1)
            requireNotNull(weightReferenceMarketValues)
            var transitionBaseline = baselinePositions
            val transitionPlans = transitionSteps.dropLast(1).map { step ->
                val blendedPositions = blendReconstitutionTransitionPositions(
                    initialPositions = baselinePositions,
                    finalPositions = finalPositions,
                    previousTransitionPositions = transitionBaseline,
                    completionFraction = step.completionFraction,
                    effectiveDate = step.effectiveDate,
                )
                newPlan(
                    state = state,
                    kind = ReferencePortfolioActionKind.CORPORATE_ACTION_TRANSITION,
                    selectionDate = event.announcementDate,
                    weightReferenceDate = event.announcementDate,
                    effectiveDate = step.effectiveDate,
                    positions = blendedPositions,
                    transitionBaselineWeights = transitionBaseline.associate { position ->
                        position.assetId to position.currentWeight
                    },
                    corporateAction = event,
                    baselinePositions = transitionBaseline,
                ).also { plan -> transitionBaseline = plan.positions }
            }
            val transitionBaselineById = transitionBaseline.associateBy(
                ReferencePortfolioPosition::assetId,
            )
            val completedPositions = finalPositions.map { position ->
                transitionBaselineById[position.assetId]?.let { transitionPosition ->
                    position.copy(enteredOn = transitionPosition.enteredOn)
                } ?: position
            }
            val completedPlan = newPlan(
                state = state,
                kind = kind,
                selectionDate = event.announcementDate,
                weightReferenceDate = event.announcementDate,
                effectiveDate = transitionSteps.last().effectiveDate,
                positions = completedPositions,
                weightReferenceMarketValues = weightReferenceMarketValues,
                corporateAction = event,
                baselinePositions = transitionBaseline,
            )
            return transitionPlans + completedPlan
        }
        val primaryPlan = newPlan(
            state = state,
            kind = kind,
            selectionDate = event.announcementDate,
            weightReferenceDate = event.announcementDate,
            effectiveDate = event.effectiveDate,
            positions = finalPositions,
            weightReferenceMarketValues = weightReferenceMarketValues,
            corporateAction = event,
            baselinePositions = baselinePositions,
        )
        if (event.kind != ReferencePortfolioCorporateActionKind.SPIN_OFF) {
            return listOf(primaryPlan)
        }
        val childId = requireNotNull(event.secondaryAssetId)
        val removalPositions = primaryPlan.positions
            .filterNot { position -> position.assetId == childId }
            .normalizeBothWeights()
        val removalPlan = newPlan(
            state = state,
            kind = ReferencePortfolioActionKind.SPIN_OFF_REMOVAL,
            selectionDate = event.announcementDate,
            weightReferenceDate = event.announcementDate,
            effectiveDate = requireNotNull(event.followUpEffectiveDate),
            positions = removalPositions,
            corporateAction = event,
            baselinePositions = primaryPlan.positions,
        )
        return listOf(primaryPlan, removalPlan)
    }

    private fun corporateActionDecision(
        methodology: CompiledEquityMethodology,
        event: ReferencePortfolioCorporateAction,
        currentIds: Set<String>,
    ): EquityMethodologyCorporateActionDecision? {
        if (currentIds.isEmpty() || currentIds.size > ReferencePortfolioLimits.MAX_CONSTITUENTS) return null
        if (currentIds.any { assetId -> !hasCanonicalReferenceAssetId(assetId) }) return null
        if (event.primaryAssetId !in currentIds) return null
        if (event.primaryAssetId in spinOffChildAssetIds) return null
        val secondaryId = event.secondaryAssetId
        if (event.kind == ReferencePortfolioCorporateActionKind.SPIN_OFF && secondaryId in currentIds) {
            return null
        }
        if (event.kind == ReferencePortfolioCorporateActionKind.SPIN_OFF &&
            currentIds.size >= methodology.constraints.maximumConstituentCount
        ) return null
        if (secondaryId != null && !hasCanonicalReferenceAssetId(secondaryId)) return null
        val snapshots = snapshotMapForKnownDataAt(event.announcementDate, methodology)
        val unavailableIds = unavailableScheduledAssetIdsAt(methodology, event.announcementDate)
        val universeIds = snapshots.keys.filterTo(linkedSetOf()) { assetId ->
            assetId !in unavailableIds &&
                referenceEquityById.getValue(assetId).referenceUniverse ==
                methodology.profile.referenceUniverse
        }
        secondaryId?.let(universeIds::add)
        currentIds.forEach(universeIds::add)
        val decision = methodology.policy.corporateActionDecision(
            EquityMethodologyCorporateActionInput(
                profile = methodology.profile,
                event = event,
                currentConstituents = currentIds.sorted().map { assetId ->
                    snapshots.getValue(assetId).toMethodologyCandidate(methodology, event.announcementDate)
                },
                universeCandidates = universeIds.sorted().map { assetId ->
                    snapshots.getValue(assetId).toMethodologyCandidate(methodology, event.announcementDate)
                },
            ),
        ) ?: return null
        requireCanonicalCorporateActionDecision(event, currentIds, decision, methodology)
        return decision
    }

    private fun requireCanonicalCorporateActionDecision(
        event: ReferencePortfolioCorporateAction,
        currentIds: Set<String>,
        decision: EquityMethodologyCorporateActionDecision,
        methodology: CompiledEquityMethodology,
    ) {
        require(decision.removedAssetIds.all(currentIds::contains))
        require(decision.addedAssetIds.none(currentIds::contains))
        require((decision.removedAssetIds + decision.addedAssetIds).all(referenceEquityById::containsKey))
        require(currentIds.size - decision.removedAssetIds.size + decision.addedAssetIds.size in
            methodology.constraints.minimumConstituentCount..methodology.constraints.maximumConstituentCount)
        when (event.kind) {
            ReferencePortfolioCorporateActionKind.MERGER -> {
                require(decision.removedAssetIds.contains(event.primaryAssetId))
                require(decision.followUpRemovalDate == null)
                require(decision.survivingAcquirerAssetId == null ||
                    decision.survivingAcquirerAssetId == event.secondaryAssetId &&
                    decision.survivingAcquirerAssetId in currentIds)
                require(decision.transferredValueFraction <= event.valueTransferFraction)
            }
            ReferencePortfolioCorporateActionKind.SPIN_OFF -> {
                require(decision.removedAssetIds.isEmpty())
                require(decision.addedAssetIds == setOf(event.secondaryAssetId))
                require(decision.survivingAcquirerAssetId == null)
                require(decision.transferredValueFraction == event.valueTransferFraction)
                require(decision.followUpRemovalDate == event.followUpEffectiveDate)
            }
            ReferencePortfolioCorporateActionKind.TERMINAL_REMOVAL -> {
                require(decision.removedAssetIds == setOf(event.primaryAssetId))
                require(decision.survivingAcquirerAssetId == null)
                require(decision.transferredValueFraction == 0.0)
                require(decision.followUpRemovalDate == null)
            }
        }
    }

    private fun positionsForCorporateAction(
        currentPositions: List<ReferencePortfolioPosition>,
        event: ReferencePortfolioCorporateAction,
        decision: EquityMethodologyCorporateActionDecision,
        effectiveDate: LocalDate,
        methodology: CompiledEquityMethodology,
        weightReferenceMarketValues: Map<String, Double>? = null,
    ): List<ReferencePortfolioPosition> {
        val positions = currentPositions.associateByTo(linkedMapOf(), ReferencePortfolioPosition::assetId)
        when (event.kind) {
            ReferencePortfolioCorporateActionKind.MERGER -> {
                val target = positions.getValue(event.primaryAssetId)
                decision.survivingAcquirerAssetId?.takeIf { decision.transferredValueFraction > 0.0 }
                    ?.let { acquirerId ->
                        val acquirer = positions.getValue(acquirerId)
                        val fraction = decision.transferredValueFraction
                        positions[acquirerId] = acquirer.copy(
                            currentWeight = acquirer.currentWeight + target.currentWeight * fraction,
                            targetWeight = acquirer.targetWeight + target.targetWeight * fraction,
                            referenceFloatMarketValue = (
                                acquirer.referenceFloatMarketValue +
                                    target.referenceFloatMarketValue * fraction
                                ).coerceAtMost(MAX_REFERENCE_MARKET_CAP),
                        )
                    }
                decision.removedAssetIds.forEach(positions::remove)
            }
            ReferencePortfolioCorporateActionKind.SPIN_OFF -> {
                val parent = positions.getValue(event.primaryAssetId)
                val childId = requireNotNull(event.secondaryAssetId)
                val fraction = decision.transferredValueFraction
                positions[event.primaryAssetId] = parent.copy(
                    currentWeight = parent.currentWeight * (1.0 - fraction),
                    targetWeight = parent.targetWeight * (1.0 - fraction),
                    referenceFloatMarketValue = (parent.referenceFloatMarketValue * (1.0 - fraction))
                        .coerceAtLeast(MIN_REFERENCE_MARKET_CAP),
                )
                positions[childId] = ReferencePortfolioPosition(
                    assetId = childId,
                    currentWeight = parent.currentWeight * fraction,
                    targetWeight = parent.targetWeight * fraction,
                    referenceFloatMarketValue = (parent.referenceFloatMarketValue * fraction)
                        .coerceAtLeast(MIN_REFERENCE_MARKET_CAP),
                    enteredOn = effectiveDate,
                    selectionRank = currentPositions.maxOf(ReferencePortfolioPosition::selectionRank) + 1,
                )
            }
            ReferencePortfolioCorporateActionKind.TERMINAL_REMOVAL ->
                decision.removedAssetIds.forEach(positions::remove)
        }
        if (event.kind != ReferencePortfolioCorporateActionKind.SPIN_OFF &&
            decision.addedAssetIds.isNotEmpty()
        ) {
            val snapshots = snapshotMapForKnownDataAt(event.announcementDate, methodology)
            var nextRank = currentPositions.maxOf(ReferencePortfolioPosition::selectionRank) + 1
            decision.addedAssetIds.sorted().forEach { assetId ->
                val snapshot = requireNotNull(snapshots[assetId]) {
                    "A corporate-action replacement must exist in the canonical reference universe."
                }
                val referenceValue = untrackedReferenceMarketValueAt(
                    methodology = methodology,
                    snapshot = snapshot,
                    date = event.announcementDate,
                )
                positions[assetId] = ReferencePortfolioPosition(
                    assetId = assetId,
                    currentWeight = 0.0,
                    targetWeight = 0.0,
                    referenceFloatMarketValue = referenceValue,
                    enteredOn = effectiveDate,
                    selectionRank = nextRank++,
                )
            }
            val announcementMarketValues = corporateActionTargetMarketValues(
                positions = positions.values.toList(),
                previousValues = weightReferenceMarketValues,
            )
            return cappedPositionsForExistingBasket(
                positions = positions.values.toList(),
                methodology = methodology,
                actionKind = corporateActionPlanKind(event),
                referenceDate = event.announcementDate,
                effectiveDate = effectiveDate,
                rawFloatMarketValues = announcementMarketValues,
            )
        }
        require(positions.isNotEmpty())
        return positions.values.toList().normalizeBothWeights()
    }

    /** Live FMC inputs used to calculate a replacement target before its weights exist. */
    private fun corporateActionTargetMarketValues(
        positions: List<ReferencePortfolioPosition>,
        previousValues: Map<String, Double>? = null,
    ): Map<String, Double> = buildMap {
        positions.sortedBy(ReferencePortfolioPosition::assetId).forEach { position ->
            val value = previousValues?.get(position.assetId) ?: position.referenceFloatMarketValue
            require(value.isFinite() && value > 0.0) {
                "A corporate-action target market value must be finite and positive."
            }
            put(position.assetId, value)
        }
    }

    /**
     * Keeps an immutable announcement-close basis while allowing a rebased plan to add a newly
     * canonical line. The target/current ratio encodes drift that already existed at announcement;
     * later [preservePendingPlanDrift] can therefore reproduce both that drift and subsequent FMC
     * moves. Replacement plans begin with target == current, so their basis is the observed FMC.
     */
    private fun corporateActionWeightReferenceMarketValues(
        positions: List<ReferencePortfolioPosition>,
        previousValues: Map<String, Double>? = null,
    ): Map<String, Double> = buildMap {
        positions.sortedBy(ReferencePortfolioPosition::assetId).forEach { position ->
            val value = previousValues?.get(position.assetId) ?: run {
                require(position.currentWeight > 0.0 && position.targetWeight > 0.0) {
                    "A corporate-action addition requires positive current and target weights."
                }
                position.referenceFloatMarketValue * position.targetWeight / position.currentWeight
            }
            require(value.isFinite() && value > 0.0) {
                "A corporate-action weighting basis must be finite and positive."
            }
            put(
                position.assetId,
                value,
            )
        }
    }

    /**
     * A reserve candidate is not advanced in the live portfolio before entry. Give it a canonical
     * announcement-date FMC by advancing its latest point-in-time snapshot along the deterministic
     * reference path; incumbents keep the actual live FMC already carried by their positions.
     */
    private fun untrackedReferenceMarketValueAt(
        methodology: CompiledEquityMethodology,
        snapshot: SimulatedReferenceEquitySnapshot,
        date: LocalDate,
    ): Double {
        val selectedAction = latestReconstitutionSelectedInYear(methodology, date)
        val snapshotDate = if (
            selectedAction != null && snapshot.definition.assetId in ordinaryReferenceEquityIds
        ) {
            selectedAction.selectionDate
        } else {
            LocalDate((date.year - 1).coerceAtLeast(REFERENCE_BASE_YEAR), 12, 31)
        }
        return simulatedReferenceMarketValueBetween(
            methodology = methodology,
            snapshot = snapshot,
            fromDate = snapshotDate,
            throughDate = date,
        )
    }

    private fun newPlan(
        state: ReferencePortfolioState,
        kind: ReferencePortfolioActionKind,
        selectionDate: LocalDate,
        weightReferenceDate: LocalDate,
        effectiveDate: LocalDate,
        positions: List<ReferencePortfolioPosition>,
        weightReferenceMarketValues: Map<String, Double>? = null,
        transitionBaselineWeights: Map<String, Double>? = null,
        selectionIncumbentAssetIds: List<String>? = null,
        selectionAvailabilityDate: LocalDate? = null,
        corporateAction: ReferencePortfolioCorporateAction? = null,
        baselinePositions: List<ReferencePortfolioPosition> = state.positions,
        methodologyPathState: EquityMethodologyPathState = state.methodologyPathState,
    ): ReferencePortfolioPlan {
        val previousIds = baselinePositions.mapTo(linkedSetOf(), ReferencePortfolioPosition::assetId)
        val nextIds = positions.mapTo(linkedSetOf(), ReferencePortfolioPosition::assetId)
        return ReferencePortfolioPlan(
            id = referencePortfolioPlanId(
                portfolioId = state.portfolioId,
                kind = kind,
                weightReferenceDate = weightReferenceDate,
                effectiveDate = effectiveDate,
                corporateAction = corporateAction,
            ),
            portfolioId = state.portfolioId,
            benchmarkRef = state.benchmarkRef,
            kind = kind,
            selectionDate = selectionDate,
            weightReferenceDate = weightReferenceDate,
            effectiveDate = effectiveDate,
            positions = positions,
            methodologyPathState = methodologyPathState,
            addedAssetIds = (nextIds - previousIds).sorted(),
            removedAssetIds = (previousIds - nextIds).sorted(),
            transitionBaselineWeights = transitionBaselineWeights,
            weightReferenceMarketValues = weightReferenceMarketValues,
            selectionIncumbentAssetIds = selectionIncumbentAssetIds,
            selectionAvailabilityDate = selectionAvailabilityDate,
            corporateAction = corporateAction,
        )
    }

    private fun referencePortfolioPlanId(
        portfolioId: String,
        kind: ReferencePortfolioActionKind,
        weightReferenceDate: LocalDate,
        effectiveDate: LocalDate,
        corporateAction: ReferencePortfolioCorporateAction?,
    ): String = if (corporateAction == null) {
        "reference-plan:$portfolioId:${kind.name}:$weightReferenceDate:$effectiveDate"
    } else {
        "reference-plan:$portfolioId:${kind.name}:${corporateAction.eventId}:" +
            "$weightReferenceDate:$effectiveDate"
    }

    private fun corporateActionPlanKind(
        event: ReferencePortfolioCorporateAction,
    ): ReferencePortfolioActionKind = when (event.kind) {
        ReferencePortfolioCorporateActionKind.MERGER -> ReferencePortfolioActionKind.CONSTITUENT_MERGER
        ReferencePortfolioCorporateActionKind.SPIN_OFF -> ReferencePortfolioActionKind.SPIN_OFF_ADDITION
        ReferencePortfolioCorporateActionKind.TERMINAL_REMOVAL -> ReferencePortfolioActionKind.TERMINAL_REMOVAL
    }

    private fun projectedPositionsBefore(
        currentPositions: List<ReferencePortfolioPosition>,
        plans: List<ReferencePortfolioPlan>,
        effectiveDate: LocalDate,
        kind: ReferencePortfolioActionKind,
        planId: String,
    ): List<ReferencePortfolioPosition> {
        var projected = currentPositions
        plans.sortedWith(PLAN_ORDER).forEach { pending ->
            val precedes = pending.effectiveDate < effectiveDate ||
                pending.effectiveDate == effectiveDate && (
                    pending.kind.executionPriority() < kind.executionPriority() ||
                        pending.kind.executionPriority() == kind.executionPriority() && pending.id < planId
                    )
            if (!precedes) return projected
            projected = pending.positions
        }
        return projected
    }

    private fun selectConstituents(
        methodology: CompiledEquityMethodology,
        action: EquityMethodologyScheduledAction,
        incumbentAssetIds: Set<String>,
        previousPathState: EquityMethodologyPathState,
        unavailableOnDate: LocalDate = action.selectionDate,
    ): ReconstitutedReferenceCandidates {
        require(action.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION)
        val unavailableIds = unavailableScheduledAssetIdsAt(methodology, unavailableOnDate)
        val universeIds = ordinaryReferenceEquityIdsByUniverse
            .getValue(methodology.profile.referenceUniverse)
        val snapshots = selectionSnapshotMapForAction(action, methodology)
            .filterKeys { assetId -> assetId in universeIds && assetId !in unavailableIds }
        val candidatesById = snapshots.mapValues { (_, snapshot) ->
            snapshot.toMethodologyCandidate(methodology, action.selectionDate)
        }
        val eligibleIncumbentIds = incumbentAssetIds.filterTo(linkedSetOf(), candidatesById::containsKey)
        val eligiblePreviousPathState = EquityMethodologyPathState(
            previousPathState.entries.filter { entry -> entry.assetId in candidatesById },
        )
        val result: EquityMethodologyReconstitutionResult = methodology.policy.reconstitute(
            EquityMethodologySelectionInput(
                profile = methodology.profile,
                scheduledAction = action,
                candidates = candidatesById.values.toList(),
                incumbentAssetIds = eligibleIncumbentIds,
                previousPathState = eligiblePreviousPathState,
            ),
        )
        val selected = result.selections
        val constraints = methodology.constraints
        require(selected.size in constraints.minimumConstituentCount..constraints.maximumConstituentCount) {
            "The methodology selected a constituent count outside its declared constraints."
        }
        constraints.scheduledSelectionCount?.let { expectedCount ->
            require(selected.size == expectedCount) {
                "The methodology selected an unexpected scheduled constituent count."
            }
        }
        require(selected.map { it.assetId }.distinct().size == selected.size) {
            "The methodology selected duplicate constituent IDs."
        }
        require(selected.map { it.rank }.distinct().size == selected.size) {
            "The methodology returned duplicate constituent ranks."
        }
        require(selected.all { selection -> selection.rank <= candidatesById.size }) {
            "The methodology returned a rank outside its candidate universe."
        }
        require(selected.all { selection -> selection.assetId in candidatesById }) {
            "The methodology selected an asset outside its candidate universe."
        }
        require(result.referenceMarketValueMultipliers.keys ==
            selected.mapTo(linkedSetOf(), EquityMethodologySelection::assetId)) {
            "The methodology returned multipliers for an unexpected constituent set."
        }
        require(result.referenceMarketValueMultipliers.values.all { multiplier ->
            multiplier.isFinite() && multiplier > 0.0 &&
                multiplier <= EquityMethodologyReconstitutionResult.MAX_PROVIDER_WEIGHTING_MULTIPLIER
        }) {
            "The methodology returned an invalid provider weighting multiplier."
        }
        require(result.nextPathState.entries.all { entry -> entry.assetId in candidatesById }) {
            "The methodology returned path state outside its candidate universe."
        }
        require(methodology.policy.usesPathState || result.nextPathState == EquityMethodologyPathState.EMPTY) {
            "A stateless methodology cannot persist scheduled-review path state."
        }
        val rankedCandidates = selected.map { selection ->
            RankedReferenceCandidate(
                snapshot = snapshots.getValue(selection.assetId),
                compositeRank = selection.rank,
            )
        }.sortedBy(RankedReferenceCandidate::compositeRank)
        return ReconstitutedReferenceCandidates(
            candidates = rankedCandidates,
            referenceMarketValueMultipliers = result.referenceMarketValueMultipliers,
            nextPathState = result.nextPathState,
        )
    }

    private fun applyReconstitutionMarketValueMultipliers(
        reconstitution: ReconstitutedReferenceCandidates,
        rawFloatMarketValues: Map<String, Double>,
    ): Map<String, Double> {
        require(rawFloatMarketValues.keys == reconstitution.referenceMarketValueMultipliers.keys)
        return buildMap {
            rawFloatMarketValues.toSortedMap().forEach { (assetId, rawMarketValue) ->
                require(rawMarketValue.isFinite() && rawMarketValue > 0.0)
                val multiplied = rawMarketValue *
                    reconstitution.referenceMarketValueMultipliers.getValue(assetId)
                require(multiplied.isFinite() && multiplied > 0.0)
                put(assetId, multiplied)
            }
        }
    }

    /** Removes the same complete provider multiplier once, preserving raw FMC across reviews. */
    private fun removeReconstitutionMarketValueMultipliers(
        reconstitution: ReconstitutedReferenceCandidates,
        multipliedFloatMarketValues: Map<String, Double>,
    ): Map<String, Double> {
        require(multipliedFloatMarketValues.keys == reconstitution.referenceMarketValueMultipliers.keys)
        return buildMap {
            multipliedFloatMarketValues.toSortedMap().forEach { (assetId, multipliedMarketValue) ->
                require(multipliedMarketValue.isFinite() && multipliedMarketValue > 0.0)
                val rawMarketValue = multipliedMarketValue /
                    reconstitution.referenceMarketValueMultipliers.getValue(assetId)
                require(rawMarketValue.isFinite() && rawMarketValue > 0.0)
                put(assetId, rawMarketValue)
            }
        }
    }

    private fun positionsForSelection(
        selected: List<RankedReferenceCandidate>,
        rawFloatMarketValues: Map<String, Double>,
        persistedReferenceMarketValues: Map<String, Double> = rawFloatMarketValues,
        methodology: CompiledEquityMethodology,
        actionKind: ReferencePortfolioActionKind,
        observationDate: LocalDate,
        effectiveDate: LocalDate,
        previousPositions: Map<String, ReferencePortfolioPosition>,
    ): List<ReferencePortfolioPosition> {
        require(selected.map { it.snapshot.definition.assetId }.toSet() == rawFloatMarketValues.keys)
        require(persistedReferenceMarketValues.keys == rawFloatMarketValues.keys)
        require(persistedReferenceMarketValues.values.all { value -> value.isFinite() && value > 0.0 })
        val targetWeights = targetWeights(
            methodology = methodology,
            selected = selected,
            actionKind = actionKind,
            observationDate = observationDate,
            effectiveDate = effectiveDate,
            rawFloatMarketValues = rawFloatMarketValues,
        )
        return selected.map { candidate ->
            val definition = candidate.snapshot.definition
            val targetWeight = targetWeights.getValue(definition.assetId)
            ReferencePortfolioPosition(
                assetId = definition.assetId,
                currentWeight = targetWeight,
                targetWeight = targetWeight,
                referenceFloatMarketValue = persistedReferenceMarketValues.getValue(definition.assetId),
                enteredOn = previousPositions[definition.assetId]?.enteredOn ?: effectiveDate,
                selectionRank = candidate.compositeRank,
            )
        }.sortedBy(ReferencePortfolioPosition::assetId).repairBothWeightRounding()
    }

    private fun referenceMarketValuesForWeighting(
        methodology: CompiledEquityMethodology,
        selected: List<RankedReferenceCandidate>,
        actionKind: ReferencePortfolioActionKind,
        observationDate: LocalDate,
        effectiveDate: LocalDate,
        rawFloatMarketValues: Map<String, Double>,
    ): Map<String, Double> {
        val candidates = selected.map { candidate ->
            candidate.snapshot.toMethodologyCandidate(methodology, observationDate)
        }
        val adjusted = methodology.policy.referenceMarketValuesForWeighting(
            EquityMethodologyWeightingInput(
                profile = methodology.profile,
                actionKind = actionKind,
                observationDate = observationDate,
                effectiveDate = effectiveDate,
                selectedCandidates = candidates,
                referenceMarketValues = rawFloatMarketValues,
            ),
        )
        require(adjusted.size <= ReferencePortfolioLimits.MAX_CONSTITUENTS) {
            "The methodology returned too many adjusted reference market values."
        }
        val result = buildMap { putAll(adjusted.toSortedMap()) }
        require(result.keys == rawFloatMarketValues.keys) {
            "The methodology adjusted an unexpected constituent set."
        }
        require(result.values.all { value -> value.isFinite() && value > 0.0 }) {
            "The methodology returned invalid adjusted reference market values."
        }
        return result
    }

    private fun cappedPositionsForExistingBasket(
        positions: List<ReferencePortfolioPosition>,
        methodology: CompiledEquityMethodology,
        actionKind: ReferencePortfolioActionKind,
        referenceDate: LocalDate,
        effectiveDate: LocalDate,
        rawFloatMarketValues: Map<String, Double>,
    ): List<ReferencePortfolioPosition> {
        require(positions.isNotEmpty()) { "빈 구성에는 상한 비중을 배정할 수 없습니다." }
        require(rawFloatMarketValues.keys == positions.mapTo(linkedSetOf(), ReferencePortfolioPosition::assetId))
        val snapshots = snapshotMapForKnownDataAt(referenceDate, methodology)
        val selected = positions.map { position ->
            RankedReferenceCandidate(
                snapshot = requireNotNull(snapshots[position.assetId]),
                compositeRank = position.selectionRank,
            )
        }
        val targetWeights = targetWeights(
            methodology = methodology,
            selected = selected,
            actionKind = actionKind,
            observationDate = referenceDate,
            effectiveDate = effectiveDate,
            rawFloatMarketValues = rawFloatMarketValues,
        )
        return positions.map { position ->
            val targetWeight = targetWeights.getValue(position.assetId)
            position.copy(
                currentWeight = targetWeight,
                targetWeight = targetWeight,
            )
        }.sortedBy(ReferencePortfolioPosition::assetId).repairBothWeightRounding()
    }

    private fun targetWeights(
        methodology: CompiledEquityMethodology,
        selected: List<RankedReferenceCandidate>,
        actionKind: ReferencePortfolioActionKind,
        observationDate: LocalDate,
        effectiveDate: LocalDate,
        rawFloatMarketValues: Map<String, Double>,
    ): Map<String, Double> {
        val candidates = selected.map { candidate ->
            candidate.snapshot.toMethodologyCandidate(methodology, observationDate)
        }
        val returnedWeights = methodology.policy.targetWeights(
            EquityMethodologyWeightingInput(
                profile = methodology.profile,
                actionKind = actionKind,
                observationDate = observationDate,
                effectiveDate = effectiveDate,
                selectedCandidates = candidates,
                referenceMarketValues = rawFloatMarketValues,
            ),
        )
        require(returnedWeights.size <= ReferencePortfolioLimits.MAX_CONSTITUENTS) {
            "The methodology returned too many target weights."
        }
        val result = buildMap { putAll(returnedWeights.toSortedMap()) }
        require(result.keys == rawFloatMarketValues.keys) {
            "The methodology returned weights for an unexpected constituent set."
        }
        require(result.values.all { weight -> weight.isFinite() && weight >= 0.0 }) {
            "The methodology returned invalid target weights."
        }
        methodology.constraints.individualWeightCap?.let { individualCap ->
            require(result.values.all { weight -> weight <= individualCap + WEIGHT_ALLOCATION_EPSILON }) {
                "The methodology returned a target weight above the individual cap."
            }
        }
        val sectorWeights = result.entries.groupBy { (assetId, _) ->
            referenceEquityById.getValue(assetId).methodologySector
        }.values.map { entries -> entries.sumOf(Map.Entry<String, Double>::value) }
        methodology.constraints.sectorWeightCap?.let { groupCap ->
            require(sectorWeights.all { weight -> weight <= groupCap + WEIGHT_ALLOCATION_EPSILON }) {
                "The methodology returned target weights above the group cap."
            }
        }
        require(abs(result.values.sum() - 1.0) <= 1e-10) {
            "The methodology target weights do not sum to 100%."
        }
        return result
    }

    private fun extraordinaryRemovalDecision(
        methodology: CompiledEquityMethodology,
        assetIds: Set<String>,
        observationDate: LocalDate,
    ): Pair<LocalDate, Set<String>>? {
        if (assetIds.isEmpty()) return null
        val snapshots = snapshotMapForKnownDataAt(observationDate, methodology)
        val decision = methodology.policy.extraordinaryRemovalDecision(
            EquityMethodologyRemovalInput(
                profile = methodology.profile,
                observationDate = observationDate,
                constituents = assetIds.sorted().map { assetId ->
                    snapshots.getValue(assetId).toMethodologyCandidate(methodology, observationDate)
                },
            ),
        ) ?: return null
        val removedIds = buildSet { addAll(decision.removedAssetIds.sorted()) }
        require(removedIds.all(assetIds::contains)) {
            "The methodology removed an asset outside the current constituent set."
        }
        require(decision.effectiveDate > observationDate && methodology.schedule.isTradingDate(decision.effectiveDate)) {
            "The methodology returned an invalid extraordinary-removal effective date."
        }
        return decision.effectiveDate to removedIds
    }

    /** Canonical seed-derived reference-asset event announced at one methodology close. */
    private fun canonicalCorporateActionOn(
        methodology: CompiledEquityMethodology,
        announcementDate: LocalDate,
    ): ReferencePortfolioCorporateAction? = corporateActionByDateCache.getOrPut(
        methodology.profile to announcementDate,
    ) {
        if (!methodology.schedule.isTradingDate(announcementDate) ||
            announcementDate < methodology.profile.effectiveFrom ||
            announcementDate >= GameCalendar.CAMPAIGN_END_DATE
        ) {
            return@getOrPut null
        }
        val random = DeterministicRandom.keyed(
            seed,
            "fund-reference-corporate-action:${methodology.profile.methodologyRef}:$announcementDate",
        )
        if (!random.nextBoolean(REFERENCE_CORPORATE_ACTION_DAILY_PROBABILITY)) {
            return@getOrPut null
        }
        val kind = when (random.nextInt(100)) {
            in 0..54 -> ReferencePortfolioCorporateActionKind.MERGER
            in 55..79 -> ReferencePortfolioCorporateActionKind.SPIN_OFF
            else -> ReferencePortfolioCorporateActionKind.TERMINAL_REMOVAL
        }
        val ordinaryUniverse = ordinaryReferenceEquitiesByUniverse
            .getValue(methodology.profile.referenceUniverse)
        val primaryIndex = random.nextInt(ordinaryUniverse.size)
        val primaryId = ordinaryUniverse[primaryIndex].assetId
        val secondaryId = when (kind) {
            ReferencePortfolioCorporateActionKind.TERMINAL_REMOVAL -> null
            ReferencePortfolioCorporateActionKind.SPIN_OFF -> spinOffChildAssetId(
                methodology.profile.referenceUniverse,
                announcementDate,
            )
            ReferencePortfolioCorporateActionKind.MERGER -> ordinaryUniverse[
                (primaryIndex + 1 + random.nextInt(ordinaryUniverse.size - 1)) %
                    ordinaryUniverse.size
            ].assetId
        }
        val noticeTradingDays = methodology.policy.corporateActionNoticeTradingDays(
            methodology.profile,
            kind,
        )
        require(noticeTradingDays in 1..MAX_CORPORATE_ACTION_NOTICE_TRADING_DAYS) {
            "A methodology corporate-action notice must be within the supported trading-day window."
        }
        val effectiveDate = methodology.schedule.addTradingDays(
            announcementDate,
            noticeTradingDays,
        )
        val consideration = if (kind == ReferencePortfolioCorporateActionKind.MERGER) {
            when (random.nextInt(3)) {
                0 -> ReferencePortfolioCorporateActionConsiderationKind.CASH
                1 -> ReferencePortfolioCorporateActionConsiderationKind.STOCK
                else -> ReferencePortfolioCorporateActionConsiderationKind.MIXED
            }
        } else {
            ReferencePortfolioCorporateActionConsiderationKind.NONE
        }
        val transferFraction = when {
            kind == ReferencePortfolioCorporateActionKind.SPIN_OFF -> random.nextDouble(0.04, 0.24)
            consideration == ReferencePortfolioCorporateActionConsiderationKind.STOCK -> 1.0
            consideration == ReferencePortfolioCorporateActionConsiderationKind.MIXED ->
                random.nextDouble(0.20, 0.80)
            else -> 0.0
        }
        val followUpDate = if (kind == ReferencePortfolioCorporateActionKind.SPIN_OFF) {
            methodology.schedule.addTradingDays(effectiveDate, 1)
        } else {
            null
        }
        ReferencePortfolioCorporateAction(
            eventId = "reference-event:${methodology.definition.ref.benchmarkId}:v" +
                "${methodology.definition.ref.version}:$announcementDate:${kind.name}:$primaryId",
            kind = kind,
            announcementDate = announcementDate,
            effectiveDate = effectiveDate,
            primaryAssetId = primaryId,
            secondaryAssetId = secondaryId,
            considerationKind = consideration,
            valueTransferFraction = transferFraction,
            followUpEffectiveDate = followUpDate,
        )
    }

    /** Assets that cannot participate in a normal annual selection at [date]. */
    private fun unavailableScheduledAssetIdsAt(
        methodology: CompiledEquityMethodology,
        date: LocalDate,
    ): Set<String> = unavailableScheduledAssetIdsCache.getOrPut(methodology.profile to date) {
        val unavailable = spinOffChildAssetIdsByUniverse
            .getValue(methodology.profile.referenceUniverse)
            .toMutableSet()
        var cursor = methodology.profile.effectiveFrom
        while (cursor <= date) {
            canonicalCorporateActionOn(methodology, cursor)?.let { action ->
                if (action.effectiveDate <= date &&
                    (action.kind == ReferencePortfolioCorporateActionKind.MERGER ||
                        action.kind == ReferencePortfolioCorporateActionKind.TERMINAL_REMOVAL)
                ) {
                    unavailable += action.primaryAssetId
                }
            }
            cursor = cursor.plus(1, DateTimeUnit.DAY)
        }
        unavailable.toSet()
    }

    private fun SimulatedReferenceEquitySnapshot.toMethodologyCandidate(
        methodology: CompiledEquityMethodology,
        observationDate: LocalDate,
    ): EquityMethodologyCandidate {
        val supportedDecimals = mapOf(
            StandardEquityMethodologySignalIds.FLOAT_MARKET_CAP to floatMarketCap,
            StandardEquityMethodologySignalIds.TOTAL_COMPANY_MARKET_CAP to totalCompanyMarketCap,
            StandardEquityMethodologySignalIds.INVESTABLE_WEIGHT_FACTOR to investableWeightFactor,
            StandardEquityMethodologySignalIds.FLOAT_ADJUSTED_LIQUIDITY_RATIO to
                floatAdjustedLiquidityRatio,
            StandardEquityMethodologySignalIds.MINIMUM_SIX_MONTH_MONTHLY_SHARE_VOLUME to
                minimumSixMonthMonthlyShareVolume,
            StandardEquityMethodologySignalIds.AVERAGE_DAILY_VALUE_TRADED to
                threeMonthAverageDailyValueTraded,
            StandardEquityMethodologySignalIds.MEDIAN_DAILY_VALUE_TRADED to
                threeMonthMedianDailyValueTraded,
            StandardEquityMethodologySignalIds
                .TRAILING_125_TRADING_DAY_AVERAGE_DAILY_VALUE_TRADED to
                trailing125TradingDayAverageDailyValueTraded,
            StandardEquityMethodologySignalIds.INDICATED_DIVIDEND_YIELD to indicatedDividendYield,
            StandardEquityMethodologySignalIds.FREE_CASH_FLOW_TO_DEBT to freeCashFlowToDebt,
            StandardEquityMethodologySignalIds.RETURN_ON_EQUITY to returnOnEquity,
            StandardEquityMethodologySignalIds.FIVE_YEAR_DIVIDEND_GROWTH to fiveYearDividendGrowth,
            StandardEquityMethodologySignalIds.THREE_YEAR_AVERAGE_DIVIDEND_PAYOUT_RATIO to
                threeYearAverageDividendPayoutRatio,
            StandardEquityMethodologySignalIds.THREE_YEAR_AVERAGE_RETURN_ON_EQUITY to
                threeYearAverageReturnOnEquity,
            StandardEquityMethodologySignalIds.ONE_MONTH_AVERAGE_DAILY_VALUE_TRADED to
                oneMonthAverageDailyValueTraded,
            StandardEquityMethodologySignalIds.ONE_MONTH_AVERAGE_PRICE_TO_BOOK_RATIO to
                oneMonthAveragePriceToBookRatio,
            StandardEquityMethodologySignalIds.ONE_MONTH_AVERAGE_DIVIDEND_YIELD to
                oneMonthAverageDividendYield,
            StandardEquityMethodologySignalIds.ONE_MONTH_AVERAGE_MARKET_CAP to
                oneMonthAverageMarketCap,
            StandardEquityMethodologySignalIds.TRAILING_FOUR_QUARTER_TOTAL_CASH_DIVIDENDS to
                trailingFourQuarterTotalCashDividends,
            StandardEquityMethodologySignalIds.BOOK_TO_PRICE to bookToPrice,
            StandardEquityMethodologySignalIds.FUTURE_EARNINGS_TO_PRICE to futureEarningsToPrice,
            StandardEquityMethodologySignalIds.HISTORICAL_EARNINGS_TO_PRICE to
                historicalEarningsToPrice,
            StandardEquityMethodologySignalIds.DIVIDEND_TO_PRICE to dividendToPrice,
            StandardEquityMethodologySignalIds.SALES_TO_PRICE to salesToPrice,
            StandardEquityMethodologySignalIds.FUTURE_LONG_TERM_EARNINGS_GROWTH to
                futureLongTermEarningsGrowth,
            StandardEquityMethodologySignalIds.FUTURE_SHORT_TERM_EARNINGS_GROWTH to
                futureShortTermEarningsGrowth,
            StandardEquityMethodologySignalIds.THREE_YEAR_HISTORICAL_EARNINGS_GROWTH to
                threeYearHistoricalEarningsGrowth,
            StandardEquityMethodologySignalIds.THREE_YEAR_HISTORICAL_SALES_GROWTH to
                threeYearHistoricalSalesGrowth,
            StandardEquityMethodologySignalIds.CURRENT_INVESTMENT_TO_ASSETS to
                currentInvestmentToAssets,
            StandardEquityMethodologySignalIds.RETURN_ON_ASSETS to returnOnAssets,
        )
        val supportedIntegers = mapOf(
            StandardEquityMethodologySignalIds.GICS_CLASSIFICATION_CODE to
                definition.gicsClassificationCode,
            StandardEquityMethodologySignalIds.DIVIDEND_PAYMENT_YEARS to dividendPaymentYears,
            StandardEquityMethodologySignalIds.LISTING_AGE_YEARS to listingAgeYears,
        )
        val supportedBooleans = mapOf(
            StandardEquityMethodologySignalIds.ZERO_TOTAL_DEBT to zeroTotalDebt,
            StandardEquityMethodologySignalIds.NEGATIVE_BOOK_VALUE_PER_SHARE to
                negativeBookValuePerShare,
            StandardEquityMethodologySignalIds.SCHEDULED_DIVIDEND_PAYMENT_OMITTED to
                hasDividendEventInCurrentReviewWindow(
                    definition,
                    observationDate,
                    DIVIDEND_EVENT_OMITTED,
                ),
            StandardEquityMethodologySignalIds.DIVIDEND_PROGRAM_CEASED_INDEFINITELY to
                hasDividendEventInCurrentReviewWindow(
                    definition,
                    observationDate,
                    DIVIDEND_EVENT_CEASED_INDEFINITELY,
                ),
            StandardEquityMethodologySignalIds.LATEST_QUARTER_GAAP_NET_INCOME_POSITIVE to
                latestQuarterGaapNetIncomePositive,
            StandardEquityMethodologySignalIds.TRAILING_FOUR_QUARTER_GAAP_NET_INCOME_POSITIVE to
                trailingFourQuarterGaapNetIncomePositive,
            StandardEquityMethodologySignalIds.KOSPI200_FINANCIAL_MEMBER to
                kospi200FinancialMember,
        )
        return EquityMethodologyCandidate(
            assetId = definition.assetId,
            sector = definition.methodologySector,
            signals = EquityMethodologySignals(
                decimals = methodology.requiredDecimalSignalIds.associateWith(supportedDecimals::getValue),
                integers = methodology.requiredIntegerSignalIds.associateWith(supportedIntegers::getValue),
                booleans = methodology.requiredBooleanSignalIds.associateWith(supportedBooleans::getValue),
                texts = methodology.requiredTextSignalIds.associateWith {
                    error("The reference-portfolio host has no supported text signals.")
                },
            ),
        )
    }

    private fun advancePositions(
        positions: List<ReferencePortfolioPosition>,
        assetReturns: Map<String, Double>,
    ): List<ReferencePortfolioPosition> {
        val grossFactor = positions.sumOf { position ->
            position.currentWeight * exp(assetReturns.getValue(position.assetId))
        }.coerceAtLeast(MIN_PORTFOLIO_FACTOR)
        return positions.map { position ->
            val factor = exp(assetReturns.getValue(position.assetId))
            position.copy(
                currentWeight = position.currentWeight * factor / grossFactor,
                referenceFloatMarketValue = (position.referenceFloatMarketValue * factor)
                    .coerceIn(MIN_REFERENCE_MARKET_CAP, MAX_REFERENCE_MARKET_CAP),
            )
        }.repairCurrentWeightRounding().sortedBy(ReferencePortfolioPosition::assetId)
    }

    private fun constituentLogReturn(
        snapshot: SimulatedReferenceEquitySnapshot,
        from: Instant,
        fraction: Double,
        marketReturn: Double,
        macro: MacroEnvironment,
    ): Double {
        val asset = snapshot.definition
        val sectorReturn = macro.sectorHourlyReturns[asset.sector] ?: 0.0
        val qualityTilt = (asset.quality - 0.5) *
            (macro.institutionalOrderFlow - macro.liquidityStress * 0.5) * QUALITY_TILT_SCALE
        val valueTilt = (asset.value - 0.5) *
            (macro.institutionalOrderFlow - macro.retailOrderFlow) * VALUE_TILT_SCALE
        val dividendTilt = (snapshot.indicatedDividendYield - REFERENCE_DIVIDEND_YIELD) *
            (-macro.policyRateChange * DIVIDEND_RATE_SCALE + macro.riskSentiment * DIVIDEND_SENTIMENT_SCALE)
        val systemicDiffusion = (
            asset.beta * marketReturn + SECTOR_LOADING * sectorReturn
            ) * sqrt(fraction)
        val styleCarry = (qualityTilt + valueTilt + dividendTilt) * fraction
        val random = DeterministicRandom.keyed(
            seed,
            "fund-reference-return:${asset.assetId}:${from.epochSeconds}",
        )
        val volatility = asset.annualVolatility / sqrt(TRADING_HOURS_PER_YEAR) *
            sqrt(macro.volatilityRegime.coerceIn(0.25, 4.0)) * sqrt(fraction)
        val residual = -0.5 * volatility * volatility + volatility * random.nextGaussian()
        return (systemicDiffusion + styleCarry + residual)
            .coerceIn(-MAX_CONSTITUENT_LOG_MOVE, MAX_CONSTITUENT_LOG_MOVE)
    }

    private fun snapshotMapForKnownDataAt(
        date: LocalDate,
        methodology: CompiledEquityMethodology,
    ): Map<String, SimulatedReferenceEquitySnapshot> {
        val selectedAction = latestReconstitutionSelectedInYear(methodology, date)
        return if (selectedAction != null && date.year > REFERENCE_BASE_YEAR) {
            buildMap {
                putAll(selectionSnapshotMapForAction(selectedAction, methodology))
                snapshotMapForYear(date.year - 1).forEach { (assetId, snapshot) ->
                    if (assetId in spinOffChildAssetIds) put(assetId, snapshot)
                }
            }
        } else {
            snapshotMapForYear((date.year - 1).coerceAtLeast(REFERENCE_BASE_YEAR))
        }
    }

    private fun latestReconstitutionSelectedInYear(
        methodology: CompiledEquityMethodology,
        date: LocalDate,
    ): EquityMethodologyScheduledAction? {
        var action = initialScheduledAction(methodology)
        var latest: EquityMethodologyScheduledAction? = null
        var actionCount = 0
        while (action.selectionDate.year <= date.year) {
            require(++actionCount <= MAX_PREFLIGHT_SCHEDULED_ACTIONS) {
                "The methodology returned too many reconstitutions in the supported lookup window."
            }
            if (action.selectionDate.year == date.year && action.selectionDate <= date) {
                latest = action
            }
            if (!methodology.policy.hasRecurringScheduledReconstitution) break
            action = nextScheduledAction(
                methodology = methodology,
                afterExclusive = action.effectiveDate,
                kind = ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION,
            )
        }
        return latest
    }

    /**
     * CFO, CapEx, total debt, basic EPS, BVPS and five-year regular-DPS history stay frozen at
     * prior year-end. Only FMC, the trailing liquidity windows, the regular fixed IAD and share
     * price are advanced to the official selection date.
     */
    private fun selectionSnapshotMapForAction(
        action: EquityMethodologyScheduledAction,
        methodology: CompiledEquityMethodology,
    ): Map<String, SimulatedReferenceEquitySnapshot> {
        require(action.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION)
        val effectiveYear = action.effectiveDate.year
        val selectionDate = action.selectionDate
        require(selectionDate.year in (REFERENCE_BASE_YEAR + 1)..MAX_SCENARIO_YEAR)
        return selectionSnapshotByIdCache.getOrPut(methodology.profile to selectionDate) {
            val priorYear = selectionDate.year - 1
            val universeIds = ordinaryReferenceEquityIdsByUniverse
                .getValue(methodology.profile.referenceUniverse)
            snapshotMapForYear(priorYear)
                .filterKeys(universeIds::contains)
                .mapValues { (_, priorSnapshot) ->
                val equity = priorSnapshot.definition
                val currentMarketCap = simulatedReferenceMarketValueAt(
                    methodology = methodology,
                    snapshot = priorSnapshot,
                    snapshotYear = priorYear,
                    date = selectionDate,
                )
                val liquidityRandom = DeterministicRandom.keyed(
                    seed,
                    "fund-selection-liquidity:${equity.assetId}:$effectiveYear",
                )
                val priorTurnover = priorSnapshot.threeMonthAverageDailyValueTraded /
                    priorSnapshot.floatMarketCap
                val currentTurnover = (priorTurnover * exp(liquidityRandom.nextGaussian() * 0.055))
                    .coerceIn(MIN_SELECTION_DAILY_TURNOVER, MAX_SELECTION_DAILY_TURNOVER)
                val medianLiquidityRandom = DeterministicRandom.keyed(
                    seed,
                    "fund-selection-three-month-mdvt:${equity.assetId}:$selectionDate",
                )
                val priorMedianTurnover = priorSnapshot.threeMonthMedianDailyValueTraded /
                    priorSnapshot.floatMarketCap
                val currentMedianTurnover = (
                    priorMedianTurnover * exp(medianLiquidityRandom.nextGaussian() * 0.055)
                    ).coerceIn(MIN_SELECTION_DAILY_TURNOVER, MAX_SELECTION_DAILY_TURNOVER)
                val morningstarLiquidityRandom = DeterministicRandom.keyed(
                    seed,
                    "fund-selection-morningstar-125-day-advt:${equity.assetId}:$selectionDate",
                )
                val priorMorningstarTurnover =
                    priorSnapshot.trailing125TradingDayAverageDailyValueTraded /
                        priorSnapshot.floatMarketCap
                val currentMorningstarTurnover = (
                    priorMorningstarTurnover *
                        exp(morningstarLiquidityRandom.nextGaussian() * 0.055)
                    ).coerceIn(MIN_SELECTION_DAILY_TURNOVER, MAX_SELECTION_DAILY_TURNOVER)

                val dividendRandom = DeterministicRandom.keyed(
                    seed,
                    "fund-selection-indicated-dividend:${equity.assetId}:$effectiveYear",
                )
                val supplementalSelectionRandom = DeterministicRandom.keyed(
                    seed,
                    "fund-selection-supplemental-signals:${equity.assetId}:$selectionDate",
                )
                val morningstarSelectionRandom = DeterministicRandom.keyed(
                    seed,
                    "fund-selection-morningstar-style-signals:${equity.assetId}:$selectionDate",
                )
                val currentKospi200FinancialMember = if (
                    equity.referenceUniverse == FundReferenceUniverse.KOREA_BROAD_EQUITY &&
                    equity.methodologySector == MethodologyEquitySector.FINANCIALS &&
                    supplementalSelectionRandom.nextBoolean(0.035)
                ) {
                    !priorSnapshot.kospi200FinancialMember
                } else {
                    priorSnapshot.kospi200FinancialMember
                }
                val ordinaryCut = dividendRandom.nextBoolean(
                    (0.050 - equity.quality * 0.038).coerceIn(0.008, 0.045),
                )
                var indicatedCashFactor = if (ordinaryCut) {
                    dividendRandom.nextDouble(0.55, 0.90)
                } else {
                    exp(dividendRandom.nextGaussian() * 0.045)
                }
                val selectionMonth = selectionDate.month.ordinal + 1
                val suspendedBySelection = (1..selectionMonth).any { month ->
                    dividendMaintenanceEvent(equity, effectiveYear, month) ==
                        DIVIDEND_EVENT_CEASED_INDEFINITELY &&
                        (month < selectionMonth ||
                            dividendEventAnnouncementDay(equity, effectiveYear, month) <=
                            selectionDate.day)
                }
                if (suspendedBySelection) indicatedCashFactor *= SUSPENDED_DIVIDEND_CASH_FACTOR
                val priceFactor = currentMarketCap / priorSnapshot.floatMarketCap
                val selectionMaxSharePrice = if (
                    equity.referenceUniverse == FundReferenceUniverse.KOREA_BROAD_EQUITY
                ) {
                    MAX_KOREA_SHARE_PRICE
                } else {
                    MAX_SHARE_PRICE
                }
                val selectionMaxDividend = if (
                    equity.referenceUniverse == FundReferenceUniverse.KOREA_BROAD_EQUITY
                ) {
                    MAX_KOREA_REGULAR_DIVIDEND_PER_SHARE
                } else {
                    MAX_REGULAR_DIVIDEND_PER_SHARE
                }
                val currentSharePrice = (priorSnapshot.sharePrice * priceFactor)
                    .coerceIn(MIN_SHARE_PRICE, selectionMaxSharePrice)
                val rawRegularFixedDividend = (
                    priorSnapshot.regularFixedAnnualDividendPerShare * indicatedCashFactor
                    ).coerceIn(MIN_REGULAR_DIVIDEND_PER_SHARE, selectionMaxDividend)
                val currentYield = (rawRegularFixedDividend / currentSharePrice)
                    .coerceIn(MIN_INDICATED_DIVIDEND_YIELD, MAX_INDICATED_DIVIDEND_YIELD)
                val currentTotalCompanyMarketCap = currentMarketCap /
                    priorSnapshot.investableWeightFactor
                priorSnapshot.copy(
                    floatMarketCap = currentMarketCap,
                    kospi200FinancialMember = currentKospi200FinancialMember,
                    threeMonthAverageDailyValueTraded = currentMarketCap * currentTurnover,
                    threeMonthMedianDailyValueTraded = currentMarketCap * currentMedianTurnover,
                    trailing125TradingDayAverageDailyValueTraded =
                        currentMarketCap * currentMorningstarTurnover,
                    twelveMonthAverageDailyValueTraded = currentMarketCap *
                        (priorSnapshot.twelveMonthAverageDailyValueTraded /
                            priorSnapshot.floatMarketCap),
                    minimumSixMonthMonthlyShareVolume =
                        priorSnapshot.minimumSixMonthMonthlyShareVolume *
                            (currentMarketCap / priorSnapshot.floatMarketCap) /
                            (currentSharePrice / priorSnapshot.sharePrice),
                    sharePrice = currentSharePrice,
                    regularFixedAnnualDividendPerShare = currentSharePrice * currentYield,
                    oneMonthAverageDailyValueTraded = currentMarketCap * (
                        priorSnapshot.oneMonthAverageDailyValueTraded /
                            priorSnapshot.floatMarketCap *
                            exp(supplementalSelectionRandom.nextGaussian() * 0.035)
                        ).coerceIn(0.00005, 0.08),
                    oneMonthAveragePriceToBookRatio = if (priorSnapshot.bookValuePerShare > 0.0) {
                        (currentSharePrice / priorSnapshot.bookValuePerShare *
                            exp(supplementalSelectionRandom.nextGaussian() * 0.012))
                            .coerceIn(0.01, 30.0)
                    } else {
                        30.0
                    },
                    oneMonthAverageDividendYield = currentYield,
                    oneMonthAverageMarketCap = currentTotalCompanyMarketCap *
                        exp(supplementalSelectionRandom.nextGaussian() * 0.012),
                    trailingFourQuarterTotalCashDividends =
                        currentTotalCompanyMarketCap / currentSharePrice *
                            (currentSharePrice * currentYield),
                    futureEarningsPerShare = (
                        priorSnapshot.futureEarningsPerShare * priceFactor +
                            currentSharePrice * morningstarSelectionRandom.nextGaussian() * 0.006
                        ).coerceIn(
                        -selectionMaxSharePrice,
                        selectionMaxSharePrice,
                    ),
                    salesPerShare = (priorSnapshot.salesPerShare * priceFactor *
                        exp(morningstarSelectionRandom.nextGaussian() * 0.025))
                        .coerceIn(MIN_STYLE_SALES_PER_SHARE, MAX_STYLE_SALES_PER_SHARE),
                    futureLongTermEarningsGrowth = (
                        priorSnapshot.futureLongTermEarningsGrowth +
                            morningstarSelectionRandom.nextGaussian() * 0.018
                        ).coerceIn(MIN_STYLE_GROWTH, MAX_STYLE_GROWTH),
                    futureShortTermEarningsGrowth = (
                        priorSnapshot.futureShortTermEarningsGrowth +
                            morningstarSelectionRandom.nextGaussian() * 0.028
                        ).coerceIn(MIN_STYLE_GROWTH, MAX_STYLE_GROWTH),
                    threeYearHistoricalEarningsGrowth = (
                        priorSnapshot.threeYearHistoricalEarningsGrowth +
                            morningstarSelectionRandom.nextGaussian() * 0.012
                        ).coerceIn(MIN_STYLE_GROWTH, MAX_STYLE_GROWTH),
                    threeYearHistoricalSalesGrowth = (
                        priorSnapshot.threeYearHistoricalSalesGrowth +
                            morningstarSelectionRandom.nextGaussian() * 0.010
                        ).coerceIn(MIN_STYLE_GROWTH, MAX_STYLE_GROWTH),
                    currentInvestmentToAssets = (
                        priorSnapshot.currentInvestmentToAssets +
                            morningstarSelectionRandom.nextGaussian() * 0.015
                        ).coerceIn(
                        MIN_STYLE_INVESTMENT_TO_ASSETS,
                        MAX_STYLE_INVESTMENT_TO_ASSETS,
                    ),
                    returnOnAssets = (
                        priorSnapshot.returnOnAssets +
                            morningstarSelectionRandom.nextGaussian() * 0.012
                        ).coerceIn(MIN_STYLE_RETURN_ON_ASSETS, MAX_STYLE_RETURN_ON_ASSETS),
                )
                }
        }
    }

    private fun snapshotMapForYear(year: Int): Map<String, SimulatedReferenceEquitySnapshot> {
        require(year in REFERENCE_BASE_YEAR..MAX_SCENARIO_YEAR)
        return annualSnapshotByIdCache.getOrPut(year) {
            referenceEquities.associate { equity ->
                equity.assetId to annualSnapshot(equity, year)
            }
        }
    }

    private fun annualSnapshot(
        equity: SimulatedReferenceEquity,
        year: Int,
    ): SimulatedReferenceEquitySnapshot {
        val maxSharePrice = if (equity.referenceUniverse == FundReferenceUniverse.KOREA_BROAD_EQUITY) {
            MAX_KOREA_SHARE_PRICE
        } else {
            MAX_SHARE_PRICE
        }
        val maxBookValuePerShare =
            if (equity.referenceUniverse == FundReferenceUniverse.KOREA_BROAD_EQUITY) {
                MAX_KOREA_ABSOLUTE_BOOK_VALUE_PER_SHARE
            } else {
                MAX_ABSOLUTE_BOOK_VALUE_PER_SHARE
            }
        val maxAbsoluteEarningsPerShare =
            if (equity.referenceUniverse == FundReferenceUniverse.KOREA_BROAD_EQUITY) {
                MAX_KOREA_ABSOLUTE_EARNINGS_PER_SHARE
            } else {
                MAX_BASIC_EARNINGS_PER_SHARE
            }
        val minEarningsPerShare =
            if (equity.referenceUniverse == FundReferenceUniverse.KOREA_BROAD_EQUITY) {
                -MAX_KOREA_ABSOLUTE_EARNINGS_PER_SHARE
            } else {
                MIN_BASIC_EARNINGS_PER_SHARE
            }
        val maxRegularDividendPerShare =
            if (equity.referenceUniverse == FundReferenceUniverse.KOREA_BROAD_EQUITY) {
                MAX_KOREA_REGULAR_DIVIDEND_PER_SHARE
            } else {
                MAX_REGULAR_DIVIDEND_PER_SHARE
            }
        var marketCap = equity.baseFloatMarketCap
        var investableWeightFactor = equity.baseInvestableWeightFactor
        var threeMonthAverageDailyValueTraded = equity.baseThreeMonthAverageDailyValueTraded
        var threeMonthMedianDailyValueTraded = equity.baseThreeMonthMedianDailyValueTraded
        var trailing125TradingDayAverageDailyValueTraded =
            equity.baseTrailing125TradingDayAverageDailyValueTraded
        var twelveMonthAverageDailyValueTraded = equity.baseTwelveMonthAverageDailyValueTraded
        var minimumSixMonthMonthlyShareVolume = equity.baseMinimumSixMonthMonthlyShareVolume
        var latestQuarterGaapNetIncome = equity.baseLatestQuarterGaapNetIncome
        var trailingFourQuarterGaapNetIncome = equity.baseTrailingFourQuarterGaapNetIncome
        var dividendYears = equity.baseDividendPaymentYears
        var cashFlowFromOperations = equity.baseCashFlowFromOperations
        var capitalExpenditures = equity.baseCapitalExpenditures
        var totalDebt = equity.baseTotalDebt
        var basicEarningsPerShare = equity.baseBasicEarningsPerShare
        var bookValuePerShare = equity.baseBookValuePerShare
        var sharePrice = equity.baseSharePrice
        var regularFixedAnnualDividendPerShare = equity.baseRegularFixedAnnualDividendPerShare
        var annualRegularDividendPerShareNewestFirst =
            equity.baseAnnualRegularDividendPerShareNewestFirst
        var listingAgeYears = equity.baseListingAgeYears
        var kospi200FinancialMember = equity.baseKospi200FinancialMember
        var threeYearAverageDividendPayoutRatio =
            equity.baseThreeYearAverageDividendPayoutRatio
        var threeYearAverageReturnOnEquity = equity.baseThreeYearAverageReturnOnEquity
        var oneMonthAverageDailyValueTraded = equity.baseOneMonthAverageDailyValueTraded
        var oneMonthAveragePriceToBookRatio = equity.baseOneMonthAveragePriceToBookRatio
        var oneMonthAverageDividendYield = equity.baseOneMonthAverageDividendYield
        var oneMonthAverageMarketCap = equity.baseOneMonthAverageMarketCap
        var trailingFourQuarterTotalCashDividends =
            equity.baseTrailingFourQuarterTotalCashDividends
        var futureEarningsPerShare = equity.baseFutureEarningsPerShare
        var salesPerShare = equity.baseSalesPerShare
        var futureLongTermEarningsGrowth = equity.baseFutureLongTermEarningsGrowth
        var futureShortTermEarningsGrowth = equity.baseFutureShortTermEarningsGrowth
        var threeYearHistoricalEarningsGrowth = equity.baseThreeYearHistoricalEarningsGrowth
        var threeYearHistoricalSalesGrowth = equity.baseThreeYearHistoricalSalesGrowth
        var currentInvestmentToAssets = equity.baseCurrentInvestmentToAssets
        var returnOnAssets = equity.baseReturnOnAssets
        for (candidateYear in (REFERENCE_BASE_YEAR + 1)..year) {
            val random = DeterministicRandom.keyed(
                seed,
                "fund-reference-fundamentals:${equity.assetId}:$candidateYear",
            )
            val sp500SignalRandom = DeterministicRandom.keyed(
                seed,
                "fund-reference-sp500-signals:${equity.assetId}:$candidateYear",
            )
            val supplementalSignalRandom = DeterministicRandom.keyed(
                seed,
                "fund-reference-supplemental-signals:${equity.assetId}:$candidateYear",
            )
            val morningstarSignalRandom = DeterministicRandom.keyed(
                seed,
                "fund-reference-morningstar-style-signals:${equity.assetId}:$candidateYear",
            )
            val morningstarLiquidityRandom = DeterministicRandom.keyed(
                seed,
                "fund-reference-morningstar-125-day-advt:${equity.assetId}:$candidateYear",
            )
            val medianLiquidityRandom = DeterministicRandom.keyed(
                seed,
                "fund-reference-three-month-mdvt:${equity.assetId}:$candidateYear",
            )
            val priorMarketCap = marketCap
            val priorInvestableWeightFactor = investableWeightFactor
            val priorSharePrice = sharePrice
            val growth = (0.025 + equity.quality * 0.055 + random.nextGaussian() * 0.16)
                .coerceIn(-0.45, 0.55)
            marketCap = (marketCap * exp(growth)).coerceIn(MIN_REFERENCE_MARKET_CAP, MAX_REFERENCE_MARKET_CAP)

            investableWeightFactor = (
                investableWeightFactor + sp500SignalRandom.nextGaussian() * 0.012
                )
                .coerceIn(MIN_INVESTABLE_WEIGHT_FACTOR, MAX_INVESTABLE_WEIGHT_FACTOR)

            val priorTurnover = threeMonthAverageDailyValueTraded / priorMarketCap
            val currentTurnover = (priorTurnover * exp(random.nextGaussian() * 0.08))
                .coerceIn(MIN_SELECTION_DAILY_TURNOVER, MAX_SELECTION_DAILY_TURNOVER)
            threeMonthAverageDailyValueTraded = marketCap * currentTurnover
            val priorMedianTurnover = threeMonthMedianDailyValueTraded / priorMarketCap
            val currentMedianTurnover = (
                priorMedianTurnover * exp(medianLiquidityRandom.nextGaussian() * 0.08)
                ).coerceIn(MIN_SELECTION_DAILY_TURNOVER, MAX_SELECTION_DAILY_TURNOVER)
            threeMonthMedianDailyValueTraded = marketCap * currentMedianTurnover
            val priorMorningstarTurnover =
                trailing125TradingDayAverageDailyValueTraded / priorMarketCap
            val currentMorningstarTurnover = (
                priorMorningstarTurnover *
                    exp(morningstarLiquidityRandom.nextGaussian() * 0.065)
                ).coerceIn(MIN_SELECTION_DAILY_TURNOVER, MAX_SELECTION_DAILY_TURNOVER)
            trailing125TradingDayAverageDailyValueTraded =
                marketCap * currentMorningstarTurnover
            val priorTwelveMonthTurnover = twelveMonthAverageDailyValueTraded / priorMarketCap
            val currentTwelveMonthTurnover = (
                priorTwelveMonthTurnover * exp(sp500SignalRandom.nextGaussian() * 0.055)
                ).coerceIn(MIN_SELECTION_DAILY_TURNOVER, MAX_SELECTION_DAILY_TURNOVER)
            twelveMonthAverageDailyValueTraded = marketCap * currentTwelveMonthTurnover

            val cashFlowFromOperationsRatio = cashFlowFromOperations / priorMarketCap
            cashFlowFromOperations = marketCap * (
                cashFlowFromOperationsRatio + random.nextGaussian() * 0.018
                ).coerceIn(MIN_CASH_FLOW_FROM_OPERATIONS_RATIO, MAX_CASH_FLOW_FROM_OPERATIONS_RATIO)
            val capitalExpenditureRatio = capitalExpenditures / priorMarketCap
            capitalExpenditures = marketCap * (
                capitalExpenditureRatio * exp(random.nextGaussian() * 0.14)
                ).coerceIn(MIN_CAPITAL_EXPENDITURE_RATIO, MAX_CAPITAL_EXPENDITURE_RATIO)
            totalDebt = if (totalDebt == 0.0) {
                if (random.nextBoolean(NEW_DEBT_PROBABILITY)) {
                    marketCap * random.nextDouble(MIN_TOTAL_DEBT_RATIO, NEW_TOTAL_DEBT_RATIO_LIMIT)
                } else {
                    0.0
                }
            } else if (random.nextBoolean(
                    (0.010 + equity.quality * 0.018).coerceIn(0.010, 0.028),
                )
            ) {
                0.0
            } else {
                val debtRatio = totalDebt / priorMarketCap
                marketCap * (debtRatio * exp(random.nextGaussian() * 0.16))
                    .coerceIn(MIN_TOTAL_DEBT_RATIO, MAX_TOTAL_DEBT_RATIO)
            }

            sharePrice = (sharePrice * exp(growth + random.nextGaussian() * 0.045))
                .coerceIn(MIN_SHARE_PRICE, maxSharePrice)
            val bookMagnitude = (abs(bookValuePerShare) * exp(random.nextGaussian() * 0.11))
                .coerceIn(MIN_ABSOLUTE_BOOK_VALUE_PER_SHARE, maxBookValuePerShare)
            val remainsNegative = if (bookValuePerShare < 0.0) {
                !random.nextBoolean((0.12 + equity.quality * 0.20).coerceIn(0.12, 0.32))
            } else {
                random.nextBoolean((0.022 - equity.quality * 0.018).coerceIn(0.002, 0.021))
            }
            bookValuePerShare = if (remainsNegative) -bookMagnitude else bookMagnitude
            basicEarningsPerShare = (
                basicEarningsPerShare +
                    sharePrice * ((equity.quality - 0.5) * 0.010 + random.nextGaussian() * 0.018)
                ).coerceIn(minEarningsPerShare, maxAbsoluteEarningsPerShare)
            minimumSixMonthMonthlyShareVolume = (
                minimumSixMonthMonthlyShareVolume *
                    (marketCap / priorMarketCap) *
                    (priorInvestableWeightFactor / investableWeightFactor) *
                    (priorSharePrice / sharePrice) *
                    exp(sp500SignalRandom.nextGaussian() * 0.08)
                ).coerceIn(0.0, MAX_MONTHLY_SHARE_VOLUME)
            val totalCompanyMarketCap = marketCap / investableWeightFactor
            val trailingProfitability = basicEarningsPerShare / sharePrice
            trailingFourQuarterGaapNetIncome = totalCompanyMarketCap * trailingProfitability
            latestQuarterGaapNetIncome = totalCompanyMarketCap *
                (trailingProfitability / 4.0 +
                    sp500SignalRandom.nextGaussian() * QUARTERLY_PROFITABILITY_NOISE)

            val dividendEvents = (1..12).associateWith { month ->
                dividendMaintenanceEvent(equity, candidateYear, month)
            }
            val ceased = dividendEvents.values.any { it == DIVIDEND_EVENT_CEASED_INDEFINITELY }
            val omittedPaymentCount = dividendEvents.values.count { it == DIVIDEND_EVENT_OMITTED }
            val noRegularPaymentMade = omittedPaymentCount == equity.dividendPaymentsPerYear
            dividendYears = if (ceased || noRegularPaymentMade) 0 else dividendYears + 1
            val dividendCut = random.nextBoolean((0.050 - equity.quality * 0.038).coerceIn(0.008, 0.045))
            val regularDividendFactor = if (ceased) {
                random.nextDouble(0.02, 0.20)
            } else if (dividendCut || dividendEvents.values.any { it == DIVIDEND_EVENT_REDUCED }) {
                random.nextDouble(0.55, 0.90)
            } else {
                exp(random.nextGaussian() * 0.10)
            }
            regularFixedAnnualDividendPerShare = (
                regularFixedAnnualDividendPerShare * regularDividendFactor
                ).coerceIn(MIN_REGULAR_DIVIDEND_PER_SHARE, maxRegularDividendPerShare)
            val paidFraction = if (ceased) {
                SUSPENDED_DIVIDEND_CASH_FACTOR
            } else {
                (equity.dividendPaymentsPerYear - omittedPaymentCount).coerceAtLeast(0).toDouble() /
                    equity.dividendPaymentsPerYear
            }
            val annualRegularDividendPerShare =
                regularFixedAnnualDividendPerShare * paidFraction
            annualRegularDividendPerShareNewestFirst = buildList(DIVIDEND_HISTORY_YEARS) {
                add(annualRegularDividendPerShare)
                addAll(
                    annualRegularDividendPerShareNewestFirst.take(DIVIDEND_HISTORY_YEARS - 1),
                )
            }
            listingAgeYears += 1
            if (equity.referenceUniverse == FundReferenceUniverse.KOREA_BROAD_EQUITY &&
                equity.methodologySector == MethodologyEquitySector.FINANCIALS &&
                supplementalSignalRandom.nextBoolean(0.035)
            ) {
                kospi200FinancialMember = !kospi200FinancialMember
            }
            threeYearAverageDividendPayoutRatio = (
                threeYearAverageDividendPayoutRatio + supplementalSignalRandom.nextGaussian() * 0.035
                ).coerceIn(0.0, 1.50)
            threeYearAverageReturnOnEquity = (
                threeYearAverageReturnOnEquity + supplementalSignalRandom.nextGaussian() * 0.018
                ).coerceIn(-0.60, 0.80)
            val priorOneMonthTurnover = oneMonthAverageDailyValueTraded / priorMarketCap
            oneMonthAverageDailyValueTraded = marketCap * (
                priorOneMonthTurnover * exp(supplementalSignalRandom.nextGaussian() * 0.10)
                ).coerceIn(0.00005, 0.08)
            oneMonthAveragePriceToBookRatio = if (bookValuePerShare > 0.0) {
                (sharePrice / bookValuePerShare *
                    exp(supplementalSignalRandom.nextGaussian() * 0.025)).coerceIn(0.01, 30.0)
            } else {
                30.0
            }
            oneMonthAverageDividendYield = (
                regularFixedAnnualDividendPerShare / sharePrice *
                    exp(supplementalSignalRandom.nextGaussian() * 0.015)
                ).coerceIn(0.0, 1.0)
            val currentTotalCompanyMarketCap = marketCap / investableWeightFactor
            oneMonthAverageMarketCap = (
                currentTotalCompanyMarketCap * exp(supplementalSignalRandom.nextGaussian() * 0.018)
                ).coerceIn(MIN_REFERENCE_MARKET_CAP, MAX_REFERENCE_MARKET_CAP)
            trailingFourQuarterTotalCashDividends = (
                currentTotalCompanyMarketCap / sharePrice * regularFixedAnnualDividendPerShare *
                    exp(supplementalSignalRandom.nextGaussian() * 0.025)
                ).coerceAtLeast(MIN_REFERENCE_MARKET_CAP)
            salesPerShare = (salesPerShare * exp(
                growth + morningstarSignalRandom.nextGaussian() * 0.08,
            )).coerceIn(MIN_STYLE_SALES_PER_SHARE, MAX_STYLE_SALES_PER_SHARE)
            futureEarningsPerShare = (
                basicEarningsPerShare + sharePrice * (
                    (equity.quality - 0.5) * 0.012 +
                        morningstarSignalRandom.nextGaussian() * 0.012
                    )
                ).coerceIn(-maxAbsoluteEarningsPerShare, maxAbsoluteEarningsPerShare)
            futureLongTermEarningsGrowth = (
                futureLongTermEarningsGrowth * 0.72 +
                    (equity.quality - 0.5) * 0.08 +
                    morningstarSignalRandom.nextGaussian() * 0.055
                ).coerceIn(MIN_STYLE_GROWTH, MAX_STYLE_GROWTH)
            futureShortTermEarningsGrowth = (
                futureShortTermEarningsGrowth * 0.62 +
                    growth * 0.25 + morningstarSignalRandom.nextGaussian() * 0.075
                ).coerceIn(MIN_STYLE_GROWTH, MAX_STYLE_GROWTH)
            threeYearHistoricalEarningsGrowth = (
                threeYearHistoricalEarningsGrowth * 0.70 + growth * 0.30 +
                    morningstarSignalRandom.nextGaussian() * 0.05
                ).coerceIn(MIN_STYLE_GROWTH, MAX_STYLE_GROWTH)
            threeYearHistoricalSalesGrowth = (
                threeYearHistoricalSalesGrowth * 0.78 + growth * 0.22 +
                    morningstarSignalRandom.nextGaussian() * 0.035
                ).coerceIn(MIN_STYLE_GROWTH, MAX_STYLE_GROWTH)
            currentInvestmentToAssets = (
                currentInvestmentToAssets * 0.75 +
                    morningstarSignalRandom.nextGaussian() * 0.04
                ).coerceIn(MIN_STYLE_INVESTMENT_TO_ASSETS, MAX_STYLE_INVESTMENT_TO_ASSETS)
            returnOnAssets = (
                returnOnAssets * 0.72 + (basicEarningsPerShare / sharePrice) * 0.28 +
                    morningstarSignalRandom.nextGaussian() * 0.025
                ).coerceIn(MIN_STYLE_RETURN_ON_ASSETS, MAX_STYLE_RETURN_ON_ASSETS)
        }
        return SimulatedReferenceEquitySnapshot(
            definition = equity,
            floatMarketCap = marketCap,
            investableWeightFactor = investableWeightFactor,
            threeMonthAverageDailyValueTraded = threeMonthAverageDailyValueTraded,
            threeMonthMedianDailyValueTraded = threeMonthMedianDailyValueTraded,
            trailing125TradingDayAverageDailyValueTraded =
                trailing125TradingDayAverageDailyValueTraded,
            twelveMonthAverageDailyValueTraded = twelveMonthAverageDailyValueTraded,
            minimumSixMonthMonthlyShareVolume = minimumSixMonthMonthlyShareVolume,
            latestQuarterGaapNetIncome = latestQuarterGaapNetIncome,
            trailingFourQuarterGaapNetIncome = trailingFourQuarterGaapNetIncome,
            dividendPaymentYears = dividendYears,
            cashFlowFromOperations = cashFlowFromOperations,
            capitalExpenditures = capitalExpenditures,
            totalDebt = totalDebt,
            basicEarningsPerShare = basicEarningsPerShare,
            bookValuePerShare = bookValuePerShare,
            sharePrice = sharePrice,
            regularFixedAnnualDividendPerShare = regularFixedAnnualDividendPerShare,
            annualRegularDividendPerShareNewestFirst =
                annualRegularDividendPerShareNewestFirst,
            listingAgeYears = listingAgeYears,
            kospi200FinancialMember = kospi200FinancialMember,
            threeYearAverageDividendPayoutRatio = threeYearAverageDividendPayoutRatio,
            threeYearAverageReturnOnEquity = threeYearAverageReturnOnEquity,
            oneMonthAverageDailyValueTraded = oneMonthAverageDailyValueTraded,
            oneMonthAveragePriceToBookRatio = oneMonthAveragePriceToBookRatio,
            oneMonthAverageDividendYield = oneMonthAverageDividendYield,
            oneMonthAverageMarketCap = oneMonthAverageMarketCap,
            trailingFourQuarterTotalCashDividends = trailingFourQuarterTotalCashDividends,
            futureEarningsPerShare = futureEarningsPerShare,
            salesPerShare = salesPerShare,
            futureLongTermEarningsGrowth = futureLongTermEarningsGrowth,
            futureShortTermEarningsGrowth = futureShortTermEarningsGrowth,
            threeYearHistoricalEarningsGrowth = threeYearHistoricalEarningsGrowth,
            threeYearHistoricalSalesGrowth = threeYearHistoricalSalesGrowth,
            currentInvestmentToAssets = currentInvestmentToAssets,
            returnOnAssets = returnOnAssets,
        )
    }

    private fun dividendMaintenanceEvent(
        equity: SimulatedReferenceEquity,
        year: Int,
        month: Int,
    ): Int {
        if (!isScheduledDividendMonth(equity, month)) return DIVIDEND_EVENT_NONE
        val probability = (0.0040 - equity.quality * 0.0028).coerceIn(0.0008, 0.0038)
        val random = DeterministicRandom.keyed(
            seed,
            "fund-dividend-maintenance:${equity.assetId}:$year:$month",
        )
        if (!random.nextBoolean(probability)) return DIVIDEND_EVENT_NONE
        return when (random.nextInt(100)) {
            in 0..24 -> DIVIDEND_EVENT_CEASED_INDEFINITELY
            in 25..54 -> DIVIDEND_EVENT_OMITTED
            in 55..79 -> DIVIDEND_EVENT_POSTPONED_OR_DEFERRED
            else -> DIVIDEND_EVENT_REDUCED
        }
    }

    private fun isScheduledDividendMonth(equity: SimulatedReferenceEquity, month: Int): Boolean {
        require(month in 1..12)
        val cadence = 12 / equity.dividendPaymentsPerYear
        return (month - equity.firstDividendPaymentMonth).mod(cadence) == 0
    }

    private fun dividendEventAnnouncementDay(
        equity: SimulatedReferenceEquity,
        year: Int,
        month: Int,
    ): Int = DeterministicRandom.keyed(
        seed,
        "fund-dividend-announcement-day:${equity.assetId}:$year:$month",
    ).nextInt(28) + 1

    /**
     * Approach C observes announcements after the prior cutoff through this month's cutoff.
     * Postponements, deferrals and reductions are deliberately generated but do not set either
     * removal signal exposed to the provider.
     */
    private fun hasDividendEventInCurrentReviewWindow(
        equity: SimulatedReferenceEquity,
        observationDate: LocalDate,
        requiredEvent: Int,
    ): Boolean {
        val year = observationDate.year
        val month = observationDate.month.ordinal + 1
        val cutoffDay = if (month == 2) FEBRUARY_DIVIDEND_REVIEW_CUTOFF_DAY else DIVIDEND_REVIEW_CUTOFF_DAY
        val previousMonth = if (month == 1) 12 else month - 1
        val previousYear = if (month == 1) year - 1 else year
        val previousCutoffDay = if (previousMonth == 2) {
            FEBRUARY_DIVIDEND_REVIEW_CUTOFF_DAY
        } else {
            DIVIDEND_REVIEW_CUTOFF_DAY
        }
        return (
            dividendMaintenanceEvent(equity, year, month) == requiredEvent &&
                dividendEventAnnouncementDay(equity, year, month) <= cutoffDay
            ) || (
            dividendMaintenanceEvent(equity, previousYear, previousMonth) == requiredEvent &&
                dividendEventAnnouncementDay(equity, previousYear, previousMonth) > previousCutoffDay
            )
    }

    private fun buildReferenceEquities(): List<SimulatedReferenceEquity> {
        val equities = (1..REFERENCE_EQUITY_COUNT).map { index ->
            val paddedIndex = index.toString().padStart(4, '0')
            val companyId = "SIM:US-COMPANY:$paddedIndex"
            val assetId = "REF:US-BROAD:$paddedIndex"
            val random = DeterministicRandom.keyed(seed, "fund-reference-definition:$assetId")
            val methodologySector = REFERENCE_SECTORS[random.nextInt(REFERENCE_SECTORS.size)]
            val gicsClassificationCode = methodologySector.simulatedGicsClassificationCode(random)
            val quality = random.nextDouble(0.05, 0.98)
            val value = random.nextDouble(0.05, 0.98)
            val logCap = ln(MIN_BASE_MARKET_CAP) +
                random.nextDouble() * ln(MAX_BASE_MARKET_CAP / MIN_BASE_MARKET_CAP)
            val baseFloatMarketCap = exp(logCap)
            val baseSharePrice = random.nextDouble(MIN_BASE_SHARE_PRICE, MAX_BASE_SHARE_PRICE)
            val baseRegularFixedAnnualDividendPerShare =
                baseSharePrice * random.nextDouble(0.004, 0.085)
            val baseAnnualRegularDividendPerShareNewestFirst = buildBaseRegularDividendHistory(
                random = random,
                currentDividendPerShare = baseRegularFixedAnnualDividendPerShare *
                    random.nextDouble(0.92, 1.05),
            )
            val baseTotalDebt = if (random.nextBoolean(BASE_ZERO_TOTAL_DEBT_PROBABILITY)) {
                0.0
            } else {
                baseFloatMarketCap * random.nextDouble(MIN_TOTAL_DEBT_RATIO, 1.25)
            }
            val negativeBookValuePerShare = random.nextBoolean(
                (0.035 - quality * 0.025).coerceIn(0.005, 0.032),
            )
            val baseBookMagnitude = random.nextDouble(
                MIN_BASE_BOOK_VALUE_PER_SHARE,
                MAX_BASE_BOOK_VALUE_PER_SHARE,
            )
            val baseBookValuePerShare = if (negativeBookValuePerShare) {
                -baseBookMagnitude
            } else {
                baseBookMagnitude
            }
            val baseReturnOnEquity = if (random.nextBoolean(BASE_NET_LOSS_PROBABILITY)) {
                -random.nextDouble(0.01, 0.25)
            } else {
                random.nextDouble(0.03, 0.42)
            }
            val baseBasicEarningsPerShare = baseBookValuePerShare * baseReturnOnEquity
            // S&P-specific eligibility inputs use a separate stream so adding them cannot rewrite
            // the already-versioned SCHD synthetic universe characteristics.
            val sp500SignalRandom = DeterministicRandom.keyed(
                seed,
                "fund-reference-sp500-signals:$assetId",
            )
            val baseInvestableWeightFactor = sp500SignalRandom.nextDouble(
                MIN_INVESTABLE_WEIGHT_FACTOR,
                MAX_INVESTABLE_WEIGHT_FACTOR,
            )
            val baseTwelveMonthAverageDailyValueTraded = baseFloatMarketCap *
                sp500SignalRandom.nextDouble(0.001, 0.025)
            val baseMinimumSixMonthMonthlyShareVolume =
                baseTwelveMonthAverageDailyValueTraded / baseSharePrice *
                    sp500SignalRandom.nextDouble(12.0, 21.0)
            val baseTotalCompanyMarketCap = baseFloatMarketCap / baseInvestableWeightFactor
            val baseTrailingFourQuarterGaapNetIncome = baseTotalCompanyMarketCap *
                (baseBasicEarningsPerShare / baseSharePrice)
            val baseLatestQuarterGaapNetIncome = baseTotalCompanyMarketCap *
                (baseBasicEarningsPerShare / baseSharePrice / 4.0 +
                    sp500SignalRandom.nextGaussian() * QUARTERLY_PROFITABILITY_NOISE)
            val supplementalSignalRandom = DeterministicRandom.keyed(
                seed,
                "fund-reference-supplemental-signals:$assetId",
            )
            val baseThreeYearAverageReturnOnEquity = baseReturnOnEquity +
                supplementalSignalRandom.nextGaussian() * 0.015
            val positiveEarningsPerShare = baseBasicEarningsPerShare.coerceAtLeast(
                baseRegularFixedAnnualDividendPerShare / 0.90,
            )
            val baseThreeYearAverageDividendPayoutRatio =
                (baseRegularFixedAnnualDividendPerShare / positiveEarningsPerShare)
                    .coerceIn(0.02, 1.20)
            val baseOneMonthAveragePriceToBookRatio = if (baseBookValuePerShare > 0.0) {
                (baseSharePrice / baseBookValuePerShare).coerceIn(0.05, 20.0)
            } else {
                20.0
            }
            val baseTrailingFourQuarterTotalCashDividends =
                baseTotalCompanyMarketCap / baseSharePrice * baseRegularFixedAnnualDividendPerShare
            val morningstarSignalRandom = DeterministicRandom.keyed(
                seed,
                "fund-reference-morningstar-style-signals:$assetId",
            )
            val baseFutureEarningsPerShare = (
                baseBasicEarningsPerShare + baseSharePrice * (
                    (quality - 0.5) * 0.015 + morningstarSignalRandom.nextGaussian() * 0.012
                    )
                ).coerceIn(MIN_BASIC_EARNINGS_PER_SHARE, MAX_BASIC_EARNINGS_PER_SHARE)
            val baseSalesPerShare = (
                baseSharePrice * morningstarSignalRandom.nextDouble(0.20, 3.50)
                ).coerceIn(MIN_STYLE_SALES_PER_SHARE, MAX_STYLE_SALES_PER_SHARE)
            val morningstarLiquidityRandom = DeterministicRandom.keyed(
                seed,
                "fund-reference-morningstar-125-day-advt:$assetId",
            )
            val medianLiquidityRandom = DeterministicRandom.keyed(
                seed,
                "fund-reference-three-month-mdvt:$assetId",
            )
            SimulatedReferenceEquity(
                referenceUniverse = FundReferenceUniverse.US_BROAD_EQUITY,
                companyId = companyId,
                assetId = assetId,
                displaySymbol = "SIM$paddedIndex",
                displayName = "시뮬레이션 미국 기준자산 $paddedIndex",
                sector = methodologySector.toGameSector(),
                methodologySector = methodologySector,
                gicsClassificationCode = gicsClassificationCode,
                baseFloatMarketCap = baseFloatMarketCap,
                baseInvestableWeightFactor = baseInvestableWeightFactor,
                baseThreeMonthAverageDailyValueTraded =
                    baseFloatMarketCap * random.nextDouble(0.001, 0.025),
                baseThreeMonthMedianDailyValueTraded =
                    baseFloatMarketCap * medianLiquidityRandom.nextDouble(0.0008, 0.023),
                baseTrailing125TradingDayAverageDailyValueTraded =
                    baseFloatMarketCap * morningstarLiquidityRandom.nextDouble(0.001, 0.025),
                baseTwelveMonthAverageDailyValueTraded =
                    baseTwelveMonthAverageDailyValueTraded,
                baseMinimumSixMonthMonthlyShareVolume =
                    baseMinimumSixMonthMonthlyShareVolume,
                baseLatestQuarterGaapNetIncome = baseLatestQuarterGaapNetIncome,
                baseTrailingFourQuarterGaapNetIncome = baseTrailingFourQuarterGaapNetIncome,
                baseDividendPaymentYears = random.nextInt(38) + 2,
                dividendPaymentsPerYear = when (random.nextInt(10)) {
                    0 -> 1
                    1 -> 2
                    else -> 4
                },
                firstDividendPaymentMonth = 1,
                baseCashFlowFromOperations = baseFloatMarketCap *
                    random.nextDouble(
                        MIN_BASE_CASH_FLOW_FROM_OPERATIONS_RATIO,
                        MAX_BASE_CASH_FLOW_FROM_OPERATIONS_RATIO,
                    ),
                baseCapitalExpenditures = baseFloatMarketCap *
                    random.nextDouble(
                        MIN_BASE_CAPITAL_EXPENDITURE_RATIO,
                        MAX_BASE_CAPITAL_EXPENDITURE_RATIO,
                    ),
                baseTotalDebt = baseTotalDebt,
                baseBasicEarningsPerShare = baseBasicEarningsPerShare,
                baseBookValuePerShare = baseBookValuePerShare,
                baseSharePrice = baseSharePrice,
                baseRegularFixedAnnualDividendPerShare =
                    baseRegularFixedAnnualDividendPerShare,
                baseAnnualRegularDividendPerShareNewestFirst =
                    baseAnnualRegularDividendPerShareNewestFirst,
                baseListingAgeYears = supplementalSignalRandom.nextInt(60) + 1,
                baseKospi200FinancialMember = false,
                baseThreeYearAverageDividendPayoutRatio =
                    baseThreeYearAverageDividendPayoutRatio,
                baseThreeYearAverageReturnOnEquity = baseThreeYearAverageReturnOnEquity,
                baseOneMonthAverageDailyValueTraded =
                    baseFloatMarketCap * supplementalSignalRandom.nextDouble(0.0005, 0.035),
                baseOneMonthAveragePriceToBookRatio = baseOneMonthAveragePriceToBookRatio,
                baseOneMonthAverageDividendYield =
                    baseRegularFixedAnnualDividendPerShare / baseSharePrice,
                baseOneMonthAverageMarketCap = baseTotalCompanyMarketCap,
                baseTrailingFourQuarterTotalCashDividends =
                    baseTrailingFourQuarterTotalCashDividends,
                baseFutureEarningsPerShare = baseFutureEarningsPerShare,
                baseSalesPerShare = baseSalesPerShare,
                baseFutureLongTermEarningsGrowth =
                    morningstarSignalRandom.nextDouble(-0.10, 0.35),
                baseFutureShortTermEarningsGrowth =
                    morningstarSignalRandom.nextDouble(-0.18, 0.45),
                baseThreeYearHistoricalEarningsGrowth =
                    morningstarSignalRandom.nextDouble(-0.22, 0.48),
                baseThreeYearHistoricalSalesGrowth =
                    morningstarSignalRandom.nextDouble(-0.15, 0.32),
                baseCurrentInvestmentToAssets =
                    morningstarSignalRandom.nextDouble(-0.15, 0.30),
                baseReturnOnAssets = morningstarSignalRandom.nextDouble(-0.12, 0.28),
                beta = random.nextDouble(0.55, 1.45),
                annualVolatility = random.nextDouble(0.11, 0.38),
                quality = quality,
                value = value,
            )
        }
        require(equities.map(SimulatedReferenceEquity::companyId).distinct().size == equities.size) {
            "Each synthetic company must have exactly one reference listing."
        }
        require(equities.map(SimulatedReferenceEquity::assetId).distinct().size == equities.size) {
            "Each synthetic reference listing must have a unique asset ID."
        }
        return equities
    }

    /**
     * 한국 실행형 방법론 전용 비거래 후보군이다. 미국 후보군과 RNG·ID를 완전히 분리해
     * 한국 지수를 추가해도 기존 SCHD/S&P 500 경로가 바뀌지 않게 한다.
     */
    private fun buildKoreanReferenceEquities(): List<SimulatedReferenceEquity> {
        val equities = (1..KOREA_REFERENCE_EQUITY_COUNT).map { index ->
            val paddedIndex = index.toString().padStart(4, '0')
            val companyId = "SIM:KR-COMPANY:$paddedIndex"
            val assetId = "REF:KR-BROAD:$paddedIndex"
            val random = DeterministicRandom.keyed(seed, "fund-korea-reference-definition:$assetId")
            val methodologySector = KOREA_REFERENCE_SECTORS[
                random.nextInt(KOREA_REFERENCE_SECTORS.size)
            ]
            val quality = random.nextDouble(0.05, 0.98)
            val value = random.nextDouble(0.05, 0.98)
            val logCap = ln(MIN_KOREA_BASE_MARKET_CAP) +
                random.nextDouble() * ln(MAX_KOREA_BASE_MARKET_CAP / MIN_KOREA_BASE_MARKET_CAP)
            val baseFloatMarketCap = exp(logCap)
            val baseInvestableWeightFactor = random.nextDouble(0.10, 1.0)
            val baseTotalCompanyMarketCap = baseFloatMarketCap / baseInvestableWeightFactor
            val baseSharePrice = random.nextDouble(MIN_KOREA_BASE_SHARE_PRICE, MAX_KOREA_BASE_SHARE_PRICE)
            val pbr = if (random.nextBoolean(0.025)) {
                -random.nextDouble(0.05, 2.0)
            } else if (methodologySector == MethodologyEquitySector.FINANCIALS) {
                // Korean financial companies structurally trade at lower P/B multiples than the
                // broad market. Keep the synthetic KOSPI 200 Financial sleeve deep enough for the
                // official strict PBR screen without weakening the provider's eligibility rule.
                random.nextDouble(0.20, 1.10)
            } else {
                random.nextDouble(0.20, 3.50)
            }
            val baseBookValuePerShare = if (pbr < 0.0) {
                -baseSharePrice / -pbr
            } else {
                baseSharePrice / pbr
            }
            val baseThreeYearAverageReturnOnEquity = if (random.nextBoolean(0.06)) {
                -random.nextDouble(0.01, 0.18)
            } else {
                random.nextDouble(0.025, 0.32)
            }
            val baseBasicEarningsPerShare = baseBookValuePerShare *
                baseThreeYearAverageReturnOnEquity
            val baseThreeYearAverageDividendPayoutRatio =
                if (methodologySector == MethodologyEquitySector.FINANCIALS) {
                    random.nextDouble(0.14, 0.62)
                } else {
                    random.nextDouble(0.04, 0.88)
                }
            val rawAnnualDividendPerShare = if (baseBasicEarningsPerShare > 0.0) {
                baseBasicEarningsPerShare * baseThreeYearAverageDividendPayoutRatio
            } else {
                baseSharePrice * random.nextDouble(0.001, 0.012)
            }
            val baseRegularFixedAnnualDividendPerShare = rawAnnualDividendPerShare.coerceIn(
                baseSharePrice * MIN_INDICATED_DIVIDEND_YIELD,
                baseSharePrice * MAX_INDICATED_DIVIDEND_YIELD,
            )
            val baseAnnualRegularDividendPerShareNewestFirst = buildBaseRegularDividendHistory(
                random = random,
                currentDividendPerShare = baseRegularFixedAnnualDividendPerShare *
                    random.nextDouble(0.94, 1.04),
                maxDividendPerShare = MAX_KOREA_REGULAR_DIVIDEND_PER_SHARE,
            )
            val baseOneMonthAverageMarketCap = baseTotalCompanyMarketCap *
                random.nextDouble(0.97, 1.03)
            val baseOneMonthAverageDailyValueTraded = if (
                methodologySector == MethodologyEquitySector.FINANCIALS &&
                baseTotalCompanyMarketCap >= 1_000_000_000_000.0
            ) {
                random.nextDouble(4_500_000_000.0, 80_000_000_000.0)
            } else {
                (baseFloatMarketCap * random.nextDouble(0.00015, 0.025))
                    .coerceAtLeast(100_000_000.0)
            }
            val baseThreeMonthAverageDailyValueTraded = baseOneMonthAverageDailyValueTraded *
                random.nextDouble(0.80, 1.20)
            val baseTwelveMonthAverageDailyValueTraded = baseThreeMonthAverageDailyValueTraded *
                random.nextDouble(0.78, 1.18)
            val baseMinimumSixMonthMonthlyShareVolume =
                baseTwelveMonthAverageDailyValueTraded / baseSharePrice *
                    random.nextDouble(12.0, 21.0)
            val baseTotalDebt = if (random.nextBoolean(BASE_ZERO_TOTAL_DEBT_PROBABILITY)) {
                0.0
            } else {
                baseFloatMarketCap * random.nextDouble(MIN_TOTAL_DEBT_RATIO, 1.60)
            }
            val baseTrailingFourQuarterTotalCashDividends =
                baseTotalCompanyMarketCap / baseSharePrice * baseRegularFixedAnnualDividendPerShare
            val baseTrailingFourQuarterGaapNetIncome = baseTotalCompanyMarketCap *
                (baseBasicEarningsPerShare / baseSharePrice)
            val baseLatestQuarterGaapNetIncome = baseTrailingFourQuarterGaapNetIncome / 4.0 +
                baseTotalCompanyMarketCap * random.nextGaussian() * QUARTERLY_PROFITABILITY_NOISE
            val isKospi200FinancialMember =
                methodologySector == MethodologyEquitySector.FINANCIALS &&
                    baseTotalCompanyMarketCap >= 800_000_000_000.0 &&
                    random.nextBoolean(0.82)
            val morningstarSignalRandom = DeterministicRandom.keyed(
                seed,
                "fund-reference-morningstar-style-signals:$assetId",
            )
            val baseFutureEarningsPerShare = (
                baseBasicEarningsPerShare + baseSharePrice * (
                    (quality - 0.5) * 0.015 + morningstarSignalRandom.nextGaussian() * 0.012
                    )
                ).coerceIn(
                -MAX_KOREA_ABSOLUTE_EARNINGS_PER_SHARE,
                MAX_KOREA_ABSOLUTE_EARNINGS_PER_SHARE,
            )
            val morningstarLiquidityRandom = DeterministicRandom.keyed(
                seed,
                "fund-reference-morningstar-125-day-advt:$assetId",
            )
            val medianLiquidityRandom = DeterministicRandom.keyed(
                seed,
                "fund-reference-three-month-mdvt:$assetId",
            )
            SimulatedReferenceEquity(
                referenceUniverse = FundReferenceUniverse.KOREA_BROAD_EQUITY,
                companyId = companyId,
                assetId = assetId,
                displaySymbol = "KRSIM$paddedIndex",
                displayName = "시뮬레이션 한국 기준자산 $paddedIndex",
                sector = methodologySector.toGameSector(),
                methodologySector = methodologySector,
                gicsClassificationCode = methodologySector.simulatedGicsClassificationCode(random),
                baseFloatMarketCap = baseFloatMarketCap,
                baseInvestableWeightFactor = baseInvestableWeightFactor,
                baseThreeMonthAverageDailyValueTraded = baseThreeMonthAverageDailyValueTraded,
                baseThreeMonthMedianDailyValueTraded =
                    baseFloatMarketCap * medianLiquidityRandom.nextDouble(0.0008, 0.023),
                baseTrailing125TradingDayAverageDailyValueTraded =
                    baseFloatMarketCap * morningstarLiquidityRandom.nextDouble(0.001, 0.025),
                baseTwelveMonthAverageDailyValueTraded = baseTwelveMonthAverageDailyValueTraded,
                baseMinimumSixMonthMonthlyShareVolume = baseMinimumSixMonthMonthlyShareVolume,
                baseLatestQuarterGaapNetIncome = baseLatestQuarterGaapNetIncome,
                baseTrailingFourQuarterGaapNetIncome = baseTrailingFourQuarterGaapNetIncome,
                baseDividendPaymentYears = random.nextInt(39) + 2,
                dividendPaymentsPerYear = when (random.nextInt(10)) {
                    in 0..3 -> 1
                    in 4..5 -> 2
                    else -> 4
                },
                firstDividendPaymentMonth = 3,
                baseCashFlowFromOperations = baseFloatMarketCap * random.nextDouble(-0.01, 0.22),
                baseCapitalExpenditures = baseFloatMarketCap * random.nextDouble(0.002, 0.05),
                baseTotalDebt = baseTotalDebt,
                baseBasicEarningsPerShare = baseBasicEarningsPerShare,
                baseBookValuePerShare = baseBookValuePerShare,
                baseSharePrice = baseSharePrice,
                baseRegularFixedAnnualDividendPerShare = baseRegularFixedAnnualDividendPerShare,
                baseAnnualRegularDividendPerShareNewestFirst =
                    baseAnnualRegularDividendPerShareNewestFirst,
                baseListingAgeYears = random.nextInt(50) + 1,
                baseKospi200FinancialMember = isKospi200FinancialMember,
                baseThreeYearAverageDividendPayoutRatio =
                    baseThreeYearAverageDividendPayoutRatio,
                baseThreeYearAverageReturnOnEquity = baseThreeYearAverageReturnOnEquity,
                baseOneMonthAverageDailyValueTraded = baseOneMonthAverageDailyValueTraded,
                baseOneMonthAveragePriceToBookRatio = pbr.coerceAtLeast(0.0),
                baseOneMonthAverageDividendYield =
                    baseRegularFixedAnnualDividendPerShare / baseSharePrice,
                baseOneMonthAverageMarketCap = baseOneMonthAverageMarketCap,
                baseTrailingFourQuarterTotalCashDividends =
                    baseTrailingFourQuarterTotalCashDividends,
                baseFutureEarningsPerShare = baseFutureEarningsPerShare,
                baseSalesPerShare = (
                    baseSharePrice * morningstarSignalRandom.nextDouble(0.20, 3.50)
                    ).coerceIn(MIN_STYLE_SALES_PER_SHARE, MAX_STYLE_SALES_PER_SHARE),
                baseFutureLongTermEarningsGrowth =
                    morningstarSignalRandom.nextDouble(-0.10, 0.35),
                baseFutureShortTermEarningsGrowth =
                    morningstarSignalRandom.nextDouble(-0.18, 0.45),
                baseThreeYearHistoricalEarningsGrowth =
                    morningstarSignalRandom.nextDouble(-0.22, 0.48),
                baseThreeYearHistoricalSalesGrowth =
                    morningstarSignalRandom.nextDouble(-0.15, 0.32),
                baseCurrentInvestmentToAssets =
                    morningstarSignalRandom.nextDouble(-0.15, 0.30),
                baseReturnOnAssets = morningstarSignalRandom.nextDouble(-0.12, 0.28),
                beta = random.nextDouble(0.55, 1.45),
                annualVolatility = random.nextDouble(0.14, 0.48),
                quality = quality,
                value = value,
            )
        }
        require(equities.map(SimulatedReferenceEquity::companyId).distinct().size == equities.size)
        require(equities.map(SimulatedReferenceEquity::assetId).distinct().size == equities.size)
        return equities
    }

    /**
     * Temporary spin-off lines live outside the ordinary selection universe. Giving every
     * possible announcement date its own identity prevents a future synthetic event from
     * reserving an otherwise eligible company in an earlier annual review.
     */
    private fun buildSpinOffReferenceEquities(
        ordinaryEquities: List<SimulatedReferenceEquity>,
    ): List<SimulatedReferenceEquity> = buildList {
        require(ordinaryEquities.isNotEmpty())
        val universe = ordinaryEquities.first().referenceUniverse
        require(ordinaryEquities.all { equity -> equity.referenceUniverse == universe })
        var date = LocalDate(REFERENCE_BASE_YEAR + 1, 1, 1)
        while (date < GameCalendar.CAMPAIGN_END_DATE) {
            val random = DeterministicRandom.keyed(seed, "fund-reference-spin-child-definition:$date")
            val source = ordinaryEquities[random.nextInt(ordinaryEquities.size)]
            val dateToken = date.toString().replace("-", "")
            add(
                source.copy(
                    companyId = "SIM:${universe.idToken()}-SPIN-COMPANY:$dateToken",
                    assetId = spinOffChildAssetId(universe, date),
                    displaySymbol = if (universe == FundReferenceUniverse.US_BROAD_EQUITY) {
                        "SPIN$dateToken"
                    } else {
                        "KRSPIN$dateToken"
                    },
                    displayName = "시뮬레이션 분사 기준자산 $date",
                ),
            )
            date = date.plus(1, DateTimeUnit.DAY)
        }
    }

    private fun spinOffChildAssetId(
        universe: FundReferenceUniverse,
        announcementDate: LocalDate,
    ): String = "REF:${universe.idToken()}-SPIN:${announcementDate.toString().replace("-", "")}"

    private fun FundReferenceUniverse.idToken(): String = when (this) {
        FundReferenceUniverse.US_BROAD_EQUITY -> "US"
        FundReferenceUniverse.KOREA_BROAD_EQUITY -> "KR"
    }

    private fun buildBaseRegularDividendHistory(
        random: DeterministicRandom,
        currentDividendPerShare: Double,
        maxDividendPerShare: Double = MAX_REGULAR_DIVIDEND_PER_SHARE,
    ): List<Double> = buildList(DIVIDEND_HISTORY_YEARS) {
        var dividendPerShare = currentDividendPerShare
        repeat(DIVIDEND_HISTORY_YEARS) {
            add(
                dividendPerShare.coerceIn(
                    MIN_REGULAR_DIVIDEND_PER_SHARE,
                    maxDividendPerShare,
                ),
            )
            val priorYearGrowth = random.nextDouble(-0.08, 0.24)
            dividendPerShare /= 1.0 + priorYearGrowth
        }
    }

    private fun MethodologyEquitySector.simulatedGicsClassificationCode(
        random: DeterministicRandom,
    ): Int = when (this) {
        MethodologyEquitySector.COMMUNICATION_SERVICES -> 5020
        MethodologyEquitySector.CONSUMER_DISCRETIONARY -> 2520
        MethodologyEquitySector.CONSUMER_STAPLES -> 3020
        MethodologyEquitySector.ENERGY -> 1010
        MethodologyEquitySector.FINANCIALS -> if (random.nextBoolean(0.035)) 402040 else 4010
        MethodologyEquitySector.HEALTH_CARE -> 3520
        MethodologyEquitySector.INDUSTRIALS -> 2010
        MethodologyEquitySector.INFORMATION_TECHNOLOGY -> 4510
        MethodologyEquitySector.MATERIALS -> 1510
        MethodologyEquitySector.REAL_ESTATE -> if (random.nextBoolean(0.78)) 6010 else 6020
        MethodologyEquitySector.UTILITIES -> 5510
    }

    private fun MethodologyEquitySector.toGameSector(): Sector = when (this) {
        MethodologyEquitySector.COMMUNICATION_SERVICES -> Sector.COMMUNICATION_SERVICES
        MethodologyEquitySector.CONSUMER_DISCRETIONARY -> Sector.CONSUMER_DISCRETIONARY
        MethodologyEquitySector.CONSUMER_STAPLES -> Sector.CONSUMER_STAPLES
        MethodologyEquitySector.ENERGY -> Sector.ENERGY
        MethodologyEquitySector.FINANCIALS -> Sector.FINANCIALS
        MethodologyEquitySector.HEALTH_CARE -> Sector.HEALTHCARE_BIO
        MethodologyEquitySector.INDUSTRIALS -> Sector.INDUSTRIALS
        MethodologyEquitySector.INFORMATION_TECHNOLOGY -> Sector.INFORMATION_TECHNOLOGY
        MethodologyEquitySector.MATERIALS -> Sector.MATERIALS_CHEMICALS
        MethodologyEquitySector.REAL_ESTATE -> Sector.REAL_ESTATE
        MethodologyEquitySector.UTILITIES -> Sector.UTILITIES
    }

    private fun portfolioIncomeYield(
        positions: List<ReferencePortfolioPosition>,
        snapshots: Map<String, SimulatedReferenceEquitySnapshot>,
    ): Double {
        return positions.sumOf { position ->
            position.currentWeight * requireNotNull(snapshots[position.assetId]).indicatedDividendYield
        }.coerceIn(0.0, 1.0)
    }

    private fun oneWayTurnover(
        before: List<ReferencePortfolioPosition>,
        after: List<ReferencePortfolioPosition>,
    ): Double {
        val beforeWeights = before.associate { it.assetId to it.currentWeight }
        val afterWeights = after.associate { it.assetId to it.currentWeight }
        return (beforeWeights.keys + afterWeights.keys).sumOf { assetId ->
            abs((afterWeights[assetId] ?: 0.0) - (beforeWeights[assetId] ?: 0.0))
        }.div(2.0).coerceIn(0.0, 1.0)
    }

    private fun List<ReferencePortfolioPosition>.normalizeBothWeights(): List<ReferencePortfolioPosition> {
        val currentTotal = sumOf(ReferencePortfolioPosition::currentWeight)
        val targetTotal = sumOf(ReferencePortfolioPosition::targetWeight)
        require(currentTotal > 0.0 && targetTotal > 0.0)
        return map { position ->
            position.copy(
                currentWeight = position.currentWeight / currentTotal,
                targetWeight = position.targetWeight / targetTotal,
            )
        }.repairBothWeightRounding().sortedBy(ReferencePortfolioPosition::assetId)
    }

    private fun List<ReferencePortfolioPosition>.repairCurrentWeightRounding(): List<ReferencePortfolioPosition> {
        if (isEmpty()) return this
        val difference = 1.0 - sumOf(ReferencePortfolioPosition::currentWeight)
        if (difference == 0.0) return this
        val index = indices.maxBy { this[it].currentWeight }
        return mapIndexed { candidateIndex, position ->
            if (candidateIndex == index) position.copy(currentWeight = position.currentWeight + difference)
            else position
        }
    }

    private fun List<ReferencePortfolioPosition>.repairBothWeightRounding(): List<ReferencePortfolioPosition> {
        if (isEmpty()) return this
        val currentDifference = 1.0 - sumOf(ReferencePortfolioPosition::currentWeight)
        val targetDifference = 1.0 - sumOf(ReferencePortfolioPosition::targetWeight)
        val currentIndex = indices.maxBy { this[it].currentWeight }
        val targetIndex = indices.maxBy { this[it].targetWeight }
        return mapIndexed { index, position ->
            position.copy(
                currentWeight = position.currentWeight + if (index == currentIndex) currentDifference else 0.0,
                targetWeight = position.targetWeight + if (index == targetIndex) targetDifference else 0.0,
            )
        }
    }

    private fun methodologyMarketReturn(
        methodology: CompiledEquityMethodology,
        macro: MacroEnvironment,
    ): Double =
        macro.regionalEtfHourlyReturns?.get(methodology.schedule.exposureRegion)
            ?: macro.marketHourlyReturns.filterKeys { market ->
                if (methodology.schedule.market.isKorean) market.isKorean else market.isUnitedStates
            }.values
                .takeIf(Collection<Double>::isNotEmpty)
                ?.average()
            ?: 0.0

    companion object {
        fun portfolioIdFor(ref: BenchmarkRef): String =
            ReferencePortfolioState.portfolioIdFor(ref)

        /** 런타임과 저장 검증이 같은 독립 RNG stream을 쓰게 하는 유일한 생성 경계다. */
        fun forCampaignSeed(
            campaignSeed: Long,
            methodologyRegistry: EquityMethodologyRegistry,
        ): ReferencePortfolioEngine = ReferencePortfolioEngine(
            seed = DeterministicRandom.mixSeed(campaignSeed, CAMPAIGN_STREAM_ID),
            methodologyRegistry = methodologyRegistry,
        )

        private const val CAMPAIGN_STREAM_ID: Long = 0x46554E44504F5254L
        private const val REFERENCE_BASE_YEAR: Int = 2025
        private const val MAX_SCENARIO_YEAR: Int = 2040
        private const val MAX_PREFLIGHT_SCHEDULED_ACTIONS: Int = 1_024
        private const val MAX_PREFLIGHT_EXTRAORDINARY_REVIEWS: Int = 1_024
        private const val MAX_NON_TRADING_DATE_SEARCH_DAYS: Int = 32
        private const val MAX_CORPORATE_ACTION_NOTICE_TRADING_DAYS: Int = 20
        private const val MAX_SCHEDULED_RECONSTITUTION_TRANSITION_STEPS: Int = 6
        private const val MAX_CORPORATE_ACTION_TRANSITION_STEPS: Int = 6
        private const val REFERENCE_EQUITY_COUNT: Int = 2_500
        private const val KOREA_REFERENCE_EQUITY_COUNT: Int = 900
        private const val TRADING_DAYS_PER_YEAR: Double = 252.0
        private const val TRADING_HOURS_PER_YEAR: Double = 252.0 * 6.5
        private const val BOOTSTRAP_MARKET_ANNUAL_DRIFT: Double = 0.065
        private const val BOOTSTRAP_MARKET_ANNUAL_VOLATILITY: Double = 0.16
        private const val BOOTSTRAP_SECTOR_ANNUAL_VOLATILITY: Double = 0.075
        private const val BOOTSTRAP_RESIDUAL_SHARE: Double = 0.62
        private const val BOOTSTRAP_QUALITY_ANNUAL_PREMIUM: Double = 0.018
        private const val BOOTSTRAP_VALUE_ANNUAL_PREMIUM: Double = 0.012
        private const val BOOTSTRAP_DIVIDEND_ANNUAL_PREMIUM: Double = 0.20
        private const val SECTOR_LOADING: Double = 0.45
        private const val QUALITY_TILT_SCALE: Double = 0.00010
        private const val VALUE_TILT_SCALE: Double = 0.00008
        private const val REFERENCE_DIVIDEND_YIELD: Double = 0.03
        private const val DIVIDEND_RATE_SCALE: Double = 1.8
        private const val DIVIDEND_SENTIMENT_SCALE: Double = 0.015
        private const val MAX_CONSTITUENT_LOG_MOVE: Double = 0.45
        private const val MAX_BOOTSTRAP_DAILY_LOG_MOVE: Double = 0.30
        private const val MAX_SELECTION_PERIOD_LOG_MOVE: Double = 0.65
        private const val MAX_HOURLY_PORTFOLIO_LOG_MOVE: Double = 0.35
        private const val MIN_PORTFOLIO_FACTOR: Double = 1e-12
        private const val WEIGHT_ALLOCATION_EPSILON: Double = 1e-12
        private const val MIN_BASE_MARKET_CAP: Double = 100_000_000.0
        private const val MAX_BASE_MARKET_CAP: Double = 2_500_000_000_000.0
        private const val MIN_KOREA_BASE_MARKET_CAP: Double = 50_000_000_000.0
        private const val MAX_KOREA_BASE_MARKET_CAP: Double = 250_000_000_000_000.0
        private const val MIN_REFERENCE_MARKET_CAP: Double = 1.0
        private const val MAX_REFERENCE_MARKET_CAP: Double = 1e20
        private const val MIN_SELECTION_DAILY_TURNOVER: Double = 0.0002
        private const val MAX_SELECTION_DAILY_TURNOVER: Double = 0.08
        private const val MIN_INVESTABLE_WEIGHT_FACTOR: Double = 0.05
        private const val MAX_INVESTABLE_WEIGHT_FACTOR: Double = 1.0
        private const val MAX_MONTHLY_SHARE_VOLUME: Double = 1e15
        private const val QUARTERLY_PROFITABILITY_NOISE: Double = 0.004
        private const val MIN_BASE_CASH_FLOW_FROM_OPERATIONS_RATIO: Double = -0.02
        private const val MAX_BASE_CASH_FLOW_FROM_OPERATIONS_RATIO: Double = 0.18
        private const val MIN_CASH_FLOW_FROM_OPERATIONS_RATIO: Double = -0.08
        private const val MAX_CASH_FLOW_FROM_OPERATIONS_RATIO: Double = 0.30
        private const val MIN_BASE_CAPITAL_EXPENDITURE_RATIO: Double = 0.005
        private const val MAX_BASE_CAPITAL_EXPENDITURE_RATIO: Double = 0.06
        private const val MIN_CAPITAL_EXPENDITURE_RATIO: Double = 0.001
        private const val MAX_CAPITAL_EXPENDITURE_RATIO: Double = 0.15
        private const val BASE_ZERO_TOTAL_DEBT_PROBABILITY: Double = 0.08
        private const val NEW_DEBT_PROBABILITY: Double = 0.025
        private const val MIN_TOTAL_DEBT_RATIO: Double = 0.01
        private const val NEW_TOTAL_DEBT_RATIO_LIMIT: Double = 0.20
        private const val MAX_TOTAL_DEBT_RATIO: Double = 2.0
        private const val MIN_BASE_SHARE_PRICE: Double = 8.0
        private const val MAX_BASE_SHARE_PRICE: Double = 350.0
        private const val MIN_KOREA_BASE_SHARE_PRICE: Double = 500.0
        private const val MAX_KOREA_BASE_SHARE_PRICE: Double = 500_000.0
        private const val MAX_KOREA_SHARE_PRICE: Double = 1_000_000.0
        private const val MIN_SHARE_PRICE: Double = 1.0
        private const val MAX_SHARE_PRICE: Double = 10_000.0
        private const val MIN_BASE_BOOK_VALUE_PER_SHARE: Double = 5.0
        private const val MAX_BASE_BOOK_VALUE_PER_SHARE: Double = 120.0
        private const val MIN_ABSOLUTE_BOOK_VALUE_PER_SHARE: Double = 2.0
        private const val MAX_ABSOLUTE_BOOK_VALUE_PER_SHARE: Double = 2_000.0
        private const val MAX_KOREA_ABSOLUTE_BOOK_VALUE_PER_SHARE: Double = 2_000_000.0
        private const val MIN_BASIC_EARNINGS_PER_SHARE: Double = -100.0
        private const val MAX_BASIC_EARNINGS_PER_SHARE: Double = 250.0
        private const val MAX_KOREA_ABSOLUTE_EARNINGS_PER_SHARE: Double = 500_000.0
        private const val MIN_STYLE_SALES_PER_SHARE: Double = 0.01
        private const val MAX_STYLE_SALES_PER_SHARE: Double = 5_000_000.0
        private const val MIN_STYLE_GROWTH: Double = -0.75
        private const val MAX_STYLE_GROWTH: Double = 1.50
        private const val MIN_STYLE_INVESTMENT_TO_ASSETS: Double = -0.50
        private const val MAX_STYLE_INVESTMENT_TO_ASSETS: Double = 0.75
        private const val MIN_STYLE_RETURN_ON_ASSETS: Double = -0.50
        private const val MAX_STYLE_RETURN_ON_ASSETS: Double = 0.75
        private const val BASE_NET_LOSS_PROBABILITY: Double = 0.08
        private const val DIVIDEND_HISTORY_YEARS: Int = 5
        private const val MIN_REGULAR_DIVIDEND_PER_SHARE: Double = 0.0001
        private const val MAX_REGULAR_DIVIDEND_PER_SHARE: Double = 2_000.0
        private const val MAX_KOREA_REGULAR_DIVIDEND_PER_SHARE: Double = 200_000.0
        private const val MIN_INDICATED_DIVIDEND_YIELD: Double = 0.0001
        private const val MAX_INDICATED_DIVIDEND_YIELD: Double = 0.20
        private const val SUSPENDED_DIVIDEND_CASH_FACTOR: Double = 0.05
        private const val DIVIDEND_REVIEW_CUTOFF_DAY: Int = 21
        private const val FEBRUARY_DIVIDEND_REVIEW_CUTOFF_DAY: Int = 18
        private const val DIVIDEND_EVENT_NONE: Int = 0
        private const val DIVIDEND_EVENT_OMITTED: Int = 1
        private const val DIVIDEND_EVENT_CEASED_INDEFINITELY: Int = 2
        private const val DIVIDEND_EVENT_POSTPONED_OR_DEFERRED: Int = 3
        private const val DIVIDEND_EVENT_REDUCED: Int = 4
        private const val REFERENCE_CORPORATE_ACTION_DAILY_PROBABILITY: Double = 0.025
        private val PLAN_ORDER = compareBy<ReferencePortfolioPlan>(ReferencePortfolioPlan::effectiveDate)
            .thenBy { plan -> plan.kind.executionPriority() }
            .thenBy(ReferencePortfolioPlan::id)

        private fun ReferencePortfolioActionKind.executionPriority(): Int = when (this) {
            ReferencePortfolioActionKind.CONSTITUENT_MERGER,
            ReferencePortfolioActionKind.CORPORATE_ACTION_TRANSITION,
            ReferencePortfolioActionKind.SPIN_OFF_REMOVAL,
            ReferencePortfolioActionKind.TERMINAL_REMOVAL,
            -> 0
            ReferencePortfolioActionKind.EXTRAORDINARY_REMOVAL -> 1
            ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION_TRANSITION -> 2
            ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION -> 3
            ReferencePortfolioActionKind.SCHEDULED_REWEIGHT -> 4
            ReferencePortfolioActionKind.CONSTRAINT_REWEIGHT -> 5
            ReferencePortfolioActionKind.SPIN_OFF_ADDITION -> 6
        }

        private val REFERENCE_SECTORS: List<MethodologyEquitySector> = listOf(
            MethodologyEquitySector.INFORMATION_TECHNOLOGY,
            MethodologyEquitySector.INFORMATION_TECHNOLOGY,
            MethodologyEquitySector.COMMUNICATION_SERVICES,
            MethodologyEquitySector.CONSUMER_DISCRETIONARY,
            MethodologyEquitySector.CONSUMER_DISCRETIONARY,
            MethodologyEquitySector.CONSUMER_STAPLES,
            MethodologyEquitySector.CONSUMER_STAPLES,
            MethodologyEquitySector.FINANCIALS,
            MethodologyEquitySector.FINANCIALS,
            MethodologyEquitySector.HEALTH_CARE,
            MethodologyEquitySector.HEALTH_CARE,
            MethodologyEquitySector.INDUSTRIALS,
            MethodologyEquitySector.INDUSTRIALS,
            MethodologyEquitySector.ENERGY,
            MethodologyEquitySector.MATERIALS,
            MethodologyEquitySector.UTILITIES,
            MethodologyEquitySector.REAL_ESTATE,
        )
        private val KOREA_REFERENCE_SECTORS: List<MethodologyEquitySector> = listOf(
            MethodologyEquitySector.INFORMATION_TECHNOLOGY,
            MethodologyEquitySector.INFORMATION_TECHNOLOGY,
            MethodologyEquitySector.INDUSTRIALS,
            MethodologyEquitySector.INDUSTRIALS,
            MethodologyEquitySector.CONSUMER_DISCRETIONARY,
            MethodologyEquitySector.CONSUMER_DISCRETIONARY,
            MethodologyEquitySector.FINANCIALS,
            MethodologyEquitySector.FINANCIALS,
            MethodologyEquitySector.FINANCIALS,
            MethodologyEquitySector.MATERIALS,
            MethodologyEquitySector.MATERIALS,
            MethodologyEquitySector.COMMUNICATION_SERVICES,
            MethodologyEquitySector.CONSUMER_STAPLES,
            MethodologyEquitySector.ENERGY,
            MethodologyEquitySector.HEALTH_CARE,
            MethodologyEquitySector.UTILITIES,
            MethodologyEquitySector.REAL_ESTATE,
        )
    }
}
