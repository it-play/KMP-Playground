package com.amond.kmpbook.domain.simulation.fund

import com.amond.kmpbook.domain.model.fund.ReferencePortfolioPosition
import com.amond.kmpbook.domain.model.fund.BenchmarkDefinition
import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import com.amond.kmpbook.domain.model.fund.EquityMethodologyProfile
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioAdvance
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioBook
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioBookAdvance
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioState
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioActionKind
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioPlan
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioRecord
import com.amond.kmpbook.domain.model.fund.MethodologyEquitySector
import com.amond.kmpbook.domain.model.instrument.EtfExposureRegion
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
 * 버전된 주식 벤치마크 방법론을 비거래 기준자산에 적용하는 결정론적 포트폴리오 엔진이다.
 *
 * 경로 의존 상태는 모두 [ReferencePortfolioState]에 있고, 이 객체가 보유하는 캐시는 seed와 연도만으로
 * 다시 만들 수 있는 불변 파생 데이터다. 따라서 provisional/final 가격 패스가 같은 입력을 계산해도
 * 구성안·수익률·원장이 달라지지 않는다.
 * 이 상태는 SCHD의 미래 실제 보유종목이 아니라 공식 규칙을 재현하는 비거래 reference/index
 * portfolio이며, 플레이어가 주문할 수 있는 기업 목록으로 노출되지 않는다.
 */
class ReferencePortfolioEngine private constructor(private val seed: Long) {
    private val referenceEquities: List<SimulatedReferenceEquity> = buildReferenceEquities()
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
    private val selectionSnapshotByIdCache = mutableMapOf<Int, Map<String, SimulatedReferenceEquitySnapshot>>()
    private val bootstrapMarketReturnCache = mutableMapOf<LocalDate, Double>()
    private val bootstrapSectorReturnsCache = mutableMapOf<LocalDate, DoubleArray>()
    private val selectionTradingFractionCache = mutableMapOf<LocalDate, Double>()
    private val selectionMarketReturnCache = mutableMapOf<LocalDate, Double>()
    private val selectionSectorReturnsCache = mutableMapOf<LocalDate, DoubleArray>()
    private val preflightedProfiles = mutableSetOf<EquityMethodologyProfile>()

    /** 저장 상태가 이 캠페인 seed의 비거래 기준자산 원본을 참조하는지 확인한다. */
    internal fun hasCanonicalReferenceIdentity(position: ReferencePortfolioPosition): Boolean =
        referenceAssetIdentity(position.assetId) != null

    internal fun hasCanonicalReferenceAssetId(assetId: String): Boolean =
        assetId in referenceEquityById

    internal fun referenceAssetIdentity(assetId: String): ReferenceAssetIdentity? =
        referenceIdentityById[assetId]

