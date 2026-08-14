package com.amond.kmpbook.domain.simulation.fund

import com.amond.kmpbook.domain.methodology.EquityMethodologyCandidate
import com.amond.kmpbook.domain.methodology.EquityMethodologyCorporateActionDecision
import com.amond.kmpbook.domain.methodology.EquityMethodologyCorporateActionInput
import com.amond.kmpbook.domain.methodology.EquityMethodologyConstraintInput
import com.amond.kmpbook.domain.methodology.EquityMethodologyRegistry
import com.amond.kmpbook.domain.methodology.EquityMethodologyRemovalInput
import com.amond.kmpbook.domain.methodology.EquityMethodologyScheduledAction
import com.amond.kmpbook.domain.methodology.EquityMethodologySelectionInput
import com.amond.kmpbook.domain.methodology.EquityMethodologySignals
import com.amond.kmpbook.domain.methodology.EquityMethodologyWeightingInput
import com.amond.kmpbook.domain.methodology.StandardEquityMethodologySignalIds
import com.amond.kmpbook.domain.model.fund.BenchmarkDefinition
import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import com.amond.kmpbook.domain.model.fund.EquityMethodologyProfile
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
    private val ordinaryReferenceEquities: List<SimulatedReferenceEquity> = buildReferenceEquities()
    private val ordinaryReferenceEquityIds: Set<String> =
        ordinaryReferenceEquities.mapTo(linkedSetOf(), SimulatedReferenceEquity::assetId)
    private val spinOffReferenceEquities: List<SimulatedReferenceEquity> =
        buildSpinOffReferenceEquities(ordinaryReferenceEquities)
    private val spinOffChildAssetIds: Set<String> =
        spinOffReferenceEquities.mapTo(linkedSetOf(), SimulatedReferenceEquity::assetId)
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
                positionsForCorporateAction(currentPositions, action, decision, action.effectiveDate)
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

    internal fun canonicalScheduledSelectionRanks(
        definition: BenchmarkDefinition,
        plan: ReferencePortfolioPlan,
    ): Map<String, Int>? = runCatching {
        require(plan.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION)
        require(plan.benchmarkRef == definition.ref)
        val methodology = compile(definition)
        val action = requireNotNull(scheduledActionOn(methodology, plan.effectiveDate))
        require(action.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION)
        require(action.selectionDate == plan.selectionDate)
        require(action.weightReferenceDate == plan.weightReferenceDate)
        val selected = selectConstituents(
            methodology = methodology,
            action = action,
            incumbentAssetIds = requireNotNull(plan.selectionIncumbentAssetIds).toSet(),
            unavailableOnDate = requireNotNull(plan.selectionAvailabilityDate),
        )
        buildMap {
            selected.sortedBy { candidate -> candidate.snapshot.definition.assetId }
                .forEach { candidate ->
                    put(candidate.snapshot.definition.assetId, candidate.compositeRank)
                }
        }
    }.getOrNull()

    internal fun referenceAssetIdentity(assetId: String): ReferenceAssetIdentity? =
        referenceIdentityById[assetId]

    /** Creates exactly one campaign state for each distinct benchmark version. */
    fun initialBook(
        definitions: Collection<BenchmarkDefinition>,
        referenceDates: Map<BenchmarkRef, LocalDate>,
        at: Instant,
    ): ReferencePortfolioBook {
        val definitionsByRef = definitionsByRef(definitions)
        require(referenceDates.keys == definitionsByRef.keys) {
            "Initial reference dates must exactly match the benchmark definition set."
        }
        val states = linkedMapOf<String, ReferencePortfolioState>()
        definitionsByRef.values.sortedBy(BenchmarkDefinition::ref).forEach { definition ->
            val portfolioId = portfolioIdFor(definition.ref)
            states[portfolioId] = initialState(
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
        val action = queryNextScheduledAction(
            methodology = methodology,
            afterExclusive = afterExclusive,
            kind = kind,
        )
        if (kind == null) {
            val nextByLane = listOf(
                ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION,
                ReferencePortfolioActionKind.SCHEDULED_REWEIGHT,
            ).map { scheduledKind ->
                queryNextScheduledAction(
                    methodology = methodology,
                    afterExclusive = afterExclusive,
                    kind = scheduledKind,
                )
            }
            require(nextByLane.map(EquityMethodologyScheduledAction::effectiveDate).distinct().size == 2) {
                "The two scheduled methodology lanes cannot share one effective date."
            }
            val expected = nextByLane.minBy(EquityMethodologyScheduledAction::effectiveDate)
            require(action == expected) {
                "An unfiltered next scheduled action must be the earliest action from either lane."
            }
        }
        return action
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

    internal fun initialState(
        portfolioId: String,
        definition: BenchmarkDefinition,
        atDate: LocalDate,
        at: Instant,
    ): ReferencePortfolioState {
        val methodology = compile(definition)
        val profile = methodology.profile
        val schedule = methodology.schedule
        preflightScenario(methodology)
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
        val firstSelectionDate = initialAction.selectionDate
        val firstWeightReference = initialAction.weightReferenceDate
        val selected = selectConstituents(
            methodology = methodology,
            action = initialAction,
            incumbentAssetIds = emptySet(),
        )
        val firstReferenceValues = selected.associate { candidate ->
            candidate.snapshot.definition.assetId to simulatedReferenceMarketValueBetween(
                methodology = methodology,
                snapshot = candidate.snapshot,
                fromDate = firstSelectionDate,
                throughDate = firstWeightReference,
            )
        }
        val firstReferencePositions = positionsForSelection(
            selected = selected,
            rawFloatMarketValues = firstReferenceValues,
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
            revision = 0L,
            lastReconstitutionDate = firstReconstitution,
            lastRebalanceDate = firstReconstitution,
            nextReconstitutionDate = nextScheduledAction(
                methodology = methodology,
                afterExclusive = firstReconstitution,
                kind = ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION,
            ).effectiveDate,
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
            val selected = when (action.kind) {
                ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION -> selectConstituents(
                    methodology = methodology,
                    action = action,
                    incumbentAssetIds = incumbentIds,
                )

                ReferencePortfolioActionKind.SCHEDULED_REWEIGHT -> {
                    require(incumbentIds.isNotEmpty()) {
                        "A scheduled reweight cannot precede the initial reconstitution."
                    }
                    val snapshots = snapshotMapForKnownDataAt(action.weightReferenceDate, methodology)
                    incumbentIds.map { assetId ->
                        RankedReferenceCandidate(
                            snapshot = requireNotNull(snapshots[assetId]),
                            compositeRank = requireNotNull(incumbentRankById[assetId]),
                        )
                    }.sortedBy(RankedReferenceCandidate::compositeRank)
                }

                else -> error("Only provider-scheduled actions may be returned by the schedule.")
            }
            targetWeights(
                methodology = methodology,
                selected = selected,
                actionKind = action.kind,
                observationDate = action.weightReferenceDate,
                effectiveDate = action.effectiveDate,
                rawFloatMarketValues = selected.associate { candidate ->
                    candidate.snapshot.definition.assetId to candidate.snapshot.floatMarketCap
                },
            )
            incumbentIds = selected.mapTo(linkedSetOf()) { it.snapshot.definition.assetId }
            incumbentRankById = selected.associate { candidate ->
                candidate.snapshot.definition.assetId to candidate.compositeRank
            }
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
                remainingIds = remainingIds - decision.second
                requireCapCapacity(
                    assetIds = remainingIds,
                    methodology = methodology,
                    context = "${decision.first} 특별 구성 변경 후",
                )
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
            val remainingPlans = reconcilePendingPlans(
                currentPositions = plan.positions,
                plans = result.pendingPlans - plan,
                afterEffectiveDate = plan.effectiveDate,
                methodology = methodology,
            )
            result = result.copy(
                positions = plan.positions,
                revision = 0L,
                lastReconstitutionDate = if (plan.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION) {
                    plan.effectiveDate
                } else {
                    result.lastReconstitutionDate
                },
                lastRebalanceDate = plan.effectiveDate,
                nextReconstitutionDate = if (plan.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION) {
                    nextScheduledAction(
                        methodology = methodology,
                        afterExclusive = plan.effectiveDate,
                        kind = ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION,
                    ).effectiveDate
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
        plans: List<ReferencePortfolioPlan>,
        afterEffectiveDate: LocalDate,
        methodology: CompiledEquityMethodology,
        rebaseAsOfDate: LocalDate = afterEffectiveDate,
    ): List<ReferencePortfolioPlan> {
        var baselinePositions = currentPositions
        val result = mutableListOf<ReferencePortfolioPlan>()
        plans.filter { it.effectiveDate >= afterEffectiveDate }
            .sortedWith(PLAN_ORDER)
            .forEach { plan ->
                val currentIds = baselinePositions.mapTo(linkedSetOf(), ReferencePortfolioPosition::assetId)
                val plannedIds = plan.positions.mapTo(linkedSetOf(), ReferencePortfolioPosition::assetId)
                val rebased = when (plan.kind) {
                    ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION -> if (plannedIds == currentIds) {
                        plan
                    } else {
                        recompileScheduledReconstitutionPlan(
                            currentPositions = baselinePositions,
                            plan = plan,
                            methodology = methodology,
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
                    -> rebaseCorporateActionPlan(baselinePositions, plan, methodology)
                }
                if (rebased != null) {
                    result += rebased
                    baselinePositions = rebased.positions
                }
            }
        return result
    }

    private fun recompileScheduledReconstitutionPlan(
        currentPositions: List<ReferencePortfolioPosition>,
        plan: ReferencePortfolioPlan,
        methodology: CompiledEquityMethodology,
        unavailableOnDate: LocalDate,
    ): ReferencePortfolioPlan {
        val action = requireNotNull(scheduledActionOn(methodology, plan.effectiveDate))
        require(action.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION)
        val selectionAvailabilityDate = maxOf(
            requireNotNull(plan.selectionAvailabilityDate),
            unavailableOnDate,
        )
        val selected = selectConstituents(
            methodology = methodology,
            action = action,
            incumbentAssetIds = requireNotNull(plan.selectionIncumbentAssetIds).toSet(),
            unavailableOnDate = selectionAvailabilityDate,
        )
        val currentById = currentPositions.associateBy(ReferencePortfolioPosition::assetId)
        val originalMarketValues = requireNotNull(plan.weightReferenceMarketValues)
        val weightReferenceMarketValues = selected.associate { candidate ->
            val assetId = candidate.snapshot.definition.assetId
            assetId to (
                originalMarketValues[assetId]
                    ?: simulatedReferenceMarketValueBetween(
                        methodology = methodology,
                        snapshot = candidate.snapshot,
                        fromDate = action.selectionDate,
                        throughDate = action.weightReferenceDate,
                    )
                )
        }
        val fixedPositions = positionsForSelection(
            selected = selected,
            rawFloatMarketValues = weightReferenceMarketValues,
            methodology = methodology,
            actionKind = action.kind,
            observationDate = action.weightReferenceDate,
            effectiveDate = action.effectiveDate,
            previousPositions = currentById,
        )
        val positions = preservePendingPlanDrift(
            fixedPositions = fixedPositions,
            previousPlanPositions = plan.positions,
            weightReferenceMarketValues = weightReferenceMarketValues,
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
    ): ReferencePortfolioPlan? {
        val event = requireNotNull(plan.corporateAction)
        val currentIds = currentPositions.mapTo(linkedSetOf(), ReferencePortfolioPosition::assetId)
        val positions = when (plan.kind) {
            ReferencePortfolioActionKind.CONSTITUENT_MERGER,
            ReferencePortfolioActionKind.SPIN_OFF_ADDITION,
            ReferencePortfolioActionKind.TERMINAL_REMOVAL,
            -> {
                val decision = corporateActionDecision(
                    methodology = methodology,
                    event = event,
                    currentIds = currentIds,
                ) ?: return null
                positionsForCorporateAction(
                    currentPositions = currentPositions,
                    event = event,
                    decision = decision,
                    effectiveDate = plan.effectiveDate,
                )
            }
            ReferencePortfolioActionKind.SPIN_OFF_REMOVAL -> {
                val childId = requireNotNull(event.secondaryAssetId)
                if (childId !in currentIds || currentPositions.size <= 1) return null
                currentPositions.filterNot { position -> position.assetId == childId }.normalizeBothWeights()
            }
            else -> error("Only corporate-action plans can be rebased here.")
        }
        val nextIds = positions.mapTo(linkedSetOf(), ReferencePortfolioPosition::assetId)
        return plan.copy(
            positions = positions,
            addedAssetIds = (nextIds - currentIds).sorted(),
            removedAssetIds = (currentIds - nextIds).sorted(),
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
            nextScheduledAction(
                methodology = methodology,
                afterExclusive = effectiveDate,
                kind = ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION,
            ).effectiveDate
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
        val remainingPlans = reconcilePendingPlans(
            currentPositions = plan.positions,
            plans = state.pendingPlans - plan,
            afterEffectiveDate = effectiveDate,
            methodology = methodology,
        )
        val nextState = state.copy(
            positions = plan.positions,
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
        var pendingSelectionDate = state.pendingSelectionDate
        var pendingSelectionIncumbentAssetIds = state.pendingSelectionIncumbentAssetIds

        val reconstitutionAction = requireNotNull(
            scheduledActionOn(methodology, state.nextReconstitutionDate),
        ) { "The next reconstitution state date is not a provider-scheduled action." }
        require(reconstitutionAction.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION)
        if (referenceDate == reconstitutionAction.selectionDate && pendingSelectionDate == null) {
            pendingSelectionDate = referenceDate
            pendingSelectionIncumbentAssetIds =
                state.positions.map(ReferencePortfolioPosition::assetId).sorted()
        }
        if (
            referenceDate == reconstitutionAction.weightReferenceDate &&
            plans.none { it.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION }
        ) {
            plans = plans + createAnnualPlan(
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
            val kind = corporateActionPlanKind(event)
            val prospectivePlanId = referencePortfolioPlanId(
                portfolioId = state.portfolioId,
                kind = kind,
                weightReferenceDate = event.announcementDate,
                effectiveDate = event.effectiveDate,
                corporateAction = event,
            )
            val projectedPositions = projectedPositionsBefore(
                currentPositions = state.positions,
                plans = plans,
                effectiveDate = event.effectiveDate,
                kind = kind,
                planId = prospectivePlanId,
            )
            val invalidatesPendingSelection =
                event.kind != ReferencePortfolioCorporateActionKind.SPIN_OFF &&
                    plans.any { pending ->
                        pending.effectiveDate >= event.effectiveDate &&
                            pending.positions.any { position -> position.assetId == event.primaryAssetId }
                    }
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
                plans = plans,
                afterEffectiveDate = state.lastRebalanceDate,
                methodology = methodology,
                rebaseAsOfDate = pendingRebaseDate,
            )
        }
        return state.copy(
            pendingPlans = plans.sortedWith(PLAN_ORDER),
            pendingSelectionDate = pendingSelectionDate,
            pendingSelectionIncumbentAssetIds = pendingSelectionIncumbentAssetIds,
        )
    }

    private fun createAnnualPlan(
        state: ReferencePortfolioState,
        methodology: CompiledEquityMethodology,
        action: EquityMethodologyScheduledAction,
    ): ReferencePortfolioPlan {
        require(action.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION)
        val selectionDate = action.selectionDate
        val weightReferenceDate = action.weightReferenceDate
        val effectiveDate = action.effectiveDate
        require(state.pendingSelectionDate == selectionDate) {
            "The annual plan is missing its selection-close incumbent snapshot."
        }
        val selectionIncumbentAssetIds = requireNotNull(state.pendingSelectionIncumbentAssetIds)
        val selected = selectConstituents(
            methodology = methodology,
            action = action,
            incumbentAssetIds = selectionIncumbentAssetIds.toSet(),
            unavailableOnDate = weightReferenceDate,
        )
        val currentById = state.positions.associateBy(ReferencePortfolioPosition::assetId)
        val weightReferenceMarketValues = selected.associate { candidate ->
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
        val positions = positionsForSelection(
            selected = selected,
            rawFloatMarketValues = weightReferenceMarketValues,
            methodology = methodology,
            actionKind = action.kind,
            observationDate = weightReferenceDate,
            effectiveDate = effectiveDate,
            previousPositions = currentById,
        )
        return newPlan(
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
        )
    }

    private fun createReweightPlan(
        state: ReferencePortfolioState,
        methodology: CompiledEquityMethodology,
        kind: ReferencePortfolioActionKind,
        referenceDate: LocalDate,
        effectiveDate: LocalDate,
    ): ReferencePortfolioPlan {
        require(kind == ReferencePortfolioActionKind.SCHEDULED_REWEIGHT || kind == ReferencePortfolioActionKind.CONSTRAINT_REWEIGHT)
        val snapshots = snapshotMapForKnownDataAt(referenceDate, methodology)
        val selected = state.positions.map { position ->
            RankedReferenceCandidate(
                snapshot = requireNotNull(snapshots[position.assetId]),
                compositeRank = position.selectionRank,
            )
        }
        val weightReferenceMarketValues = state.positions.associate {
            it.assetId to it.referenceFloatMarketValue
        }
        val positions = positionsForSelection(
            selected = selected,
            rawFloatMarketValues = weightReferenceMarketValues,
            methodology = methodology,
            actionKind = kind,
            observationDate = referenceDate,
            effectiveDate = effectiveDate,
            previousPositions = state.positions.associateBy(ReferencePortfolioPosition::assetId),
        )
        return newPlan(
            state = state,
            kind = kind,
            selectionDate = referenceDate,
            weightReferenceDate = referenceDate,
            effectiveDate = effectiveDate,
            positions = positions,
            weightReferenceMarketValues = weightReferenceMarketValues,
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
        val positions = positionsForCorporateAction(
            currentPositions = baselinePositions,
            event = event,
            decision = decision,
            effectiveDate = event.effectiveDate,
        )
        val kind = corporateActionPlanKind(event)
        val primaryPlan = newPlan(
            state = state,
            kind = kind,
            selectionDate = event.announcementDate,
            weightReferenceDate = event.announcementDate,
            effectiveDate = event.effectiveDate,
            positions = positions,
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
        val universeIds = snapshots.keys.filterTo(linkedSetOf()) { assetId -> assetId !in unavailableIds }
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
                require(decision.addedAssetIds.isEmpty() && decision.followUpRemovalDate == null)
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
                require(decision.addedAssetIds.isEmpty())
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
        require(positions.isNotEmpty())
        return positions.values.toList().normalizeBothWeights()
    }

    private fun newPlan(
        state: ReferencePortfolioState,
        kind: ReferencePortfolioActionKind,
        selectionDate: LocalDate,
        weightReferenceDate: LocalDate,
        effectiveDate: LocalDate,
        positions: List<ReferencePortfolioPosition>,
        weightReferenceMarketValues: Map<String, Double>? = null,
        selectionIncumbentAssetIds: List<String>? = null,
        selectionAvailabilityDate: LocalDate? = null,
        corporateAction: ReferencePortfolioCorporateAction? = null,
        baselinePositions: List<ReferencePortfolioPosition> = state.positions,
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
            addedAssetIds = (nextIds - previousIds).sorted(),
            removedAssetIds = (previousIds - nextIds).sorted(),
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
        unavailableOnDate: LocalDate = action.selectionDate,
    ): List<RankedReferenceCandidate> {
        require(action.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION)
        val unavailableIds = unavailableScheduledAssetIdsAt(methodology, unavailableOnDate)
        val snapshots = selectionSnapshotMapForAction(action, methodology)
            .filterKeys { assetId -> assetId in ordinaryReferenceEquityIds && assetId !in unavailableIds }
        val candidatesById = snapshots.mapValues { (_, snapshot) ->
            snapshot.toMethodologyCandidate(methodology, action.selectionDate)
        }
        val eligibleIncumbentIds = incumbentAssetIds.filterTo(linkedSetOf(), candidatesById::containsKey)
        val selected = methodology.policy.select(
            EquityMethodologySelectionInput(
                profile = methodology.profile,
                scheduledAction = action,
                candidates = candidatesById.values.toList(),
                incumbentAssetIds = eligibleIncumbentIds,
            ),
        ).asSequence().take(ReferencePortfolioLimits.MAX_CONSTITUENTS + 1).toList()
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
        return selected.map { selection ->
            RankedReferenceCandidate(
                snapshot = snapshots.getValue(selection.assetId),
                compositeRank = selection.rank,
            )
        }.sortedBy(RankedReferenceCandidate::compositeRank)
    }

    private fun positionsForSelection(
        selected: List<RankedReferenceCandidate>,
        rawFloatMarketValues: Map<String, Double>,
        methodology: CompiledEquityMethodology,
        actionKind: ReferencePortfolioActionKind,
        observationDate: LocalDate,
        effectiveDate: LocalDate,
        previousPositions: Map<String, ReferencePortfolioPosition>,
    ): List<ReferencePortfolioPosition> {
        require(selected.map { it.snapshot.definition.assetId }.toSet() == rawFloatMarketValues.keys)
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
                referenceFloatMarketValue = rawFloatMarketValues.getValue(definition.assetId),
                enteredOn = previousPositions[definition.assetId]?.enteredOn ?: effectiveDate,
                selectionRank = candidate.compositeRank,
            )
        }.sortedBy(ReferencePortfolioPosition::assetId).repairBothWeightRounding()
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
        val primaryIndex = random.nextInt(ordinaryReferenceEquities.size)
        val primaryId = ordinaryReferenceEquities[primaryIndex].assetId
        val secondaryId = when (kind) {
            ReferencePortfolioCorporateActionKind.TERMINAL_REMOVAL -> null
            ReferencePortfolioCorporateActionKind.SPIN_OFF -> spinOffChildAssetId(announcementDate)
            ReferencePortfolioCorporateActionKind.MERGER -> ordinaryReferenceEquities[
                (primaryIndex + 1 + random.nextInt(ordinaryReferenceEquities.size - 1)) %
                    ordinaryReferenceEquities.size
            ].assetId
        }
        val effectiveDate = methodology.schedule.addTradingDays(announcementDate, 1)
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
        val unavailable = spinOffChildAssetIds.toMutableSet()
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
            StandardEquityMethodologySignalIds.AVERAGE_DAILY_VALUE_TRADED to
                threeMonthAverageDailyValueTraded,
            StandardEquityMethodologySignalIds.INDICATED_DIVIDEND_YIELD to indicatedDividendYield,
            StandardEquityMethodologySignalIds.FREE_CASH_FLOW_TO_DEBT to freeCashFlowToDebt,
            StandardEquityMethodologySignalIds.RETURN_ON_EQUITY to returnOnEquity,
            StandardEquityMethodologySignalIds.FIVE_YEAR_DIVIDEND_GROWTH to fiveYearDividendGrowth,
        )
        val supportedIntegers = mapOf(
            StandardEquityMethodologySignalIds.GICS_CLASSIFICATION_CODE to
                definition.gicsClassificationCode,
            StandardEquityMethodologySignalIds.DIVIDEND_PAYMENT_YEARS to dividendPaymentYears,
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
     * prior year-end. Only FMC, trailing three-month ADVT, the regular fixed IAD and share price
     * are advanced to the official February selection date.
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
            snapshotMapForYear(priorYear)
                .filterKeys(ordinaryReferenceEquityIds::contains)
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

                val dividendRandom = DeterministicRandom.keyed(
                    seed,
                    "fund-selection-indicated-dividend:${equity.assetId}:$effectiveYear",
                )
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
                val currentSharePrice = (priorSnapshot.sharePrice * priceFactor)
                    .coerceIn(MIN_SHARE_PRICE, MAX_SHARE_PRICE)
                val rawRegularFixedDividend = (
                    priorSnapshot.regularFixedAnnualDividendPerShare * indicatedCashFactor
                    ).coerceIn(MIN_REGULAR_DIVIDEND_PER_SHARE, MAX_REGULAR_DIVIDEND_PER_SHARE)
                val currentYield = (rawRegularFixedDividend / currentSharePrice)
                    .coerceIn(MIN_INDICATED_DIVIDEND_YIELD, MAX_INDICATED_DIVIDEND_YIELD)
                priorSnapshot.copy(
                    floatMarketCap = currentMarketCap,
                    threeMonthAverageDailyValueTraded = currentMarketCap * currentTurnover,
                    sharePrice = currentSharePrice,
                    regularFixedAnnualDividendPerShare = currentSharePrice * currentYield,
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
        var marketCap = equity.baseFloatMarketCap
        var threeMonthAverageDailyValueTraded = equity.baseThreeMonthAverageDailyValueTraded
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
        for (candidateYear in (REFERENCE_BASE_YEAR + 1)..year) {
            val random = DeterministicRandom.keyed(
                seed,
                "fund-reference-fundamentals:${equity.assetId}:$candidateYear",
            )
            val priorMarketCap = marketCap
            val growth = (0.025 + equity.quality * 0.055 + random.nextGaussian() * 0.16)
                .coerceIn(-0.45, 0.55)
            marketCap = (marketCap * exp(growth)).coerceIn(MIN_REFERENCE_MARKET_CAP, MAX_REFERENCE_MARKET_CAP)

            val priorTurnover = threeMonthAverageDailyValueTraded / priorMarketCap
            val currentTurnover = (priorTurnover * exp(random.nextGaussian() * 0.08))
                .coerceIn(MIN_SELECTION_DAILY_TURNOVER, MAX_SELECTION_DAILY_TURNOVER)
            threeMonthAverageDailyValueTraded = marketCap * currentTurnover

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
                .coerceIn(MIN_SHARE_PRICE, MAX_SHARE_PRICE)
            val bookMagnitude = (abs(bookValuePerShare) * exp(random.nextGaussian() * 0.11))
                .coerceIn(MIN_ABSOLUTE_BOOK_VALUE_PER_SHARE, MAX_ABSOLUTE_BOOK_VALUE_PER_SHARE)
            val remainsNegative = if (bookValuePerShare < 0.0) {
                !random.nextBoolean((0.12 + equity.quality * 0.20).coerceIn(0.12, 0.32))
            } else {
                random.nextBoolean((0.022 - equity.quality * 0.018).coerceIn(0.002, 0.021))
            }
            bookValuePerShare = if (remainsNegative) -bookMagnitude else bookMagnitude
            basicEarningsPerShare = (
                basicEarningsPerShare +
                    sharePrice * ((equity.quality - 0.5) * 0.010 + random.nextGaussian() * 0.018)
                ).coerceIn(MIN_BASIC_EARNINGS_PER_SHARE, MAX_BASIC_EARNINGS_PER_SHARE)

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
                ).coerceIn(MIN_REGULAR_DIVIDEND_PER_SHARE, MAX_REGULAR_DIVIDEND_PER_SHARE)
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
        }
        return SimulatedReferenceEquitySnapshot(
            definition = equity,
            floatMarketCap = marketCap,
            threeMonthAverageDailyValueTraded = threeMonthAverageDailyValueTraded,
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
            SimulatedReferenceEquity(
                companyId = companyId,
                assetId = assetId,
                displaySymbol = "SIM$paddedIndex",
                displayName = "시뮬레이션 미국 기준자산 $paddedIndex",
                sector = methodologySector.toGameSector(),
                methodologySector = methodologySector,
                gicsClassificationCode = gicsClassificationCode,
                baseFloatMarketCap = baseFloatMarketCap,
                baseThreeMonthAverageDailyValueTraded =
                    baseFloatMarketCap * random.nextDouble(0.001, 0.025),
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
                baseBasicEarningsPerShare = baseBookValuePerShare * baseReturnOnEquity,
                baseBookValuePerShare = baseBookValuePerShare,
                baseSharePrice = baseSharePrice,
                baseRegularFixedAnnualDividendPerShare =
                    baseRegularFixedAnnualDividendPerShare,
                baseAnnualRegularDividendPerShareNewestFirst =
                    baseAnnualRegularDividendPerShareNewestFirst,
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
     * Temporary spin-off lines live outside the ordinary selection universe. Giving every
     * possible announcement date its own identity prevents a future synthetic event from
     * reserving an otherwise eligible company in an earlier annual review.
     */
    private fun buildSpinOffReferenceEquities(
        ordinaryEquities: List<SimulatedReferenceEquity>,
    ): List<SimulatedReferenceEquity> = buildList {
        var date = LocalDate(REFERENCE_BASE_YEAR + 1, 1, 1)
        while (date < GameCalendar.CAMPAIGN_END_DATE) {
            val random = DeterministicRandom.keyed(seed, "fund-reference-spin-child-definition:$date")
            val source = ordinaryEquities[random.nextInt(ordinaryEquities.size)]
            val dateToken = date.toString().replace("-", "")
            add(
                source.copy(
                    companyId = "SIM:US-SPIN-COMPANY:$dateToken",
                    assetId = spinOffChildAssetId(date),
                    displaySymbol = "SPIN$dateToken",
                    displayName = "시뮬레이션 분사 기준자산 $date",
                ),
            )
            date = date.plus(1, DateTimeUnit.DAY)
        }
    }

    private fun spinOffChildAssetId(announcementDate: LocalDate): String =
        "REF:US-SPIN:${announcementDate.toString().replace("-", "")}"

    private fun buildBaseRegularDividendHistory(
        random: DeterministicRandom,
        currentDividendPerShare: Double,
    ): List<Double> = buildList(DIVIDEND_HISTORY_YEARS) {
        var dividendPerShare = currentDividendPerShare
        repeat(DIVIDEND_HISTORY_YEARS) {
            add(
                dividendPerShare.coerceIn(
                    MIN_REGULAR_DIVIDEND_PER_SHARE,
                    MAX_REGULAR_DIVIDEND_PER_SHARE,
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
            ?: macro.marketHourlyReturns.filterKeys(Market::isUnitedStates).values
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
        private const val REFERENCE_EQUITY_COUNT: Int = 2_500
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
        private const val MIN_REFERENCE_MARKET_CAP: Double = 1.0
        private const val MAX_REFERENCE_MARKET_CAP: Double = 1e20
        private const val MIN_SELECTION_DAILY_TURNOVER: Double = 0.0002
        private const val MAX_SELECTION_DAILY_TURNOVER: Double = 0.08
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
        private const val MIN_SHARE_PRICE: Double = 1.0
        private const val MAX_SHARE_PRICE: Double = 10_000.0
        private const val MIN_BASE_BOOK_VALUE_PER_SHARE: Double = 5.0
        private const val MAX_BASE_BOOK_VALUE_PER_SHARE: Double = 120.0
        private const val MIN_ABSOLUTE_BOOK_VALUE_PER_SHARE: Double = 2.0
        private const val MAX_ABSOLUTE_BOOK_VALUE_PER_SHARE: Double = 2_000.0
        private const val MIN_BASIC_EARNINGS_PER_SHARE: Double = -100.0
        private const val MAX_BASIC_EARNINGS_PER_SHARE: Double = 250.0
        private const val BASE_NET_LOSS_PROBABILITY: Double = 0.08
        private const val DIVIDEND_HISTORY_YEARS: Int = 5
        private const val MIN_REGULAR_DIVIDEND_PER_SHARE: Double = 0.0001
        private const val MAX_REGULAR_DIVIDEND_PER_SHARE: Double = 2_000.0
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
            ReferencePortfolioActionKind.SPIN_OFF_REMOVAL,
            ReferencePortfolioActionKind.TERMINAL_REMOVAL,
            -> 0
            ReferencePortfolioActionKind.EXTRAORDINARY_REMOVAL -> 1
            ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION -> 2
            ReferencePortfolioActionKind.SCHEDULED_REWEIGHT -> 3
            ReferencePortfolioActionKind.CONSTRAINT_REWEIGHT -> 4
            ReferencePortfolioActionKind.SPIN_OFF_ADDITION -> 5
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
    }
}