    /** Creates exactly one campaign state for each distinct benchmark version. */
    fun initialBook(
        definitions: Collection<BenchmarkDefinition>,
        atDate: LocalDate,
        at: Instant,
    ): ReferencePortfolioBook {
        val definitionsByRef = definitionsByRef(definitions)
        val states = linkedMapOf<String, ReferencePortfolioState>()
        definitionsByRef.values.sortedBy(BenchmarkDefinition::ref).forEach { definition ->
            val portfolioId = portfolioIdFor(definition.ref)
            states[portfolioId] = initialState(
                portfolioId = portfolioId,
                definition = definition,
                atDate = atDate,
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
            advance.record?.let(records::add)
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

    internal fun initialState(
        portfolioId: String,
        definition: BenchmarkDefinition,
        atDate: LocalDate,
        at: Instant,
    ): ReferencePortfolioState {
        val profile = BenchmarkMethodologyCompiler.compileSchd(definition)
        preflightScenario(profile)
        require(atDate >= profile.effectiveFrom) { "벤치마크 방법론 시행일 전에 구성을 만들 수 없습니다." }

        /*
         * A campaign starts without a pre-campaign rebalance ledger. Stamping the latest
         * quarterly date onto March weights would make the persisted schedule disagree with
         * both weights and reference market values, so replay the index-plan lifecycle while
         * deliberately keeping revision zero.
         */
        val firstReconstitution = profile.effectiveFrom
        val firstSelectionDate = thirdFriday(firstReconstitution.year, 2)
        val firstWeightReference = bootstrapWeightReferenceDate(profile)
        val selected = selectConstituents(
            profile = profile,
            effectiveYear = firstReconstitution.year,
            incumbentAssetIds = emptySet(),
        )
        val firstReferenceValues = selected.associate { candidate ->
            candidate.snapshot.definition.assetId to simulatedReferenceMarketValueBetween(
                snapshot = candidate.snapshot,
                fromDate = firstSelectionDate,
                throughDate = firstWeightReference,
            )
        }
        val firstReferencePositions = positionsForSelection(
            selected = selected,
            rawFloatMarketValues = firstReferenceValues,
            profile = profile,
            effectiveDate = firstReconstitution,
            previousPositions = emptyMap(),
        )
        val firstEffectivePositions = advanceBootstrapRange(
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
            nextReconstitutionDate = nextReconstitutionDate(profile, firstReconstitution),
            nextRebalanceDate = nextRebalanceDate(profile, firstReconstitution),
            pendingPlans = emptyList(),
            lastTurnoverRate = 0.0,
            estimatedAnnualIncomeYield = portfolioIncomeYield(
                firstEffectivePositions,
                snapshotMapForKnownDataAt(firstReconstitution),
            ),
            asOf = at,
            lastAppliedActionKind = ReferencePortfolioActionKind.ANNUAL_RECONSTITUTION,
        )

        val lastReplayDate = lastCompletedUsTradingDate(atDate, at)
        var replayDate = firstReconstitution
        while (replayDate <= lastReplayDate) {
            if (isUsTradingDate(replayDate)) {
                state = applyBootstrapDuePlan(state, profile, replayDate)
                val snapshots = snapshotMapForKnownDataAt(replayDate)
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
                state = schedulePlansAtClose(state, profile, replayDate)
            }
            replayDate = replayDate.plus(1, DateTimeUnit.DAY)
        }
        return state.copy(asOf = at, revision = 0L)
    }

    private fun lastCompletedUsTradingDate(atDate: LocalDate, at: Instant): LocalDate {
        var candidate = if (ReferencePortfolioCalendar.hasReachedUsRegularClose(atDate, at)) {
            atDate
        } else {
            atDate.minus(1, DateTimeUnit.DAY)
        }
        while (!isUsTradingDate(candidate)) candidate = candidate.minus(1, DateTimeUnit.DAY)
        return candidate
    }

    /** Fails at game creation instead of discovering an impossible future rebalance years later. */
    private fun preflightScenario(profile: EquityMethodologyProfile) {
        if (profile in preflightedProfiles) return
        require(profile.effectiveFrom.year in (REFERENCE_BASE_YEAR + 1)..MAX_SCENARIO_YEAR) {
            "기준자산 시나리오는 ${REFERENCE_BASE_YEAR + 1}~${MAX_SCENARIO_YEAR}년을 지원합니다."
        }
        var incumbentIds = emptySet<String>()
        for (effectiveYear in profile.effectiveFrom.year..MAX_SCENARIO_YEAR) {
            if (effectiveYear > profile.effectiveFrom.year) {
                incumbentIds = preflightMonthlyDeletions(
                    assetIds = incumbentIds,
                    profile = profile,
                    year = effectiveYear,
                    months = 1..2,
                )
            }
            val selected = selectConstituents(
                profile = profile,
                effectiveYear = effectiveYear,
                incumbentAssetIds = incumbentIds,
            )
            cappedFmcWeights(
                rawFloatMarketValues = selected.associate { candidate ->
                    candidate.snapshot.definition.assetId to candidate.snapshot.floatMarketCap
                },
                methodologySectors = selected.associate { candidate ->
                    candidate.snapshot.definition.assetId to candidate.snapshot.definition.methodologySector
                },
                individualCap = profile.individualWeightCap,
                sectorCap = profile.sectorWeightCap,
            )
            incumbentIds = selected.mapTo(linkedSetOf()) { it.snapshot.definition.assetId }
            incumbentIds = preflightMonthlyDeletions(
                assetIds = incumbentIds,
                profile = profile,
                year = effectiveYear,
                months = profile.annualReconstitutionMonth..12,
            )
        }
        preflightedProfiles += profile
    }

    private fun preflightMonthlyDeletions(
        assetIds: Set<String>,
        profile: EquityMethodologyProfile,
        year: Int,
        months: IntRange,
    ): Set<String> {
        var remainingIds = assetIds
        for (month in months) {
            val monthEnd = lastUsTradingDateOfMonth(year, month)
            val deletionEffectiveDate = firstUsTradingDateOfNextMonth(monthEnd)
            if (deletionEffectiveDate > GameCalendar.CAMPAIGN_END_DATE) continue
            val removedIds = remainingIds.filterTo(linkedSetOf()) { assetId ->
                dividendProgramSuspended(referenceEquityById.getValue(assetId), year, month)
            }
            if (removedIds.isEmpty()) continue
            remainingIds = remainingIds - removedIds
            requireCapCapacity(
                assetIds = remainingIds,
                profile = profile,
                context = "$deletionEffectiveDate 무대체 배당중단 삭제 후",
            )
        }
        return remainingIds
    }

    private fun requireCapCapacity(
        assetIds: Set<String>,
        profile: EquityMethodologyProfile,
        context: String,
    ) {
        require(assetIds.isNotEmpty()) { "$context 구성종목이 모두 삭제됩니다." }
        val totalCapacity = assetIds.groupBy { assetId ->
            referenceEquityById.getValue(assetId).methodologySector
        }.values.sumOf { assets ->
            min(profile.sectorWeightCap, assets.size * profile.individualWeightCap)
        }
        require(totalCapacity >= 1.0 - WEIGHT_ALLOCATION_EPSILON) {
            "$context 종목·섹터 상한으로 100% 비중을 만들 수 없습니다."
        }
    }

    private fun bootstrapWeightReferenceDate(profile: EquityMethodologyProfile): LocalDate =
        annualWeightReferenceDate(profile.effectiveFrom)

    private fun simulatedReferenceMarketValueAt(
        snapshot: SimulatedReferenceEquitySnapshot,
        snapshotYear: Int,
        date: LocalDate,
    ): Double {
        require(date.year == snapshotYear + 1) {
            "연말 기준 스냅샷은 바로 다음 해의 비중 기준일에만 사용합니다."
        }
        val tradingFraction = selectionTradingFractionCache.getOrPut(date) {
            var tradingDays = 0
            var cursor = LocalDate(date.year, 1, 1)
            while (cursor <= date) {
                if (isUsTradingDate(cursor)) tradingDays += 1
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
        snapshot: SimulatedReferenceEquitySnapshot,
        fromDate: LocalDate,
        throughDate: LocalDate,
    ): Double {
        require(throughDate >= fromDate)
        var value = snapshot.floatMarketCap
        var cursor = fromDate.plus(1, DateTimeUnit.DAY)
        while (cursor <= throughDate) {
            if (isUsTradingDate(cursor)) {
                value = (value * exp(bootstrapConstituentLogReturn(snapshot, cursor)))
                    .coerceIn(MIN_REFERENCE_MARKET_CAP, MAX_REFERENCE_MARKET_CAP)
            }
            cursor = cursor.plus(1, DateTimeUnit.DAY)
        }
        return value
    }

    private fun advanceBootstrapRange(
        positions: List<ReferencePortfolioPosition>,
        firstDateExclusive: LocalDate,
        lastDateExclusive: LocalDate,
    ): List<ReferencePortfolioPosition> {
        var result = positions
        var cursor = firstDateExclusive.plus(1, DateTimeUnit.DAY)
        while (cursor < lastDateExclusive) {
            if (isUsTradingDate(cursor)) {
                val snapshots = snapshotMapForKnownDataAt(cursor)
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

    private fun applyBootstrapDuePlan(
        state: ReferencePortfolioState,
        profile: EquityMethodologyProfile,
        referenceDate: LocalDate,
    ): ReferencePortfolioState {
        val duePlans = state.pendingPlans.filter { it.effectiveDate <= referenceDate }
        if (duePlans.isEmpty()) return state
        val plan = duePlans.minWith(PLAN_ORDER)
        val turnover = oneWayTurnover(state.positions, plan.positions)
        val remainingPlans = reconcilePendingPlans(
            currentPositions = plan.positions,
            plans = state.pendingPlans - plan,
            afterEffectiveDate = plan.effectiveDate,
            profile = profile,
        )
        return state.copy(
            positions = plan.positions,
            revision = 0L,
            lastReconstitutionDate = if (plan.kind == ReferencePortfolioActionKind.ANNUAL_RECONSTITUTION) {
                plan.effectiveDate
            } else {
                state.lastReconstitutionDate
            },
            lastRebalanceDate = plan.effectiveDate,
            nextReconstitutionDate = if (plan.kind == ReferencePortfolioActionKind.ANNUAL_RECONSTITUTION) {
                nextReconstitutionDate(profile, plan.effectiveDate)
            } else {
                state.nextReconstitutionDate
            },
            nextRebalanceDate = if (
                plan.kind == ReferencePortfolioActionKind.ANNUAL_RECONSTITUTION ||
                plan.kind == ReferencePortfolioActionKind.QUARTERLY_REBALANCE ||
                state.nextRebalanceDate <= plan.effectiveDate
            ) {
                nextRebalanceDate(profile, plan.effectiveDate)
            } else {
                state.nextRebalanceDate
            },
            pendingPlans = remainingPlans,
            lastTurnoverRate = turnover,
            estimatedAnnualIncomeYield = portfolioIncomeYield(
                plan.positions,
                snapshotMapForKnownDataAt(referenceDate),
            ),
            lastAppliedActionKind = plan.kind,
        )
    }

    private fun reconcilePendingPlans(
        currentPositions: List<ReferencePortfolioPosition>,
        plans: List<ReferencePortfolioPlan>,
        afterEffectiveDate: LocalDate,
        profile: EquityMethodologyProfile,
    ): List<ReferencePortfolioPlan> {
        val currentIds = currentPositions.mapTo(linkedSetOf(), ReferencePortfolioPosition::assetId)
        return plans.asSequence()
            .filter { it.effectiveDate > afterEffectiveDate }
            .mapNotNull { plan ->
                val plannedIds = plan.positions.mapTo(linkedSetOf(), ReferencePortfolioPosition::assetId)
                when (plan.kind) {
                    ReferencePortfolioActionKind.ANNUAL_RECONSTITUTION -> plan.copy(
                        addedAssetIds = (plannedIds - currentIds).sorted(),
                        removedAssetIds = (currentIds - plannedIds).sorted(),
                    )

                    ReferencePortfolioActionKind.QUARTERLY_REBALANCE,
                    ReferencePortfolioActionKind.DAILY_CAP_REBALANCE,
                    -> when {
                        plannedIds == currentIds -> plan
                        currentIds.all { it in plannedIds } -> plan.copy(
                            positions = cappedPositionsForExistingBasket(
                                positions = plan.positions.filter { it.assetId in currentIds },
                                profile = profile,
                            ),
                            addedAssetIds = emptyList(),
                            removedAssetIds = emptyList(),
                        )
                        else -> null
                    }

                    ReferencePortfolioActionKind.EXTRAORDINARY_DELETION -> {
                        val removals = plan.removedAssetIds.filterTo(linkedSetOf()) { it in currentIds }
                        if (removals.isEmpty() || removals.size >= currentPositions.size) {
                            null
                        } else {
                            plan.copy(
                                positions = currentPositions.filterNot { it.assetId in removals }
                                    .normalizeBothWeights(),
                                addedAssetIds = emptyList(),
                                removedAssetIds = removals.sorted(),
                            )
                        }
                    }
                }
            }
            .sortedWith(PLAN_ORDER)
            .toList()
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
        val profile = BenchmarkMethodologyCompiler.compileSchd(definition)
        require(state.benchmarkRef == definition.ref)
        require(from >= state.asOf && to > from)
        require(referenceTradingFraction in 0.0..1.0)

        val mayApplyDuePlan = referenceTradingFraction > 0.0 ||
            isUsTradingDate(referenceDate) && ReferencePortfolioCalendar.intersectsUsRegularSession(from, to)
        val applied = if (mayApplyDuePlan) {
            applyDuePlan(state, profile, referenceDate)
        } else {
            state to null
        }
        val openingState = applied.first
        val record = applied.second

        if (referenceTradingFraction == 0.0) {
            var nextState = openingState.copy(asOf = to)
            if (isUsTradingDate(referenceDate) && reachesUsRegularClose(from, to)) {
                nextState = schedulePlansAtClose(nextState, profile, referenceDate)
            }
            return ReferencePortfolioAdvance(
                state = nextState,
                grossReferenceLogReturn = 0.0,
                record = record,
            )
        }

        val snapshots = snapshotMapForKnownDataAt(referenceDate)
        val trackedAssetIds = buildSet {
            openingState.positions.mapTo(this, ReferencePortfolioPosition::assetId)
            openingState.pendingPlans.forEach { plan ->
                plan.positions.mapTo(this, ReferencePortfolioPosition::assetId)
            }
        }
        val marketReturn = usMarketReturn(macro)
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
        if (reachesUsRegularClose(from, to)) {
            nextState = schedulePlansAtClose(nextState, profile, referenceDate)
        }
        return ReferencePortfolioAdvance(
            state = nextState,
            grossReferenceLogReturn = grossLogReturn,
            record = record,
        )
    }

    private fun applyDuePlan(
        state: ReferencePortfolioState,
        profile: EquityMethodologyProfile,
        referenceDate: LocalDate,
    ): Pair<ReferencePortfolioState, ReferencePortfolioRecord?> {
        val due = state.pendingPlans.filter { it.effectiveDate <= referenceDate }
        if (due.isEmpty()) return state to null
        // A market-wide zero-return day can leave multiple, differently dated plans overdue.
        // Apply exactly one in stable order so each ledger revision still has one record; the
        // next valid hour applies the next plan. Same-date plans are prevented by state validation.
        val plan = due.minWith(PLAN_ORDER)
        val effectiveDate = plan.effectiveDate
        val previousPositions = state.positions
        val turnover = oneWayTurnover(previousPositions, plan.positions)
        val revision = state.revision + 1L
        val nextReconstitution = if (plan.kind == ReferencePortfolioActionKind.ANNUAL_RECONSTITUTION) {
            nextReconstitutionDate(profile, effectiveDate)
        } else {
            state.nextReconstitutionDate
        }
        val nextScheduledRebalance = if (
            plan.kind == ReferencePortfolioActionKind.ANNUAL_RECONSTITUTION ||
            plan.kind == ReferencePortfolioActionKind.QUARTERLY_REBALANCE ||
            state.nextRebalanceDate <= effectiveDate
        ) {
            nextRebalanceDate(profile, effectiveDate)
        } else {
            state.nextRebalanceDate
        }
        val remainingPlans = reconcilePendingPlans(
            currentPositions = plan.positions,
            plans = state.pendingPlans - plan,
            afterEffectiveDate = effectiveDate,
            profile = profile,
        )
        val nextState = state.copy(
            positions = plan.positions,
            revision = revision,
            lastReconstitutionDate = if (plan.kind == ReferencePortfolioActionKind.ANNUAL_RECONSTITUTION) {
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
                snapshotMapForKnownDataAt(referenceDate),
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
            beforeCompositionHash = compositionHash(previousPositions),
            afterCompositionHash = compositionHash(plan.positions),
            turnoverRate = turnover,
            resultingConstituentCount = plan.positions.size,
            revision = revision,
        )
        return nextState to record
    }

    private fun schedulePlansAtClose(
        state: ReferencePortfolioState,
        profile: EquityMethodologyProfile,
        referenceDate: LocalDate,
    ): ReferencePortfolioState {
        var plans = state.pendingPlans

        val annualEffective = state.nextReconstitutionDate
        if (
            referenceDate == annualWeightReferenceDate(annualEffective) &&
            plans.none { it.kind == ReferencePortfolioActionKind.ANNUAL_RECONSTITUTION }
        ) {
            plans = plans + createAnnualPlan(state.copy(pendingPlans = plans), profile, annualEffective)
        }

        val quarterlyEffective = state.nextRebalanceDate
        if (
            quarterlyEffective.month.ordinal + 1 != profile.annualReconstitutionMonth &&
            referenceDate == quarterlyWeightReferenceDate(quarterlyEffective) &&
            plans.none { it.kind == ReferencePortfolioActionKind.QUARTERLY_REBALANCE }
        ) {
            plans = plans + createReweightPlan(
                state = state.copy(pendingPlans = plans),
                profile = profile,
                kind = ReferencePortfolioActionKind.QUARTERLY_REBALANCE,
                referenceDate = referenceDate,
                effectiveDate = quarterlyEffective,
            )
        }

        if (
            referenceDate == lastUsTradingDateOfMonth(referenceDate.year, referenceDate.month.ordinal + 1) &&
            plans.none { it.kind == ReferencePortfolioActionKind.EXTRAORDINARY_DELETION }
        ) {
            createMonthlyDeletionPlan(
                state = state.copy(pendingPlans = plans),
                referenceDate = referenceDate,
            )?.takeIf { deletionPlan -> deletionPlan.effectiveDate <= GameCalendar.CAMPAIGN_END_DATE }
                ?.let { deletionPlan ->
                    // A deletion changes the constituent set. Any still-pending daily cap whose
                    // effective date is not earlier was calculated for the obsolete basket and is
                    // superseded. This also prevents two plans becoming due on the same next-month
                    // opening after a penultimate-day T+2 breach.
                    plans = plans.filterNot { pending ->
                        pending.kind == ReferencePortfolioActionKind.DAILY_CAP_REBALANCE &&
                            pending.effectiveDate >= deletionPlan.effectiveDate
                    } + deletionPlan
                }
        }

        val dailyBreach = state.positions
            .filter { it.currentWeight > profile.dailyWeightThreshold }
            .sumOf(ReferencePortfolioPosition::currentWeight) > profile.dailyAggregateWeightLimit
        if (
            dailyBreach && !isDailyCapFreezeDate(profile, referenceDate) &&
            plans.none { it.kind == ReferencePortfolioActionKind.DAILY_CAP_REBALANCE } &&
            plans.none { it.kind == ReferencePortfolioActionKind.EXTRAORDINARY_DELETION }
        ) {
            val effectiveDate = addUsTradingDays(referenceDate, DAILY_RECAP_DELAY_TRADING_DAYS)
            if (
                effectiveDate <= GameCalendar.CAMPAIGN_END_DATE &&
                !isDailyCapFreezeDate(profile, effectiveDate)
            ) {
                plans = plans + createReweightPlan(
                    state = state.copy(pendingPlans = plans),
                    profile = profile,
                    kind = ReferencePortfolioActionKind.DAILY_CAP_REBALANCE,
                    referenceDate = referenceDate,
                    effectiveDate = effectiveDate,
                )
            }
        }

        return state.copy(pendingPlans = plans.sortedWith(PLAN_ORDER))
    }

    private fun createAnnualPlan(
        state: ReferencePortfolioState,
        profile: EquityMethodologyProfile,
        effectiveDate: LocalDate,
    ): ReferencePortfolioPlan {
        val selectionDate = thirdFriday(effectiveDate.year, 2)
        val weightReferenceDate = annualWeightReferenceDate(effectiveDate)
        val selected = selectConstituents(
            profile = profile,
            effectiveYear = effectiveDate.year,
            incumbentAssetIds = state.positions.mapTo(linkedSetOf(), ReferencePortfolioPosition::assetId),
        )
        val currentById = state.positions.associateBy(ReferencePortfolioPosition::assetId)
        val positions = positionsForSelection(
            selected = selected,
            rawFloatMarketValues = selected.associate { candidate ->
                val assetId = candidate.snapshot.definition.assetId
                assetId to (
                    currentById[assetId]?.referenceFloatMarketValue
                        ?: simulatedReferenceMarketValueBetween(
                            snapshot = candidate.snapshot,
                            fromDate = selectionDate,
                            throughDate = weightReferenceDate,
                        )
                    )
            },
            profile = profile,
            effectiveDate = effectiveDate,
            previousPositions = currentById,
        )
        return newPlan(
            state = state,
            kind = ReferencePortfolioActionKind.ANNUAL_RECONSTITUTION,
            selectionDate = selectionDate,
            weightReferenceDate = weightReferenceDate,
            effectiveDate = effectiveDate,
            positions = positions,
        )
    }

    private fun createReweightPlan(
        state: ReferencePortfolioState,
        profile: EquityMethodologyProfile,
        kind: ReferencePortfolioActionKind,
        referenceDate: LocalDate,
        effectiveDate: LocalDate,
    ): ReferencePortfolioPlan {
        require(kind == ReferencePortfolioActionKind.QUARTERLY_REBALANCE || kind == ReferencePortfolioActionKind.DAILY_CAP_REBALANCE)
        val snapshots = snapshotMapForKnownDataAt(referenceDate)
        val selected = state.positions.map { position ->
            RankedReferenceCandidate(
                snapshot = requireNotNull(snapshots[position.assetId]),
                compositeRank = position.selectionRank,
            )
        }
        val positions = positionsForSelection(
            selected = selected,
            rawFloatMarketValues = state.positions.associate {
                it.assetId to it.referenceFloatMarketValue
            },
            profile = profile,
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
        )
    }

    private fun createMonthlyDeletionPlan(
        state: ReferencePortfolioState,
        referenceDate: LocalDate,
    ): ReferencePortfolioPlan? {
        val removedIds = state.positions.asSequence()
            .map(ReferencePortfolioPosition::assetId)
            .filter { assetId ->
                dividendProgramSuspended(
                    equity = referenceEquityById.getValue(assetId),
                    year = referenceDate.year,
                    month = referenceDate.month.ordinal + 1,
                )
            }
            .take((state.positions.size - 1).coerceAtLeast(0))
            .toSortedSet()
        if (removedIds.isEmpty()) return null
        val positions = state.positions.filterNot { it.assetId in removedIds }
            .normalizeBothWeights()
        val effectiveDate = firstUsTradingDateOfNextMonth(referenceDate)
        return newPlan(
            state = state,
            kind = ReferencePortfolioActionKind.EXTRAORDINARY_DELETION,
            selectionDate = referenceDate,
            weightReferenceDate = referenceDate,
            effectiveDate = effectiveDate,
            positions = positions,
        )
    }

    private fun newPlan(
        state: ReferencePortfolioState,
        kind: ReferencePortfolioActionKind,
        selectionDate: LocalDate,
        weightReferenceDate: LocalDate,
        effectiveDate: LocalDate,
        positions: List<ReferencePortfolioPosition>,
    ): ReferencePortfolioPlan {
        val previousIds = state.positions.mapTo(linkedSetOf(), ReferencePortfolioPosition::assetId)
        val nextIds = positions.mapTo(linkedSetOf(), ReferencePortfolioPosition::assetId)
        return ReferencePortfolioPlan(
            id = "reference-plan:${state.portfolioId}:${kind.name}:$weightReferenceDate:$effectiveDate",
            portfolioId = state.portfolioId,
            benchmarkRef = state.benchmarkRef,
            kind = kind,
            selectionDate = selectionDate,
            weightReferenceDate = weightReferenceDate,
            effectiveDate = effectiveDate,
            positions = positions,
            addedAssetIds = (nextIds - previousIds).sorted(),
            removedAssetIds = (previousIds - nextIds).sorted(),
        )
    }

    private fun selectConstituents(
        profile: EquityMethodologyProfile,
        effectiveYear: Int,
        incumbentAssetIds: Set<String>,
    ): List<RankedReferenceCandidate> = SchdDividend100Policy.selectConstituents(
        profile = profile,
        snapshots = selectionSnapshotMapForEffectiveYear(effectiveYear).values,
        incumbentAssetIds = incumbentAssetIds,
    )

    private fun positionsForSelection(
        selected: List<RankedReferenceCandidate>,
        rawFloatMarketValues: Map<String, Double>,
        profile: EquityMethodologyProfile,
        effectiveDate: LocalDate,
        previousPositions: Map<String, ReferencePortfolioPosition>,
    ): List<ReferencePortfolioPosition> {
        require(selected.map { it.snapshot.definition.assetId }.toSet() == rawFloatMarketValues.keys)
        val sectors = selected.associate { candidate ->
            candidate.snapshot.definition.assetId to candidate.snapshot.definition.methodologySector
        }
        val targetWeights = cappedFmcWeights(
            rawFloatMarketValues = rawFloatMarketValues,
            methodologySectors = sectors,
            individualCap = profile.individualWeightCap,
            sectorCap = profile.sectorWeightCap,
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
        profile: EquityMethodologyProfile,
    ): List<ReferencePortfolioPosition> {
        require(positions.isNotEmpty()) { "빈 구성에는 상한 비중을 배정할 수 없습니다." }
        val targetWeights = cappedFmcWeights(
            rawFloatMarketValues = positions.associate { position ->
                position.assetId to position.referenceFloatMarketValue
            },
            methodologySectors = positions.associate { position ->
                position.assetId to referenceEquityById.getValue(position.assetId).methodologySector
            },
            individualCap = profile.individualWeightCap,
            sectorCap = profile.sectorWeightCap,
        )
        return positions.map { position ->
            val targetWeight = targetWeights.getValue(position.assetId)
            position.copy(
                currentWeight = targetWeight,
                targetWeight = targetWeight,
            )
        }.sortedBy(ReferencePortfolioPosition::assetId).repairBothWeightRounding()
    }

    private fun cappedFmcWeights(
        rawFloatMarketValues: Map<String, Double>,
        methodologySectors: Map<String, MethodologyEquitySector>,
        individualCap: Double,
        sectorCap: Double,
    ): Map<String, Double> = SchdDividend100Policy.cappedFloatMarketCapWeights(
        rawFloatMarketValues = rawFloatMarketValues,
        methodologySectors = methodologySectors,
        individualCap = individualCap,
        sectorCap = sectorCap,
    )

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

    private fun snapshotMapForKnownDataAt(date: LocalDate): Map<String, SimulatedReferenceEquitySnapshot> {
        val selectionDate = thirdFriday(date.year, 2)
        return if (date >= selectionDate && date.year > REFERENCE_BASE_YEAR) {
            selectionSnapshotMapForEffectiveYear(date.year)
        } else {
            snapshotMapForYear((date.year - 1).coerceAtLeast(REFERENCE_BASE_YEAR))
        }
    }

    /**
     * The four accounting inputs and dividend-payment history stay frozen at prior year-end.
     * Only FMC, three-month liquidity and indicated annual dividend yield are advanced to the
     * official February selection date.
     */
    private fun selectionSnapshotMapForEffectiveYear(
        effectiveYear: Int,
    ): Map<String, SimulatedReferenceEquitySnapshot> {
        require(effectiveYear in (REFERENCE_BASE_YEAR + 1)..MAX_SCENARIO_YEAR)
        return selectionSnapshotByIdCache.getOrPut(effectiveYear) {
            val priorYear = effectiveYear - 1
            val selectionDate = thirdFriday(effectiveYear, 2)
            snapshotMapForYear(priorYear).mapValues { (_, priorSnapshot) ->
                val equity = priorSnapshot.definition
                val currentMarketCap = simulatedReferenceMarketValueAt(
                    snapshot = priorSnapshot,
                    snapshotYear = priorYear,
                    date = selectionDate,
                )
                val liquidityRandom = DeterministicRandom.keyed(
                    seed,
                    "fund-selection-liquidity:${equity.assetId}:$effectiveYear",
                )
                val priorTurnover = priorSnapshot.averageDailyValueTraded /
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
                val suspendedBySelection = (1..(selectionDate.month.ordinal + 1)).any { month ->
                    dividendProgramSuspended(equity, effectiveYear, month)
                }
                if (suspendedBySelection) indicatedCashFactor *= SUSPENDED_DIVIDEND_CASH_FACTOR
                val currentYield = (
                    priorSnapshot.indicatedDividendYield * priorSnapshot.floatMarketCap /
                        currentMarketCap * indicatedCashFactor
                    ).coerceIn(MIN_INDICATED_DIVIDEND_YIELD, MAX_INDICATED_DIVIDEND_YIELD)
                priorSnapshot.copy(
                    floatMarketCap = currentMarketCap,
                    averageDailyValueTraded = currentMarketCap * currentTurnover,
                    indicatedDividendYield = currentYield,
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
        var dividendYears = equity.baseDividendPaymentYears
        var dividendYield = equity.baseDividendYield
        var fcfToDebt = equity.baseFreeCashFlowToDebt
        var roe = equity.baseReturnOnEquity
        var dividendGrowth = equity.baseFiveYearDividendGrowth
        var turnover = equity.baseDailyTurnover
        for (candidateYear in (REFERENCE_BASE_YEAR + 1)..year) {
            val random = DeterministicRandom.keyed(
                seed,
                "fund-reference-fundamentals:${equity.assetId}:$candidateYear",
            )
            val growth = (0.025 + equity.quality * 0.055 + random.nextGaussian() * 0.16)
                .coerceIn(-0.45, 0.55)
            marketCap = (marketCap * exp(growth)).coerceIn(MIN_REFERENCE_MARKET_CAP, MAX_REFERENCE_MARKET_CAP)
            val suspended = (1..12).any { month ->
                dividendProgramSuspended(equity, candidateYear, month)
            }
            dividendYears = if (suspended) 0 else dividendYears + 1
            val dividendCut = random.nextBoolean((0.050 - equity.quality * 0.038).coerceIn(0.008, 0.045))
            dividendYield = if (suspended) {
                (dividendYield * random.nextDouble(0.02, 0.20)).coerceIn(0.0001, 0.20)
            } else if (dividendCut) {
                (dividendYield * random.nextDouble(0.55, 0.90)).coerceIn(0.0005, 0.20)
            } else {
                (dividendYield * exp(random.nextGaussian() * 0.10)).coerceIn(0.0005, 0.20)
            }
            fcfToDebt = (fcfToDebt * exp(random.nextGaussian() * 0.13)).coerceIn(0.01, 8.0)
            roe = (roe + random.nextGaussian() * 0.018).coerceIn(-0.20, 0.75)
            dividendGrowth = if (suspended || dividendCut) {
                (dividendGrowth - random.nextDouble(0.06, 0.22)).coerceIn(-0.50, 0.45)
            } else {
                (dividendGrowth + random.nextGaussian() * 0.015).coerceIn(-0.30, 0.45)
            }
            turnover = (turnover * exp(random.nextGaussian() * 0.08)).coerceIn(0.0002, 0.08)
        }
        return SimulatedReferenceEquitySnapshot(
            definition = equity,
            floatMarketCap = marketCap,
            averageDailyValueTraded = marketCap * turnover,
            dividendPaymentYears = dividendYears,
            indicatedDividendYield = dividendYield,
            freeCashFlowToDebt = if (equity.debtFree) DEBT_FREE_RANK_VALUE else fcfToDebt,
            returnOnEquity = roe,
            fiveYearDividendGrowth = dividendGrowth,
        )
    }

    private fun dividendProgramSuspended(
        equity: SimulatedReferenceEquity,
        year: Int,
        month: Int,
    ): Boolean {
        val probability = (0.0009 - equity.quality * 0.00065).coerceIn(0.00015, 0.00085)
        return DeterministicRandom.keyed(
            seed,
            "fund-dividend-program-suspension:${equity.assetId}:$year:$month",
        ).nextBoolean(probability)
    }

    private fun buildReferenceEquities(): List<SimulatedReferenceEquity> =
        (1..REFERENCE_EQUITY_COUNT).map { index ->
            val assetId = "REF:US-BROAD:${index.toString().padStart(4, '0')}"
            val random = DeterministicRandom.keyed(seed, "fund-reference-definition:$assetId")
            val methodologySector = REFERENCE_SECTORS[random.nextInt(REFERENCE_SECTORS.size)]
            val quality = random.nextDouble(0.05, 0.98)
            val value = random.nextDouble(0.05, 0.98)
            val logCap = ln(MIN_BASE_MARKET_CAP) +
                random.nextDouble() * ln(MAX_BASE_MARKET_CAP / MIN_BASE_MARKET_CAP)
            SimulatedReferenceEquity(
                assetId = assetId,
                displaySymbol = "SIM${index.toString().padStart(4, '0')}",
                displayName = "시뮬레이션 미국 기준자산 ${index.toString().padStart(4, '0')}",
                sector = methodologySector.toGameSector(),
                methodologySector = methodologySector,
                baseFloatMarketCap = exp(logCap),
                baseDailyTurnover = random.nextDouble(0.001, 0.025),
                baseDividendPaymentYears = random.nextInt(38) + 2,
                baseDividendYield = random.nextDouble(0.004, 0.085),
                baseFreeCashFlowToDebt = random.nextDouble(0.04, 1.80),
                baseReturnOnEquity = random.nextDouble(0.03, 0.42),
                baseFiveYearDividendGrowth = random.nextDouble(-0.02, 0.24),
                beta = random.nextDouble(0.55, 1.45),
                annualVolatility = random.nextDouble(0.11, 0.38),
                quality = quality,
                value = value,
                debtFree = random.nextBoolean(0.08),
            )
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

    private fun compositionHash(positions: List<ReferencePortfolioPosition>): String {
        var hash = 0xCBF29CE484222325uL
        positions.sortedBy(ReferencePortfolioPosition::assetId).forEach { position ->
            val value = "${position.assetId}:${position.targetWeight.toBits()}"
            value.forEach { character ->
                hash = hash xor character.code.toULong()
                hash *= 0x100000001B3uL
            }
        }
        return hash.toString(16).padStart(16, '0').takeLast(16)
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

    private fun usMarketReturn(macro: MacroEnvironment): Double =
        macro.regionalEtfHourlyReturns?.get(EtfExposureRegion.UNITED_STATES)
            ?: macro.marketHourlyReturns.filterKeys(Market::isUnitedStates).values
                .takeIf(Collection<Double>::isNotEmpty)
                ?.average()
            ?: 0.0

    private fun nextReconstitutionDate(
        profile: EquityMethodologyProfile,
        date: LocalDate,
    ): LocalDate = ReferencePortfolioCalendar.nextReconstitutionDate(profile, date)

    private fun nextRebalanceDate(
        profile: EquityMethodologyProfile,
        date: LocalDate,
    ): LocalDate = ReferencePortfolioCalendar.nextRebalanceDate(profile, date)

    private fun annualWeightReferenceDate(effectiveDate: LocalDate): LocalDate =
        ReferencePortfolioCalendar.annualWeightReferenceDate(effectiveDate)

    private fun quarterlyWeightReferenceDate(effectiveDate: LocalDate): LocalDate =
        ReferencePortfolioCalendar.quarterlyWeightReferenceDate(effectiveDate)

    private fun thirdFriday(year: Int, month: Int): LocalDate =
        ReferencePortfolioCalendar.thirdFriday(year, month)

    private fun isDailyCapFreezeDate(profile: EquityMethodologyProfile, date: LocalDate): Boolean =
        ReferencePortfolioCalendar.isDailyCapFreezeDate(profile, date)

    private fun addUsTradingDays(date: LocalDate, days: Int): LocalDate =
        ReferencePortfolioCalendar.addUsTradingDays(date, days)

    private fun lastUsTradingDateOfMonth(year: Int, month: Int): LocalDate =
        ReferencePortfolioCalendar.lastUsTradingDateOfMonth(year, month)

    private fun firstUsTradingDateOfNextMonth(date: LocalDate): LocalDate =
        ReferencePortfolioCalendar.firstUsTradingDateOfNextMonth(date)

    private fun isUsTradingDate(date: LocalDate): Boolean =
        ReferencePortfolioCalendar.isUsTradingDate(date)

    private fun reachesUsRegularClose(from: Instant, to: Instant): Boolean {
        return ReferencePortfolioCalendar.reachesUsRegularClose(from, to)
    }

    companion object {
        fun portfolioIdFor(ref: BenchmarkRef): String =
            ReferencePortfolioState.portfolioIdFor(ref)

        /** 런타임과 저장 검증이 같은 독립 RNG stream을 쓰게 하는 유일한 생성 경계다. */
        fun forCampaignSeed(campaignSeed: Long): ReferencePortfolioEngine = ReferencePortfolioEngine(
            DeterministicRandom.mixSeed(campaignSeed, CAMPAIGN_STREAM_ID),
        )

        private const val CAMPAIGN_STREAM_ID: Long = 0x46554E44504F5254L
        private const val REFERENCE_BASE_YEAR: Int = 2025
        private const val MAX_SCENARIO_YEAR: Int = 2040
        private const val REFERENCE_EQUITY_COUNT: Int = 2_500
        private const val DAILY_RECAP_DELAY_TRADING_DAYS: Int = 2
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
        private const val MIN_INDICATED_DIVIDEND_YIELD: Double = 0.0001
        private const val MAX_INDICATED_DIVIDEND_YIELD: Double = 0.20
        private const val SUSPENDED_DIVIDEND_CASH_FACTOR: Double = 0.05
        private const val DEBT_FREE_RANK_VALUE: Double = 1_000_000.0
        private val PLAN_ORDER = compareBy<ReferencePortfolioPlan>(ReferencePortfolioPlan::effectiveDate)
            .thenBy(ReferencePortfolioPlan::id)

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
