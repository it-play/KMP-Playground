package com.amond.kmpbook.domain.simulation.reference

import com.amond.kmpbook.domain.model.fund.BenchmarkDefinition
import com.amond.kmpbook.domain.model.fund.BenchmarkEngineKind
import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import com.amond.kmpbook.domain.model.fund.EquityEligibleUniverse
import com.amond.kmpbook.domain.model.fund.EquityRebalanceCalendar
import com.amond.kmpbook.domain.model.fund.EquityReferenceProfile
import com.amond.kmpbook.domain.model.fund.EquityReferenceRegion
import com.amond.kmpbook.domain.model.fund.EquityReferenceWeightingModel
import com.amond.kmpbook.domain.model.fund.EquitySectorPolicy
import com.amond.kmpbook.domain.model.fund.EquityStylePolicy
import com.amond.kmpbook.domain.model.fund.MethodologyEquitySector
import com.amond.kmpbook.domain.model.fund.ReferenceCatalogComplexityLimits
import com.amond.kmpbook.domain.model.instrument.EtfExposureRegion
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.market.Sector
import com.amond.kmpbook.domain.model.reference.EquityReferenceAssetIdentity
import com.amond.kmpbook.domain.model.reference.EquityReferenceActionKind
import com.amond.kmpbook.domain.model.reference.EquityReferenceBook
import com.amond.kmpbook.domain.model.reference.EquityReferenceBookAdvance
import com.amond.kmpbook.domain.model.reference.EquityReferenceFactorExposure
import com.amond.kmpbook.domain.model.reference.EquityReferencePosition
import com.amond.kmpbook.domain.model.reference.EquityReferenceRebalanceRecord
import com.amond.kmpbook.domain.model.reference.EquityReferenceState
import com.amond.kmpbook.domain.model.reference.EquityReferenceStyleFactor
import com.amond.kmpbook.domain.simulation.market.MacroEnvironment
import com.amond.kmpbook.domain.simulation.price.DeterministicRandom
import com.amond.kmpbook.domain.time.DefaultMarketHolidays
import com.amond.kmpbook.domain.time.GameCalendar
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Shared rules-based engine for provisional equity references.
 *
 * Constituents and rebalance ledgers are explicit, while hourly returns are evaluated from a
 * vectorized country/sector/style/shared-residual exposure compiled at rebalance. This keeps the
 * benchmark catalog bounded without recomputing every representative constituent on every game hour.
 * Product fees, tracking error, leverage, FX hedges and option overlays are intentionally absent.
 */
class EquityReferenceBookEngine private constructor(
    private val seed: Long,
    private val repository: EquityReferenceUniverseRepository,
) {
    private val profileFingerprintCache =
        mutableMapOf<BenchmarkRef, MutableMap<EquityReferenceProfile, String>>()
    private val selectionPolicyKeyCache = mutableMapOf<EquityReferenceProfile, String>()
    private val selectionCache = mutableMapOf<EquityReferenceSelectionCacheKey, EquityReferenceSelection>()
    private val thematicAffinityCache = mutableMapOf<String, Double>()
    private val activeConvictionCache = mutableMapOf<String, Double>()
    private val closedDatesCache = mutableMapOf<Pair<Market, Int>, Set<LocalDate>>()
    private var cachedDefinitionSource: Collection<BenchmarkDefinition>? = null
    private var cachedDefinitionsByRef: Map<BenchmarkRef, BenchmarkDefinition>? = null
    private var selectionCacheFrom: Instant? = null
    private var selectionCacheTo: Instant? = null
    fun hasCanonicalReferenceIdentity(position: EquityReferencePosition): Boolean =
        repository.identity(position.assetId)?.let { identity ->
            identity.region == position.region &&
                identity.countryCode == position.countryCode &&
                identity.sector == position.sector
        } == true

    /** Canonical annual constituent data check for persistence validators. */
    fun hasCanonicalPositionSnapshot(
        position: EquityReferencePosition,
        snapshotYear: Int,
    ): Boolean = runCatching {
        val snapshot = requireNotNull(repository.snapshot(position.assetId, snapshotYear))
        hasCanonicalReferenceIdentity(position) &&
            position.indicatedAnnualDividendYield.toBits() == snapshot.dividendYield.toBits()
    }.getOrDefault(false)

    /** Recomputes the score stored at the most recent selection or reweight action. */
    fun canonicalSelectionScore(
        ref: BenchmarkRef,
        profile: EquityReferenceProfile,
        assetId: String,
        scoreYear: Int,
        incumbent: Boolean,
    ): Double = selectionScore(
        ref = ref,
        profile = profile,
        snapshot = requireNotNull(repository.snapshot(assetId, scoreYear)),
        year = scoreYear,
        incumbent = incumbent,
    )

    /** Checks the representative-count layout independently of mutable current weights. */
    fun hasCanonicalRepresentativeLayout(
        state: EquityReferenceState,
        profile: EquityReferenceProfile,
    ): Boolean = runCatching {
        val representedTotal = minOf(
            declaredOrDefaultCount(state.benchmarkRef, profile),
            state.eligibleCandidateCount,
        )
        val base = representedTotal / state.positions.size
        val remainder = representedTotal % state.positions.size
        state.positions.sumOf(EquityReferencePosition::representedConstituentCount) ==
            representedTotal &&
            state.positions.count { it.representedConstituentCount == base + 1 } == remainder &&
            state.positions.count { it.representedConstituentCount == base } ==
            state.positions.size - remainder
    }.getOrDefault(false)

    fun referenceAssetIdentity(assetId: String): EquityReferenceAssetIdentity? =
        repository.identity(assetId)

    fun hasCanonicalUniverseMetadata(
        state: EquityReferenceState,
        profile: EquityReferenceProfile,
    ): Boolean = runCatching {
        val year = state.lastSelectionDate.year.coerceIn(FIRST_YEAR, LAST_YEAR)
        val regionCandidates = repository.snapshots(profile.region, profile.countryCodes, year)
        val sectorEligible = when (profile.sectorPolicy) {
            EquitySectorPolicy.INCLUDED_ONLY ->
                regionCandidates.filter { it.sector in profile.includedSectors }
            EquitySectorPolicy.ALL_SECTORS,
            EquitySectorPolicy.THEMATIC_CROSS_SECTOR,
            EquitySectorPolicy.UNVERIFIED,
            -> regionCandidates
        }
        state.universeModelVersion == repository.universeModelVersion &&
            state.universeFingerprint == repository.universeFingerprint &&
            state.resolvedCountryCodes == canonicalResolvedCountryCodes(profile) &&
            state.representativeBasketLimit ==
            ReferenceCatalogComplexityLimits.representativeLimit(profile) &&
            state.eligibleCandidateCount == applyUniversePolicy(
                state.benchmarkRef,
                profile,
                sectorEligible,
            ).size
    }.getOrDefault(false)

    fun factorExposure(
        ref: BenchmarkRef,
        profile: EquityReferenceProfile,
        positions: List<EquityReferencePosition>,
        year: Int,
    ): EquityReferenceFactorExposure {
        require(year in FIRST_YEAR..LAST_YEAR)
        require(positions.isNotEmpty() && positions.all(::hasCanonicalReferenceIdentity))
        val snapshots = positions.associate { position ->
            position.assetId to requireNotNull(repository.snapshot(position.assetId, year))
        }
        return factorExposure(ref, profile, positions, snapshots, year)
    }

    fun nextScheduledDate(
        ref: BenchmarkRef,
        profile: EquityReferenceProfile,
        kind: EquityReferenceActionKind,
        after: LocalDate,
    ): LocalDate = when (kind) {
        EquityReferenceActionKind.RECONSTITUTION -> nextScheduledDate(
            ref = ref,
            region = profile.region,
            calendar = profile.selectionCalendar,
            configuredMonths = profile.selectionMonths,
            scheduleKey = "selection",
            after = after,
        )
        EquityReferenceActionKind.REWEIGHT -> nextScheduledDate(
            ref = ref,
            region = profile.region,
            calendar = profile.reweightCalendar,
            configuredMonths = profile.reweightMonths,
            scheduleKey = "reweight",
            after = after,
        )
    }

    fun initialBook(
        definitions: Collection<BenchmarkDefinition>,
        atDate: LocalDate,
        at: Instant,
    ): EquityReferenceBook {
        prepareSelectionCache(at, at)
        val definitionsByRef = validatedDefinitions(definitions)
        val states = linkedMapOf<BenchmarkRef, EquityReferenceState>()
        definitionsByRef.toSortedMap().forEach { (ref, definition) ->
            val profile = requireNotNull(definition.equityReferenceProfile)
            val resolvedCountries = canonicalResolvedCountryCodes(profile)
            val bootstrap = bootstrap(ref, profile, atDate, at)
            val selection = bootstrap.selection
            val compositionHash = canonicalCompositionHash(selection.positions)
            states[ref] = EquityReferenceState(
                benchmarkRef = ref,
                region = profile.region,
                resolvedCountryCodes = resolvedCountries,
                themeId = profile.themeId,
                positions = selection.positions,
                factorExposure = selection.factorExposure,
                revision = 0L,
                lastSelectionDate = bootstrap.lastSelectionDate,
                nextSelectionDate = bootstrap.nextSelectionDate,
                lastReweightDate = bootstrap.lastReweightDate,
                nextReweightDate = bootstrap.nextReweightDate,
                estimatedAnnualIncomeYield = incomeYield(selection.positions),
                declaredTargetConstituentCount = profile.targetConstituentCount,
                eligibleCandidateCount = selection.eligibleCandidateCount,
                representativeBasketLimit = ReferenceCatalogComplexityLimits.representativeLimit(profile),
                profileFingerprint = canonicalProfileFingerprint(ref, profile),
                universeModelVersion = repository.universeModelVersion,
                universeFingerprint = repository.universeFingerprint,
                compositionHash = compositionHash,
                asOf = at,
            )
        }
        return EquityReferenceBook.fromOwnedStates(states)
    }

    fun advanceHour(
        book: EquityReferenceBook,
        definitions: Collection<BenchmarkDefinition>,
        macro: MacroEnvironment,
        marketTradingFractions: Map<Market, Double>,
        from: Instant,
        to: Instant,
    ): EquityReferenceBookAdvance {
        require(from == book.asOf)
        val elapsed = to - from
        require(elapsed.isPositive() && elapsed <= MAX_ADVANCE_DURATION)
        prepareSelectionCache(from, to)
        val definitionsByRef = validatedDefinitions(definitions)
        require(definitionsByRef.keys == book.states.keys)
        book.states.forEach { (ref, state) ->
            val profile = requireNotNull(definitionsByRef.getValue(ref).equityReferenceProfile)
            require(state.profileFingerprint == canonicalProfileFingerprint(ref, profile))
            require(state.universeModelVersion == repository.universeModelVersion)
            require(state.universeFingerprint == repository.universeFingerprint)
        }
        val countries = book.states.values.flatMapTo(linkedSetOf()) { it.resolvedCountryCodes }
        val requiredMarkets = countries.mapTo(linkedSetOf()) { country ->
            marketForRegion(regionForCountry(country))
        }
        require(marketTradingFractions.values.all { it.isFinite() && it in 0.0..1.0 })
        require(requiredMarkets.all(marketTradingFractions::containsKey)) {
            "Effective equity-reference trading fractions are required for $requiredMarkets."
        }
        val frame = factorFrame(countries, marketTradingFractions, macro, from, to)
        val crossedCloseDatesByRegion = book.states.values
            .map(EquityReferenceState::region)
            .distinct()
            .associateWith { region ->
                if (marketTradingFractions.getValue(marketForRegion(region)) == 0.0) {
                    emptyList()
                } else {
                    tradingCloseDatesCrossed(region, from, to).filter { date ->
                        isWeightMaterializationDate(region, date)
                    }
                }
            }
        val crossedCloseDates = crossedCloseDatesByRegion.values.flatten().distinct().sorted()
        val dailyDriftFrames = if (crossedCloseDates.isEmpty()) {
            emptyMap()
        } else {
            val assetIds = book.states.values.asSequence()
                .flatMap { state -> state.positions.asSequence().map(EquityReferencePosition::assetId) }
                .distinct()
                .sorted()
                .toList()
            crossedCloseDates.associateWith { date ->
                dailyDriftFrame(countries, assetIds, macro, date)
            }
        }
        val nextStates = linkedMapOf<BenchmarkRef, EquityReferenceState>()
        val returns = linkedMapOf<BenchmarkRef, Double>()
        val incomeYields = linkedMapOf<BenchmarkRef, Double>()
        val records = mutableListOf<EquityReferenceRebalanceRecord>()

        book.states.forEach { (ref, state) ->
            val profile = requireNotNull(definitionsByRef.getValue(ref).equityReferenceProfile)
            val grossLogReturn = referenceLogReturn(ref, state, profile, frame, macro, from, to)
            val driftedState = driftAtCrossedCloses(
                ref = ref,
                profile = profile,
                state = state,
                crossedCloseDates = crossedCloseDatesByRegion.getValue(profile.region),
                dailyDriftFrames = dailyDriftFrames,
            )
            val selectionDue = crossesRebalanceClose(
                profile.region,
                state.nextSelectionDate,
                from,
                to,
            )
            val reweightDue = crossesRebalanceClose(
                profile.region,
                state.nextReweightDate,
                from,
                to,
            )
            val next = if (selectionDue) {
                val due = state.nextSelectionDate
                val selection = select(
                    ref = ref,
                    profile = profile,
                    year = due.year.coerceIn(FIRST_YEAR, LAST_YEAR),
                    effectiveDate = due,
                    incumbentPositions = driftedState.positions.associateBy(
                        EquityReferencePosition::assetId,
                    ),
                )
                val nextRevision = driftedState.revision + 1L
                val nextHash = canonicalCompositionHash(selection.positions)
                val record = rebalanceRecord(
                    ref = ref,
                    previous = driftedState,
                    kind = EquityReferenceActionKind.RECONSTITUTION,
                    positions = selection.positions,
                    effectiveDate = due,
                    effectiveAt = to,
                    compositionHashAfter = nextHash,
                    revision = nextRevision,
                )
                records += record
                val coincidentReweight = reweightDue && state.nextReweightDate == due
                driftedState.copy(
                    positions = selection.positions,
                    factorExposure = selection.factorExposure,
                    revision = nextRevision,
                    lastSelectionDate = due,
                    nextSelectionDate = nextScheduledDate(
                        ref = ref,
                        region = profile.region,
                        calendar = profile.selectionCalendar,
                        configuredMonths = profile.selectionMonths,
                        scheduleKey = "selection",
                        after = due,
                    ),
                    lastReweightDate = if (coincidentReweight) due else driftedState.lastReweightDate,
                    nextReweightDate = if (coincidentReweight) {
                        nextScheduledDate(
                            ref = ref,
                            region = profile.region,
                            calendar = profile.reweightCalendar,
                            configuredMonths = profile.reweightMonths,
                            scheduleKey = "reweight",
                            after = due,
                        )
                    } else {
                        driftedState.nextReweightDate
                    },
                    estimatedAnnualIncomeYield = incomeYield(selection.positions),
                    eligibleCandidateCount = selection.eligibleCandidateCount,
                    compositionHash = nextHash,
                    asOf = to,
                )
            } else if (reweightDue) {
                val due = state.nextReweightDate
                val selection = reweight(
                    ref = ref,
                    profile = profile,
                    positions = driftedState.positions,
                    eligibleCandidateCount = driftedState.eligibleCandidateCount,
                    year = due.year.coerceIn(FIRST_YEAR, LAST_YEAR),
                )
                val nextRevision = driftedState.revision + 1L
                val nextHash = canonicalCompositionHash(selection.positions)
                records += rebalanceRecord(
                    ref = ref,
                    previous = driftedState,
                    kind = EquityReferenceActionKind.REWEIGHT,
                    positions = selection.positions,
                    effectiveDate = due,
                    effectiveAt = to,
                    compositionHashAfter = nextHash,
                    revision = nextRevision,
                )
                driftedState.copy(
                    positions = selection.positions,
                    factorExposure = selection.factorExposure,
                    revision = nextRevision,
                    lastReweightDate = due,
                    nextReweightDate = nextScheduledDate(
                        ref = ref,
                        region = profile.region,
                        calendar = profile.reweightCalendar,
                        configuredMonths = profile.reweightMonths,
                        scheduleKey = "reweight",
                        after = due,
                    ),
                    estimatedAnnualIncomeYield = incomeYield(selection.positions),
                    compositionHash = nextHash,
                    asOf = to,
                )
            } else {
                driftedState.withAsOf(to)
            }
            nextStates[ref] = next
            returns[ref] = grossLogReturn
            incomeYields[ref] = next.estimatedAnnualIncomeYield
        }
        return EquityReferenceBookAdvance.fromOwnedCollections(
            book = EquityReferenceBook.fromOwnedStates(nextStates),
            grossReferenceLogReturns = returns,
            estimatedAnnualIncomeYields = incomeYields,
            rebalanceRecords = records.sortedWith(
                compareBy<EquityReferenceRebalanceRecord> { it.benchmarkRef }.thenBy { it.revision },
            ),
        )
    }

    /**
     * Reconstructs the canonical pre-campaign basket without emitting historical ledger rows.
     * Selection starts at the last effective selection close, each intervening market close drifts
     * current weights, and only the configured reweight closes reset targets.
     */
    private fun bootstrap(
        ref: BenchmarkRef,
        profile: EquityReferenceProfile,
        atDate: LocalDate,
        at: Instant,
    ): EquityReferenceBootstrap {
        val selectionSchedule = scheduleAround(
            ref = ref,
            region = profile.region,
            calendar = profile.selectionCalendar,
            configuredMonths = profile.selectionMonths,
            scheduleKey = "selection",
            atDate = atDate,
            at = at,
        )
        val reweightSchedule = scheduleAround(
            ref = ref,
            region = profile.region,
            calendar = profile.reweightCalendar,
            configuredMonths = profile.reweightMonths,
            scheduleKey = "reweight",
            atDate = atDate,
            at = at,
        )
        var selection = select(
            ref = ref,
            profile = profile,
            year = selectionSchedule.first.year.coerceIn(FIRST_YEAR, LAST_YEAR),
            effectiveDate = selectionSchedule.first,
            incumbentPositions = emptyMap(),
        )
        val reweightDates = scheduledDates(
            ref = ref,
            region = profile.region,
            calendar = profile.reweightCalendar,
            configuredMonths = profile.reweightMonths,
            scheduleKey = "reweight",
            fromYear = selectionSchedule.first.year,
            throughYear = atDate.year,
        ).filterTo(linkedSetOf()) { date ->
            date > selectionSchedule.first && regularCloseInstant(marketForRegion(profile.region), date) <= at
        }
        var lastActionDate = selectionSchedule.first
        reweightDates.forEach { reweightDate ->
            selection = driftBootstrapSelection(
                ref = ref,
                profile = profile,
                selection = selection,
                afterDate = lastActionDate,
                throughDate = reweightDate,
            )
            selection = reweight(
                ref = ref,
                profile = profile,
                positions = selection.positions,
                eligibleCandidateCount = selection.eligibleCandidateCount,
                year = reweightDate.year.coerceIn(FIRST_YEAR, LAST_YEAR),
            )
            lastActionDate = reweightDate
        }
        val lastCompletedCloseDate = lastCompletedTradingDate(profile.region, atDate, at)
        if (lastCompletedCloseDate > lastActionDate) {
            selection = driftBootstrapSelection(
                ref = ref,
                profile = profile,
                selection = selection,
                afterDate = lastActionDate,
                throughDate = lastCompletedCloseDate,
            )
        }
        return EquityReferenceBootstrap(
            selection = selection,
            lastSelectionDate = selectionSchedule.first,
            nextSelectionDate = selectionSchedule.second,
            lastReweightDate = reweightSchedule.first,
            nextReweightDate = reweightSchedule.second,
        )
    }

    private fun driftBootstrapSelection(
        ref: BenchmarkRef,
        profile: EquityReferenceProfile,
        selection: EquityReferenceSelection,
        afterDate: LocalDate,
        throughDate: LocalDate,
    ): EquityReferenceSelection {
        require(throughDate > afterDate)
        val market = marketForRegion(profile.region)
        val tradingDays = tradingDateCount(market, afterDate, throughDate)
        if (tradingDays == 0) return selection
        val periodYearFraction = tradingDays / TRADING_DAYS_PER_YEAR
        val snapshots = selection.positions.associate { position ->
            position.assetId to requireNotNull(
                repository.snapshot(
                    position.assetId,
                    throughDate.year.coerceIn(FIRST_YEAR, LAST_YEAR),
                ),
            )
        }
        val countries = selection.positions.map(EquityReferencePosition::countryCode).distinct()
        val countryShocks = countries.associateWith { country ->
            BOOTSTRAP_COUNTRY_VOLATILITY * sqrt(periodYearFraction) *
                DeterministicRandom.keyed(
                    seed,
                    "equity-bootstrap-country:$country:$afterDate:$throughDate",
                ).nextGaussian()
        }
        val sectors = selection.positions.map(EquityReferencePosition::sector).distinct()
        val sectorShocks = sectors.associateWith { sector ->
            BOOTSTRAP_SECTOR_VOLATILITY * sqrt(periodYearFraction) *
                DeterministicRandom.keyed(
                    seed,
                    "equity-bootstrap-sector:$sector:$afterDate:$throughDate",
                ).nextGaussian()
        }
        val numerators = DoubleArray(selection.positions.size)
        var total = 0.0
        selection.positions.forEachIndexed { index, position ->
            val snapshot = snapshots.getValue(position.assetId)
            val styleCarry = periodYearFraction * (
                .020 * snapshot.value + .012 * snapshot.growth + .018 * snapshot.quality
                )
            val idiosyncratic = snapshot.annualVolatility * BOOTSTRAP_IDIOSYNCRATIC_LOADING *
                sqrt(periodYearFraction) * DeterministicRandom.keyed(
                    seed,
                    "equity-bootstrap-asset:${snapshot.assetId}:$afterDate:$throughDate",
                ).nextGaussian()
            val logReturn = (
                countryShocks.getValue(snapshot.countryCode) +
                    sectorShocks.getValue(snapshot.sector) + styleCarry + idiosyncratic
                ).coerceIn(-MAX_BOOTSTRAP_PERIOD_LOG_MOVE, MAX_BOOTSTRAP_PERIOD_LOG_MOVE)
            val numerator = position.weight * exp(logReturn)
            numerators[index] = numerator
            total += numerator
        }
        require(total.isFinite() && total > 0.0)
        val positions = selection.positions.mapIndexed { index, position ->
            val snapshot = snapshots.getValue(position.assetId)
            position.copy(
                weight = numerators[index] / total,
                indicatedAnnualDividendYield = snapshot.dividendYield,
            )
        }.repairWeightRounding().sortedBy(EquityReferencePosition::assetId)
        return EquityReferenceSelection(
            positions = positions,
            factorExposure = factorExposure(
                ref,
                profile,
                positions,
                snapshots,
                throughDate.year.coerceIn(FIRST_YEAR, LAST_YEAR),
            ),
            eligibleCandidateCount = selection.eligibleCandidateCount,
        )
    }

    private fun driftAtCrossedCloses(
        ref: BenchmarkRef,
        profile: EquityReferenceProfile,
        state: EquityReferenceState,
        crossedCloseDates: List<LocalDate>,
        dailyDriftFrames: Map<LocalDate, EquityReferenceDailyDriftFrame>,
    ): EquityReferenceState {
        return crossedCloseDates.fold(state) { current, date ->
            driftStateAtClose(
                ref = ref,
                profile = profile,
                state = current,
                frame = dailyDriftFrames.getValue(date),
                date = date,
            )
        }
    }

    private fun driftStateAtClose(
        ref: BenchmarkRef,
        profile: EquityReferenceProfile,
        state: EquityReferenceState,
        frame: EquityReferenceDailyDriftFrame,
        date: LocalDate,
    ): EquityReferenceState {
        val snapshotYear = date.year.coerceIn(FIRST_YEAR, LAST_YEAR)
        val snapshots = state.positions.associate { position ->
            position.assetId to requireNotNull(repository.snapshot(position.assetId, snapshotYear))
        }
        val numerators = DoubleArray(state.positions.size)
        var total = 0.0
        state.positions.forEachIndexed { index, position ->
            val snapshot = snapshots.getValue(position.assetId)
            val numerator = position.weight * exp(liveAssetDailyLogReturn(snapshot, frame))
            numerators[index] = numerator
            total += numerator
        }
        require(total.isFinite() && total > 0.0)
        val positions = state.positions.mapIndexed { index, position ->
            val snapshot = snapshots.getValue(position.assetId)
            position.copy(
                weight = numerators[index] / total,
                indicatedAnnualDividendYield = snapshot.dividendYield,
            )
        }.repairWeightRounding().sortedBy(EquityReferencePosition::assetId)
        return state.copy(
            positions = positions,
            factorExposure = factorExposure(ref, profile, positions, snapshots, snapshotYear),
            estimatedAnnualIncomeYield = incomeYield(positions),
            compositionHash = canonicalCompositionHash(positions),
        )
    }

    private fun dailyDriftFrame(
        countries: Set<String>,
        assetIds: List<String>,
        macro: MacroEnvironment,
        date: LocalDate,
    ): EquityReferenceDailyDriftFrame {
        val yearFraction = WEIGHT_MATERIALIZATION_YEAR_FRACTION
        val volatilityScale = sqrt(macro.volatilityRegime.coerceIn(.25, 4.0))
        val countryReturns = countries.sorted().associateWith { country ->
            LIVE_CLOSE_MARKET_LOADING * safeLog1p(
                regionalMarketSimpleReturn(regionForCountry(country), macro),
            ) + LIVE_COUNTRY_RESIDUAL_VOLATILITY * sqrt(yearFraction) * volatilityScale *
                DeterministicRandom.keyed(
                    seed,
                    "equity-close-country:$country:$date",
                ).nextGaussian()
        }
        val sectorReturns = MethodologyEquitySector.entries.associateWith { sector ->
            val supplied = macro.sectorHourlyReturns[marketSector(sector)] ?: 0.0
            LIVE_CLOSE_SECTOR_LOADING * safeLog1p(supplied) +
                LIVE_SECTOR_RESIDUAL_VOLATILITY * sqrt(yearFraction) * volatilityScale *
                DeterministicRandom.keyed(
                    seed,
                    "equity-close-sector:$sector:$date",
                ).nextGaussian()
        }
        fun styleNormal(style: String): Double = volatilityScale * DeterministicRandom.keyed(
            seed,
            "equity-close-style:$style:$date",
        ).nextGaussian()
        val idiosyncratic = assetIds.associateWith { assetId ->
            volatilityScale * DeterministicRandom.keyed(
                seed,
                "equity-close-asset:$assetId:$date",
            ).nextGaussian()
        }
        return EquityReferenceDailyDriftFrame(
            countryLogReturns = countryReturns,
            sectorLogReturns = sectorReturns,
            valueStandardNormal = styleNormal("value"),
            growthStandardNormal = styleNormal("growth"),
            qualityStandardNormal = styleNormal("quality"),
            assetIdiosyncraticStandardNormals = idiosyncratic,
        )
    }

    /** Asset/date keyed cross-sectional close return; the same anchor is shared by every benchmark. */
    private fun liveAssetDailyLogReturn(
        snapshot: EquityReferenceCandidateSnapshot,
        frame: EquityReferenceDailyDriftFrame,
    ): Double {
        val yearFraction = WEIGHT_MATERIALIZATION_YEAR_FRACTION
        val countryMove = frame.countryLogReturns.getValue(snapshot.countryCode)
        val sectorMove = frame.sectorLogReturns.getValue(snapshot.sector)
        val styleMove = yearFraction * (
            .020 * snapshot.value + .012 * snapshot.growth + .018 * snapshot.quality
            ) + LIVE_STYLE_VOLATILITY * sqrt(yearFraction) * (
            snapshot.value * frame.valueStandardNormal +
                snapshot.growth * frame.growthStandardNormal +
                snapshot.quality * frame.qualityStandardNormal
            ) / 3.0
        val idiosyncratic = snapshot.annualVolatility * LIVE_IDIOSYNCRATIC_LOADING *
            sqrt(yearFraction) * frame.assetIdiosyncraticStandardNormals.getValue(snapshot.assetId)
        return (countryMove + sectorMove + styleMove + idiosyncratic)
            .coerceIn(-MAX_DAILY_ANCHOR_LOG_MOVE, MAX_DAILY_ANCHOR_LOG_MOVE)
    }

    private fun select(
        ref: BenchmarkRef,
        profile: EquityReferenceProfile,
        year: Int,
        effectiveDate: LocalDate,
        incumbentPositions: Map<String, EquityReferencePosition>,
    ): EquityReferenceSelection {
        val key = EquityReferenceSelectionCacheKey(
            policyKey = selectionPolicyKey(profile),
            benchmarkScope = if (isShareableSelectionPolicy(profile)) null else ref,
            kind = EquityReferenceActionKind.RECONSTITUTION,
            year = year,
            effectiveDate = effectiveDate,
            incumbentIdentities = incumbentPositions.values
                .sortedBy(EquityReferencePosition::assetId)
                .map(::selectionIdentity),
        )
        return selectionCache.getOrPut(key) {
            selectUncached(ref, profile, year, effectiveDate, incumbentPositions)
        }
    }

    private fun selectUncached(
        ref: BenchmarkRef,
        profile: EquityReferenceProfile,
        year: Int,
        effectiveDate: LocalDate,
        incumbentPositions: Map<String, EquityReferencePosition>,
    ): EquityReferenceSelection {
        val regionCandidates = repository.snapshots(profile.region, profile.countryCodes, year)
        val sectorEligible = when (profile.sectorPolicy) {
            EquitySectorPolicy.INCLUDED_ONLY ->
                regionCandidates.filter { it.sector in profile.includedSectors }
            EquitySectorPolicy.ALL_SECTORS,
            EquitySectorPolicy.THEMATIC_CROSS_SECTOR,
            EquitySectorPolicy.UNVERIFIED,
            -> regionCandidates
        }
        val universeEligible = applyUniversePolicy(ref, profile, sectorEligible)
        require(universeEligible.isNotEmpty()) { "No representative candidates remain for $ref." }
        val desiredCount = declaredOrDefaultCount(ref, profile)
        val scoringPoolSize = minOf(
            universeEligible.size,
            maxOf(desiredCount, MAX_SELECTION_SCORE_POOL),
        )
        val scoringPool = if (scoringPoolSize == universeEligible.size) {
            universeEligible
        } else {
            (0 until scoringPoolSize).map { index ->
                universeEligible[index * universeEligible.size / scoringPoolSize]
            }
        }
        val scored = scoringPool.map { snapshot ->
            EquityReferenceScoredCandidate(
                snapshot = snapshot,
                score = selectionScore(
                    ref = ref,
                    profile = profile,
                    snapshot = snapshot,
                    year = year,
                    incumbent = snapshot.assetId in incumbentPositions,
                ),
            )
        }.sortedWith(
            compareByDescending<EquityReferenceScoredCandidate> { it.score }
                .thenBy { it.snapshot.assetId },
        )
        val methodologyPool = scored.take(minOf(desiredCount, scored.size))
        require(methodologyPool.isNotEmpty())
        if (profile.targetConstituentCount != null) {
            require(methodologyPool.size == desiredCount) {
                "Equity reference $ref declares $desiredCount constituents but only " +
                    "${methodologyPool.size} satisfy its typed universe."
            }
        }
        val anchorCount = minOf(
            methodologyPool.size,
            ReferenceCatalogComplexityLimits.representativeLimit(profile),
        )
        val anchors = (0 until anchorCount).map { index ->
            methodologyPool[index * methodologyPool.size / anchorCount]
        }
        require(anchors.map { it.snapshot.assetId }.distinct().size == anchors.size)
        val representedTotal = profile.targetConstituentCount ?: methodologyPool.size
        val representedCounts = representedCounts(anchors, representedTotal)
        val weights = modelWeights(profile, anchors, representedCounts)
        val positions = anchors.map { candidate ->
            val prior = incumbentPositions[candidate.snapshot.assetId]
            EquityReferencePosition(
                assetId = candidate.snapshot.assetId,
                region = candidate.snapshot.region,
                countryCode = candidate.snapshot.countryCode,
                sector = candidate.snapshot.sector,
                weight = weights.getValue(candidate.snapshot.assetId),
                targetWeight = weights.getValue(candidate.snapshot.assetId),
                representedConstituentCount = representedCounts.getValue(candidate.snapshot.assetId),
                selectionScore = candidate.score,
                indicatedAnnualDividendYield = candidate.snapshot.dividendYield,
                enteredOn = prior?.enteredOn ?: effectiveDate,
            )
        }.repairWeightRounding().sortedBy(EquityReferencePosition::assetId)
        val snapshotsById = anchors.associate { it.snapshot.assetId to it.snapshot }
        return EquityReferenceSelection(
            positions = positions,
            factorExposure = factorExposure(ref, profile, positions, snapshotsById, year),
            eligibleCandidateCount = universeEligible.size,
        )
    }

    private fun reweight(
        ref: BenchmarkRef,
        profile: EquityReferenceProfile,
        positions: List<EquityReferencePosition>,
        eligibleCandidateCount: Int,
        year: Int,
    ): EquityReferenceSelection {
        val key = EquityReferenceSelectionCacheKey(
            policyKey = selectionPolicyKey(profile),
            benchmarkScope = if (isShareableSelectionPolicy(profile)) null else ref,
            kind = EquityReferenceActionKind.REWEIGHT,
            year = year,
            effectiveDate = null,
            incumbentIdentities = positions.map(::selectionIdentity),
        )
        return selectionCache.getOrPut(key) {
            reweightUncached(ref, profile, positions, eligibleCandidateCount, year)
        }
    }

    private fun reweightUncached(
        ref: BenchmarkRef,
        profile: EquityReferenceProfile,
        positions: List<EquityReferencePosition>,
        eligibleCandidateCount: Int,
        year: Int,
    ): EquityReferenceSelection {
        val scored = positions.map { position ->
            val snapshot = requireNotNull(repository.snapshot(position.assetId, year))
            EquityReferenceScoredCandidate(
                snapshot = snapshot,
                score = selectionScore(
                    ref = ref,
                    profile = profile,
                    snapshot = snapshot,
                    year = year,
                    incumbent = true,
                ),
            )
        }
        val representedCounts = positions.associate {
            it.assetId to it.representedConstituentCount
        }
        val weights = modelWeights(profile, scored, representedCounts)
        val priorById = positions.associateBy(EquityReferencePosition::assetId)
        val positions = scored.map { candidate ->
            val prior = priorById.getValue(candidate.snapshot.assetId)
            prior.copy(
                weight = weights.getValue(candidate.snapshot.assetId),
                targetWeight = weights.getValue(candidate.snapshot.assetId),
                selectionScore = candidate.score,
                indicatedAnnualDividendYield = candidate.snapshot.dividendYield,
            )
        }.repairWeightRounding().sortedBy(EquityReferencePosition::assetId)
        val snapshotsById = scored.associate { it.snapshot.assetId to it.snapshot }
        return EquityReferenceSelection(
            positions = positions,
            factorExposure = factorExposure(ref, profile, positions, snapshotsById, year),
            eligibleCandidateCount = eligibleCandidateCount,
        )
    }

    private fun applyUniversePolicy(
        ref: BenchmarkRef,
        profile: EquityReferenceProfile,
        candidates: List<EquityReferenceCandidateSnapshot>,
    ): List<EquityReferenceCandidateSnapshot> {
        // Repository scopes are cached in descending float-cap order; policy filters preserve it.
        val byCap = candidates
        return when (profile.eligibleUniverse) {
            EquityEligibleUniverse.BROAD_MARKET,
            EquityEligibleUniverse.ALL_CAP,
            EquityEligibleUniverse.SECTOR_INDUSTRY,
            EquityEligibleUniverse.THEMATIC,
            EquityEligibleUniverse.SINGLE_SECURITY,
            EquityEligibleUniverse.ACTIVE_DISCRETIONARY,
            -> byCap
            EquityEligibleUniverse.LARGE_CAP -> byCap.take(maxOf(1, byCap.size * 30 / 100))
            EquityEligibleUniverse.MID_CAP -> byCap.drop(byCap.size * 20 / 100)
                .take(maxOf(1, byCap.size * 40 / 100))
            EquityEligibleUniverse.SMALL_CAP -> byCap.drop(byCap.size / 2)
            EquityEligibleUniverse.UNVERIFIED -> {
                when (positiveMod(DeterministicRandom.stableHash64(ref.benchmarkId), 4)) {
                    0 -> byCap
                    1 -> byCap.take(maxOf(1, byCap.size / 2))
                    2 -> byCap.drop(byCap.size / 3).take(maxOf(1, byCap.size / 2))
                    else -> byCap.drop(byCap.size / 2)
                }
            }
        }
    }

    private fun selectionScore(
        ref: BenchmarkRef,
        profile: EquityReferenceProfile,
        snapshot: EquityReferenceCandidateSnapshot,
        year: Int,
        incumbent: Boolean,
    ): Double {
        val cap = capScore(snapshot.floatMarketCap)
        val size = -cap
        val lowVolatility = (1.0 - snapshot.annualVolatility / .75).coerceIn(-1.0, 1.0)
        val thematic = thematicAffinity(profile.themeId, snapshot)
        val active = activeConviction(ref, snapshot.assetId, year)
        val styleScores = profile.stylePolicies.map { style ->
            when (style) {
                EquityStylePolicy.CORE -> .45 * cap + .30 * snapshot.quality +
                    .125 * snapshot.value + .125 * snapshot.growth
                EquityStylePolicy.GROWTH -> snapshot.growth
                EquityStylePolicy.VALUE -> snapshot.value
                EquityStylePolicy.DIVIDEND -> (snapshot.dividendYield / .06 - .5).coerceIn(-1.0, 1.0)
                EquityStylePolicy.QUALITY -> snapshot.quality
                EquityStylePolicy.MOMENTUM -> snapshot.momentum
                EquityStylePolicy.LOW_VOLATILITY -> lowVolatility
                EquityStylePolicy.HIGH_BETA -> (snapshot.beta - 1.0).coerceIn(-1.0, 1.0)
                EquityStylePolicy.ESG -> snapshot.esg
                EquityStylePolicy.THEMATIC -> thematic
                EquityStylePolicy.ACTIVE -> active
                EquityStylePolicy.SINGLE_SECURITY -> active
                EquityStylePolicy.MULTI_FACTOR -> (
                    snapshot.value + snapshot.growth + snapshot.quality + snapshot.momentum
                    ) / 4.0
                EquityStylePolicy.SIZE_TILT -> size
            }
        }
        val universeTilt = when (profile.eligibleUniverse) {
            EquityEligibleUniverse.LARGE_CAP -> cap * .30
            EquityEligibleUniverse.MID_CAP -> -abs(cap) * .20
            EquityEligibleUniverse.SMALL_CAP -> size * .30
            EquityEligibleUniverse.THEMATIC -> thematic * .35
            EquityEligibleUniverse.SINGLE_SECURITY -> active * .75
            EquityEligibleUniverse.ACTIVE_DISCRETIONARY -> active * .35
            else -> 0.0
        }
        val sectorTilt = when (profile.sectorPolicy) {
            EquitySectorPolicy.THEMATIC_CROSS_SECTOR -> thematic * .20
            EquitySectorPolicy.UNVERIFIED -> stableCandidateNoise(ref, snapshot.assetId, year) * .10
            else -> 0.0
        }
        val styleAverage = styleScores.average()
        val liquidityTilt = (ln(snapshot.liquidity) / 35.0).coerceIn(0.0, 1.0) * .05
        return (
            styleAverage + universeTilt + sectorTilt + liquidityTilt +
                if (incumbent) INCUMBENT_SCORE_BONUS else 0.0
            ).coerceIn(-100.0, 100.0)
    }

    private fun modelWeights(
        profile: EquityReferenceProfile,
        selected: List<EquityReferenceScoredCandidate>,
        representedCounts: Map<String, Int>,
    ): Map<String, Double> {
        val raw = selected.associate { candidate ->
            val snapshot = candidate.snapshot
            val represented = representedCounts.getValue(snapshot.assetId).toDouble()
            val base = when (profile.weightingModel) {
                EquityReferenceWeightingModel.FLOAT_ADJUSTED_MARKET_CAP -> snapshot.floatMarketCap
                EquityReferenceWeightingModel.MARKET_CAP -> snapshot.marketCap
                EquityReferenceWeightingModel.MODIFIED_MARKET_CAP -> sqrt(snapshot.floatMarketCap)
                EquityReferenceWeightingModel.EQUAL_WEIGHT -> 1.0
                EquityReferenceWeightingModel.PRICE_WEIGHTED -> snapshot.price
                EquityReferenceWeightingModel.FUNDAMENTAL ->
                    snapshot.revenue * (1.0 + snapshot.quality * .20)
                EquityReferenceWeightingModel.REVENUE_WEIGHTED -> snapshot.revenue
                EquityReferenceWeightingModel.DIVIDEND_WEIGHTED ->
                    snapshot.floatMarketCap * snapshot.dividendYield.coerceAtLeast(MIN_RAW_WEIGHT)
                EquityReferenceWeightingModel.FACTOR_SCORE -> exp(candidate.score.coerceIn(-10.0, 10.0))
                EquityReferenceWeightingModel.ACTIVE_DISCRETIONARY ->
                    exp((candidate.score * 1.5).coerceIn(-10.0, 10.0))
                EquityReferenceWeightingModel.UNVERIFIED -> sqrt(snapshot.floatMarketCap)
            }
            snapshot.assetId to (base * represented).coerceAtLeast(MIN_RAW_WEIGHT)
        }
        val individualCaps = selected.associate { candidate ->
            val represented = representedCounts.getValue(candidate.snapshot.assetId).toDouble()
            candidate.snapshot.assetId to (
                profile.individualWeightCap?.times(represented)?.coerceAtMost(1.0) ?: 1.0
                )
        }
        val bySector = selected.groupBy { it.snapshot.sector }
        val sectorRaw = bySector.mapValues { (_, members) ->
            members.sumOf { raw.getValue(it.snapshot.assetId) }
        }
        val sectorCaps = bySector.mapValues { (_, members) ->
            val individualCapacity = members.sumOf { individualCaps.getValue(it.snapshot.assetId) }
            minOf(profile.sectorWeightCap ?: 1.0, individualCapacity)
        }
        val sectorWeights = allocateWithCaps(
            orderedKeys = sectorRaw.keys.sortedBy(MethodologyEquitySector::ordinal),
            raw = sectorRaw,
            caps = sectorCaps,
            total = 1.0,
        )
        val result = linkedMapOf<String, Double>()
        bySector.toSortedMap(compareBy(MethodologyEquitySector::ordinal)).forEach { (sector, members) ->
            val ids = members.map { it.snapshot.assetId }.sorted()
            result += allocateWithCaps(
                orderedKeys = ids,
                raw = raw.filterKeys(ids::contains),
                caps = individualCaps.filterKeys(ids::contains),
                total = sectorWeights.getValue(sector),
            )
        }
        return repairWeightMap(result)
    }

    private fun factorExposure(
        ref: BenchmarkRef,
        profile: EquityReferenceProfile,
        positions: List<EquityReferencePosition>,
        snapshotsById: Map<String, EquityReferenceCandidateSnapshot>,
        year: Int,
    ): EquityReferenceFactorExposure {
        val countryWeights = linkedMapOf<String, Double>()
        val sectorWeights = linkedMapOf<MethodologyEquitySector, Double>()
        val styleValues = DoubleArray(EquityReferenceStyleFactor.entries.size)
        val buckets = MutableList(EquityReferenceFactorExposure.IDIOSYNCRATIC_BUCKET_COUNT) { 0.0 }
        var thematicExposure = 0.0
        var activeExposure = 0.0
        val hasTheme = profile.themeId != null
        val hasActive = EquityStylePolicy.ACTIVE in profile.stylePolicies ||
            profile.weightingModel == EquityReferenceWeightingModel.ACTIVE_DISCRETIONARY
        positions.forEach { position ->
            val snapshot = snapshotsById.getValue(position.assetId)
            val weight = position.weight
            countryWeights[position.countryCode] =
                countryWeights[position.countryCode].orZero() + weight
            sectorWeights[position.sector] = sectorWeights[position.sector].orZero() + weight
            styleValues[EquityReferenceStyleFactor.VALUE.ordinal] += weight * snapshot.value
            styleValues[EquityReferenceStyleFactor.GROWTH.ordinal] += weight * snapshot.growth
            styleValues[EquityReferenceStyleFactor.QUALITY.ordinal] += weight * snapshot.quality
            styleValues[EquityReferenceStyleFactor.DIVIDEND.ordinal] += weight *
                (snapshot.dividendYield / .06 - .5).coerceIn(-1.0, 1.0)
            styleValues[EquityReferenceStyleFactor.MOMENTUM.ordinal] += weight * snapshot.momentum
            styleValues[EquityReferenceStyleFactor.LOW_VOLATILITY.ordinal] += weight *
                (1.0 - snapshot.annualVolatility / .75).coerceIn(-1.0, 1.0)
            styleValues[EquityReferenceStyleFactor.SIZE.ordinal] += weight *
                -capScore(snapshot.floatMarketCap)
            styleValues[EquityReferenceStyleFactor.BETA.ordinal] += weight * (snapshot.beta - 1.0)
            styleValues[EquityReferenceStyleFactor.ESG.ordinal] += weight * snapshot.esg
            val hash = DeterministicRandom.stableHash64(position.assetId).toULong()
            val bucket = (hash % EquityReferenceFactorExposure.IDIOSYNCRATIC_BUCKET_COUNT.toUInt()).toInt()
            val sign = if ((hash shr 8) and 1uL == 0uL) 1.0 else -1.0
            buckets[bucket] += weight * snapshot.annualVolatility * sign
            if (hasTheme) {
                thematicExposure += weight * thematicAffinity(profile.themeId, snapshot)
            }
            if (hasActive) {
                activeExposure += weight * activeConviction(ref, position.assetId, year)
            }
        }
        val styleExposures = EquityReferenceStyleFactor.entries.associateWith { factor ->
            styleValues[factor.ordinal]
        }
        return EquityReferenceFactorExposure(
            countryWeights = repairWeightMap(countryWeights.toSortedMap()),
            sectorWeights = repairWeightMap(
                sectorWeights.toSortedMap(compareBy(MethodologyEquitySector::ordinal)),
            ),
            styleExposures = styleExposures,
            idiosyncraticVolatilityWeights = buckets,
            thematicExposure = thematicExposure,
            activeManagementExposure = activeExposure,
        )
    }

    private fun factorFrame(
        countries: Set<String>,
        marketTradingFractions: Map<Market, Double>,
        macro: MacroEnvironment,
        from: Instant,
        to: Instant,
    ): EquityReferenceFactorFrame {
        val elapsedYearFraction = (to - from).inWholeMilliseconds.toDouble() / MILLISECONDS_PER_YEAR
        val countryFractions = countries.sorted().associateWith { country ->
            marketTradingFractions.getValue(marketForRegion(regionForCountry(country)))
        }
        val countryReturns = countries.sorted().associateWith { country ->
            val fraction = countryFractions.getValue(country)
            if (fraction == 0.0) {
                0.0
            } else {
                val base = regionalMarketSimpleReturn(regionForCountry(country), macro)
                val residual = COUNTRY_RESIDUAL_VOLATILITY *
                    sqrt(elapsedYearFraction * fraction * macro.volatilityRegime.coerceIn(.25, 4.0)) *
                    DeterministicRandom.keyed(
                        seed,
                        "equity-country-return:$country:${from.epochSeconds}:${to.epochSeconds}",
                    ).nextGaussian()
                safeLog1p(base) + residual
            }
        }
        val maximumTradingFraction = countryFractions.values.maxOrNull() ?: 0.0
        val sectorReturns = MethodologyEquitySector.entries.associateWith { sector ->
            if (maximumTradingFraction == 0.0) {
                0.0
            } else {
                val supplied = macro.sectorHourlyReturns[marketSector(sector)] ?: 0.0
                val residual = SECTOR_RESIDUAL_VOLATILITY *
                    sqrt(elapsedYearFraction * maximumTradingFraction) *
                    DeterministicRandom.keyed(
                        seed,
                        "equity-sector-return:$sector:${from.epochSeconds}:${to.epochSeconds}",
                    ).nextGaussian()
                SECTOR_FACTOR_LOADING * safeLog1p(supplied) + residual
            }
        }
        val styleNormals = EquityReferenceStyleFactor.entries.associateWith { factor ->
            DeterministicRandom.keyed(
                seed,
                "equity-style-return:$factor:${from.epochSeconds}:${to.epochSeconds}",
            ).nextGaussian()
        }
        val idiosyncraticNormals = List(EquityReferenceFactorExposure.IDIOSYNCRATIC_BUCKET_COUNT) { bucket ->
            DeterministicRandom.keyed(
                seed,
                "equity-shared-residual:$bucket:${from.epochSeconds}:${to.epochSeconds}",
            ).nextGaussian()
        }
        return EquityReferenceFactorFrame(
            countryLogReturns = countryReturns,
            countryTradingFractions = countryFractions,
            sectorLogReturns = sectorReturns,
            styleStandardNormals = styleNormals,
            idiosyncraticStandardNormals = idiosyncraticNormals,
            elapsedYearFraction = elapsedYearFraction,
        )
    }

    private fun referenceLogReturn(
        ref: BenchmarkRef,
        state: EquityReferenceState,
        profile: EquityReferenceProfile,
        frame: EquityReferenceFactorFrame,
        macro: MacroEnvironment,
        from: Instant,
        to: Instant,
    ): Double {
        val exposure = state.factorExposure
        val countryReturn = exposure.countryWeights.entries.sumOf { (country, weight) ->
            weight * frame.countryLogReturns.getValue(country)
        }
        val activeFraction = exposure.countryWeights.entries.sumOf { (country, weight) ->
            weight * frame.countryTradingFractions.getValue(country)
        }.coerceIn(0.0, 1.0)
        if (activeFraction == 0.0) return 0.0
        val sectorReturn = exposure.sectorWeights.entries.sumOf { (sector, weight) ->
            weight * frame.sectorLogReturns.getValue(sector)
        }
        val styleReturn = EquityReferenceStyleFactor.entries.sumOf { factor ->
            val factorExposure = exposure.styleExposures.getValue(factor)
            val premium = STYLE_ANNUAL_PREMIA.getValue(factor) * frame.elapsedYearFraction * activeFraction
            val shock = STYLE_ANNUAL_VOLATILITIES.getValue(factor) *
                sqrt(frame.elapsedYearFraction * activeFraction * macro.volatilityRegime.coerceIn(.25, 4.0)) *
                frame.styleStandardNormals.getValue(factor)
            factorExposure * (premium + shock)
        }
        val idiosyncraticReturn = sqrt(frame.elapsedYearFraction * activeFraction) *
            exposure.idiosyncraticVolatilityWeights.indices.sumOf { bucket ->
                exposure.idiosyncraticVolatilityWeights[bucket] *
                    frame.idiosyncraticStandardNormals[bucket]
            }
        val thematicReturn = profile.themeId?.let { themeId ->
            exposure.thematicExposure * (
                THEME_ANNUAL_PREMIUM * frame.elapsedYearFraction * activeFraction +
                    THEME_ANNUAL_VOLATILITY * sqrt(frame.elapsedYearFraction * activeFraction) *
                    DeterministicRandom.keyed(
                        seed,
                        "equity-theme-return:$themeId:${from.epochSeconds}:${to.epochSeconds}",
                    ).nextGaussian()
                )
        } ?: 0.0
        val activeReturn = if (exposure.activeManagementExposure == 0.0) {
            0.0
        } else {
            exposure.activeManagementExposure * (
                ACTIVE_ANNUAL_ALPHA * frame.elapsedYearFraction * activeFraction +
                    ACTIVE_ANNUAL_VOLATILITY * sqrt(frame.elapsedYearFraction * activeFraction) *
                    DeterministicRandom.keyed(
                        seed,
                        "equity-active-return:$ref:${from.epochSeconds}:${to.epochSeconds}",
                    ).nextGaussian()
                )
        }
        return (countryReturn + sectorReturn + styleReturn + idiosyncraticReturn +
            thematicReturn + activeReturn).coerceIn(-MAX_HOURLY_LOG_MOVE, MAX_HOURLY_LOG_MOVE)
    }

    private fun rebalanceRecord(
        ref: BenchmarkRef,
        previous: EquityReferenceState,
        kind: EquityReferenceActionKind,
        positions: List<EquityReferencePosition>,
        effectiveDate: LocalDate,
        effectiveAt: Instant,
        compositionHashAfter: String,
        revision: Long,
    ): EquityReferenceRebalanceRecord {
        val before = previous.positions.associate { it.assetId to it.weight }
        val after = positions.associate { it.assetId to it.weight }
        val ids = before.keys + after.keys
        val turnover = .5 * ids.sumOf { id -> abs(before[id].orZero() - after[id].orZero()) }
        return EquityReferenceRebalanceRecord(
            id = "equity-reference-${kind.name.lowercase()}:${ref.benchmarkId}:v${ref.version}:" +
                "$effectiveDate:r$revision",
            benchmarkRef = ref,
            kind = kind,
            selectionDate = effectiveDate,
            effectiveAt = effectiveAt,
            addedAssetIds = (after.keys - before.keys).sorted(),
            removedAssetIds = (before.keys - after.keys).sorted(),
            compositionHashBefore = previous.compositionHash,
            compositionHashAfter = compositionHashAfter,
            turnoverRate = turnover.coerceIn(0.0, 1.0),
            resultingPositionCount = positions.size,
            representedConstituentCount = positions.sumOf(
                EquityReferencePosition::representedConstituentCount,
            ),
            revision = revision,
        )
    }

    private fun scheduleAround(
        ref: BenchmarkRef,
        region: EquityReferenceRegion,
        calendar: EquityRebalanceCalendar,
        configuredMonths: Set<Int>,
        scheduleKey: String,
        atDate: LocalDate,
        at: Instant,
    ): Pair<LocalDate, LocalDate> {
        val dates = scheduledDates(
            ref = ref,
            region = region,
            calendar = calendar,
            configuredMonths = configuredMonths,
            scheduleKey = scheduleKey,
            fromYear = atDate.year - 2,
            throughYear = atDate.year + 2,
        )
        val market = marketForRegion(region)
        val last = dates.lastOrNull { date ->
            val close = regularCloseInstant(market, date)
            close <= at
        } ?: error("No prior equity-reference rebalance date for $ref.")
        val next = dates.firstOrNull { it > last }
            ?: nextScheduledDate(
                ref = ref,
                region = region,
                calendar = calendar,
                configuredMonths = configuredMonths,
                scheduleKey = scheduleKey,
                after = last,
            )
        return last to next
    }

    private fun nextScheduledDate(
        ref: BenchmarkRef,
        region: EquityReferenceRegion,
        calendar: EquityRebalanceCalendar,
        configuredMonths: Set<Int>,
        scheduleKey: String,
        after: LocalDate,
    ): LocalDate = scheduledDates(
        ref = ref,
        region = region,
        calendar = calendar,
        configuredMonths = configuredMonths,
        scheduleKey = scheduleKey,
        fromYear = after.year,
        throughYear = after.year + 3,
    )
        .first { it > after }

    private fun scheduledDates(
        ref: BenchmarkRef,
        region: EquityReferenceRegion,
        calendar: EquityRebalanceCalendar,
        configuredMonths: Set<Int>,
        scheduleKey: String,
        fromYear: Int,
        throughYear: Int,
    ): List<LocalDate> {
        val months = effectiveMonths(ref, calendar, configuredMonths, scheduleKey)
        val market = marketForRegion(region)
        return (fromYear..throughYear).flatMap { year ->
            months.map { month -> lastTradingDateOfMonth(market, year, month) }
        }.sorted()
    }

    private fun effectiveMonths(
        ref: BenchmarkRef,
        calendar: EquityRebalanceCalendar,
        configuredMonths: Set<Int>,
        scheduleKey: String,
    ): Set<Int> = when (calendar) {
        EquityRebalanceCalendar.CONTINUOUS_ACTIVE,
        EquityRebalanceCalendar.MONTHLY,
        -> (1..12).toSet()
        EquityRebalanceCalendar.QUARTERLY,
        EquityRebalanceCalendar.SEMI_ANNUAL,
        EquityRebalanceCalendar.ANNUAL,
        -> configuredMonths
        EquityRebalanceCalendar.UNVERIFIED -> {
            val offset = positiveMod(
                DeterministicRandom.stableHash64("${ref.benchmarkId}:$scheduleKey"),
                3,
            ) + 1
            setOf(offset, offset + 3, offset + 6, offset + 9)
        }
    }

    private fun crossesRebalanceClose(
        region: EquityReferenceRegion,
        date: LocalDate,
        from: Instant,
        to: Instant,
    ): Boolean {
        val close = regularCloseInstant(marketForRegion(region), date)
        return from < close && to >= close
    }

    private fun tradingCloseDatesCrossed(
        region: EquityReferenceRegion,
        from: Instant,
        to: Instant,
    ): List<LocalDate> {
        val market = marketForRegion(region)
        val zone = GameCalendar.timeZoneFor(market)
        var date = from.toLocalDateTime(zone).date
        val through = to.toLocalDateTime(zone).date
        val result = mutableListOf<LocalDate>()
        while (date <= through) {
            if (isTradingDate(market, date)) {
                val close = regularCloseInstant(market, date)
                if (from < close && to >= close) result += date
            }
            date = date.plus(1, DateTimeUnit.DAY)
        }
        return result
    }

    private fun isWeightMaterializationDate(
        region: EquityReferenceRegion,
        date: LocalDate,
    ): Boolean = date == lastTradingDateOfMonth(
        marketForRegion(region),
        date.year,
        date.month.ordinal + 1,
    )

    private fun lastTradingDateOfMonth(market: Market, year: Int, month: Int): LocalDate {
        val firstOfNext = if (month == 12) LocalDate(year + 1, 1, 1) else LocalDate(year, month + 1, 1)
        var date = firstOfNext.minus(1, DateTimeUnit.DAY)
        val holidays = closedDates(market, year)
        while (GameCalendar.isWeekend(date) || date in holidays) {
            date = date.minus(1, DateTimeUnit.DAY)
        }
        return date
    }

    private fun isTradingDate(market: Market, date: LocalDate): Boolean =
        !GameCalendar.isWeekend(date) && date !in closedDates(market, date.year)

    private fun lastCompletedTradingDate(
        region: EquityReferenceRegion,
        atDate: LocalDate,
        at: Instant,
    ): LocalDate {
        val market = marketForRegion(region)
        var date = atDate
        while (!isTradingDate(market, date) || regularCloseInstant(market, date) > at) {
            date = date.minus(1, DateTimeUnit.DAY)
        }
        return date
    }

    private fun tradingDateCount(
        market: Market,
        afterDate: LocalDate,
        throughDate: LocalDate,
    ): Int {
        var count = 0
        var date = afterDate.plus(1, DateTimeUnit.DAY)
        while (date <= throughDate) {
            if (isTradingDate(market, date)) count += 1
            date = date.plus(1, DateTimeUnit.DAY)
        }
        return count
    }

    private fun closedDates(market: Market, year: Int): Set<LocalDate> =
        closedDatesCache.getOrPut(market to year) {
            if (year in FIRST_YEAR..LAST_YEAR) {
                DefaultMarketHolidays.closedDates(market, year)
            } else {
                emptySet()
            }
        }

    private fun regularCloseInstant(market: Market, date: LocalDate): Instant {
        val close = if (market.isKorean) LocalTime(15, 30) else LocalTime(16, 0)
        return LocalDateTime(date, close).toInstant(GameCalendar.timeZoneFor(market))
    }

    private fun regionalMarketSimpleReturn(
        region: EquityReferenceRegion,
        macro: MacroEnvironment,
    ): Double = when (region) {
        EquityReferenceRegion.KOREA -> macro.regionalEtfHourlyReturns?.get(EtfExposureRegion.KOREA)
            ?: macro.marketHourlyReturns.filterKeys(Market::isKorean).values.averageOrZero()
        EquityReferenceRegion.UNITED_STATES ->
            macro.regionalEtfHourlyReturns?.get(EtfExposureRegion.UNITED_STATES)
                ?: macro.marketHourlyReturns.filterKeys(Market::isUnitedStates).values.averageOrZero()
        EquityReferenceRegion.DEVELOPED_EX_US ->
            macro.regionalEtfHourlyReturns?.get(EtfExposureRegion.DEVELOPED_EX_US)
                ?: macro.marketHourlyReturns.filterKeys(Market::isUnitedStates).values.averageOrZero()
        EquityReferenceRegion.EMERGING_MARKETS ->
            macro.regionalEtfHourlyReturns?.get(EtfExposureRegion.EMERGING_MARKETS)
                ?: macro.marketHourlyReturns.values.averageOrZero()
        EquityReferenceRegion.GLOBAL ->
            macro.regionalEtfHourlyReturns?.get(EtfExposureRegion.GLOBAL)
                ?: macro.marketHourlyReturns.values.averageOrZero()
    }.coerceIn(-.95, 5.0)

    private fun marketForRegion(region: EquityReferenceRegion): Market = when (region) {
        EquityReferenceRegion.KOREA -> Market.KOSPI
        EquityReferenceRegion.UNITED_STATES,
        EquityReferenceRegion.GLOBAL,
        EquityReferenceRegion.DEVELOPED_EX_US,
        EquityReferenceRegion.EMERGING_MARKETS,
        -> Market.NYSE
    }

    private fun regionForCountry(countryCode: String): EquityReferenceRegion = when (countryCode) {
        "KR" -> EquityReferenceRegion.KOREA
        "US" -> EquityReferenceRegion.UNITED_STATES
        in EquityReferenceUniverseRepository.DEVELOPED_EX_US_COUNTRIES ->
            EquityReferenceRegion.DEVELOPED_EX_US
        in EquityReferenceUniverseRepository.EMERGING_COUNTRIES ->
            EquityReferenceRegion.EMERGING_MARKETS
        else -> error("Unknown equity-reference country $countryCode.")
    }

    fun canonicalResolvedCountryCodes(profile: EquityReferenceProfile): Set<String> =
        (if (profile.countryCodes.isEmpty()) {
            repository.countryCodesForRegion(profile.region)
        } else {
            profile.countryCodes
        }).sorted().toSet().also { countries ->
            require(countries.all { it in repository.countryCodesForRegion(profile.region) })
        }

    fun resolvedCountryCodes(profile: EquityReferenceProfile): Set<String> =
        canonicalResolvedCountryCodes(profile)

    private fun declaredOrDefaultCount(
        ref: BenchmarkRef,
        profile: EquityReferenceProfile,
    ): Int {
        if (profile.eligibleUniverse == EquityEligibleUniverse.SINGLE_SECURITY) {
            require(profile.targetConstituentCount == null || profile.targetConstituentCount == 1) {
                "SINGLE_SECURITY references must represent exactly one constituent."
            }
            return 1
        }
        return profile.targetConstituentCount ?: when (profile.eligibleUniverse) {
        EquityEligibleUniverse.BROAD_MARKET -> 500
        EquityEligibleUniverse.ALL_CAP -> 800
        EquityEligibleUniverse.LARGE_CAP -> 100
        EquityEligibleUniverse.MID_CAP -> 150
        EquityEligibleUniverse.SMALL_CAP -> 300
        EquityEligibleUniverse.SECTOR_INDUSTRY -> 80
        EquityEligibleUniverse.THEMATIC -> 64
        EquityEligibleUniverse.SINGLE_SECURITY -> error("Handled above.")
        EquityEligibleUniverse.ACTIVE_DISCRETIONARY -> 100
        EquityEligibleUniverse.UNVERIFIED ->
            64 + positiveMod(DeterministicRandom.stableHash64(ref.benchmarkId), 129)
        }
    }

    private fun representedCounts(
        selected: List<EquityReferenceScoredCandidate>,
        total: Int,
    ): Map<String, Int> {
        require(selected.isNotEmpty() && total >= selected.size)
        val base = total / selected.size
        val remainder = total % selected.size
        return selected.mapIndexed { index, candidate ->
            candidate.snapshot.assetId to base + if (index < remainder) 1 else 0
        }.toMap()
    }

    private fun thematicAffinity(
        themeId: String?,
        snapshot: EquityReferenceCandidateSnapshot,
    ): Double {
        if (themeId == null) return 0.0
        return thematicAffinityCache.getOrPut("$themeId|${snapshot.assetId}") {
            val sectorAffinity = DeterministicRandom.keyed(
                seed,
                "equity-theme-sector:$themeId:${snapshot.sector}",
            ).nextDouble(-1.0, 1.0)
            val assetAffinity = DeterministicRandom.keyed(
                seed,
                "equity-theme-asset:$themeId:${snapshot.assetId}",
            ).nextDouble(-1.0, 1.0)
            (.70 * sectorAffinity + .30 * assetAffinity).coerceIn(-1.0, 1.0)
        }
    }

    private fun activeConviction(ref: BenchmarkRef, assetId: String, year: Int): Double =
        activeConvictionCache.getOrPut("$ref|$assetId|$year") {
            DeterministicRandom.keyed(
                seed,
                "equity-active-conviction:$ref:$assetId:$year",
            ).nextDouble(-1.0, 1.0)
        }

    private fun stableCandidateNoise(ref: BenchmarkRef, assetId: String, year: Int): Double =
        DeterministicRandom.keyed(
            seed,
            "equity-unverified-score:$ref:$assetId:$year",
        ).nextDouble(-1.0, 1.0)

    private fun prepareSelectionCache(from: Instant, to: Instant) {
        if (selectionCacheFrom != from || selectionCacheTo != to) {
            selectionCache.clear()
            selectionCacheFrom = from
            selectionCacheTo = to
        }
    }

    private fun selectionPolicyKey(profile: EquityReferenceProfile): String =
        selectionPolicyKeyCache.getOrPut(profile) {
            listOf(
                profile.region,
                profile.countryCodes.joinToString(","),
                profile.eligibleUniverse,
                profile.sectorPolicy,
                profile.includedSectors.joinToString(","),
                profile.themeId,
                profile.stylePolicies.joinToString(","),
                profile.weightingModel,
                profile.targetConstituentCount,
                profile.individualWeightCap,
                profile.sectorWeightCap,
                profile.confidence,
            ).joinToString("|")
        }

    private fun isShareableSelectionPolicy(profile: EquityReferenceProfile): Boolean =
        profile.eligibleUniverse != EquityEligibleUniverse.UNVERIFIED &&
            profile.eligibleUniverse != EquityEligibleUniverse.SINGLE_SECURITY &&
            profile.eligibleUniverse != EquityEligibleUniverse.ACTIVE_DISCRETIONARY &&
            profile.sectorPolicy != EquitySectorPolicy.UNVERIFIED &&
            EquityStylePolicy.ACTIVE !in profile.stylePolicies &&
            EquityStylePolicy.SINGLE_SECURITY !in profile.stylePolicies &&
            profile.weightingModel != EquityReferenceWeightingModel.ACTIVE_DISCRETIONARY

    private fun selectionIdentity(position: EquityReferencePosition): String =
        "${position.assetId}:${position.representedConstituentCount}:${position.enteredOn}"

    private fun capScore(value: Double): Double = (
        2.0 * (ln(value) - ln(MIN_CAP_SCORE_VALUE)) /
            (ln(MAX_CAP_SCORE_VALUE) - ln(MIN_CAP_SCORE_VALUE)) - 1.0
        ).coerceIn(-1.0, 1.0)

    fun incomeYield(positions: List<EquityReferencePosition>): Double =
        positions.sumOf { it.weight * it.indicatedAnnualDividendYield }.coerceIn(0.0, 1.0)

    fun canonicalProfileFingerprint(ref: BenchmarkRef, profile: EquityReferenceProfile): String =
        profileFingerprintCache.getOrPut(ref) { mutableMapOf() }.getOrPut(profile) {
            stableHex(
                listOf(
                    ref,
                    profile.region,
                    profile.countryCodes.joinToString(","),
                    profile.eligibleUniverse,
                    profile.sectorPolicy,
                    profile.includedSectors.joinToString(","),
                    profile.themeId,
                    profile.stylePolicies.joinToString(","),
                    profile.weightingModel,
                    profile.targetConstituentCount,
                    profile.individualWeightCap,
                    profile.sectorWeightCap,
                    profile.selectionCalendar,
                    effectiveMonths(
                        ref,
                        profile.selectionCalendar,
                        profile.selectionMonths,
                        "selection",
                    ).joinToString(","),
                    profile.reweightCalendar,
                    effectiveMonths(
                        ref,
                        profile.reweightCalendar,
                        profile.reweightMonths,
                        "reweight",
                    ).joinToString(","),
                    profile.supportLevel,
                    profile.provenance,
                    profile.confidence,
                    profile.officialSourceUrls.joinToString(","),
                    profile.assumptionId,
                ).joinToString("|"),
            )
        }

    fun profileFingerprint(ref: BenchmarkRef, profile: EquityReferenceProfile): String =
        canonicalProfileFingerprint(ref, profile)

    fun canonicalCompositionHash(positions: List<EquityReferencePosition>): String = stableHex(
        positions.sortedBy(EquityReferencePosition::assetId).joinToString("|") { position ->
            "${position.assetId}:${position.targetWeight.toBits()}:" +
                "${position.representedConstituentCount}:${position.selectionScore.toBits()}:" +
                position.enteredOn
        },
    )

    fun compositionHash(positions: List<EquityReferencePosition>): String =
        canonicalCompositionHash(positions)

    private fun stableHex(value: String): String =
        DeterministicRandom.stableHash64(value).toULong().toString(16).padStart(16, '0')

    private fun validatedDefinitions(
        definitions: Collection<BenchmarkDefinition>,
    ): Map<BenchmarkRef, BenchmarkDefinition> {
        if (definitions === cachedDefinitionSource) {
            return requireNotNull(cachedDefinitionsByRef)
        }
        require(definitions.isNotEmpty())
        require(definitions.all { definition ->
            definition.engineKind == BenchmarkEngineKind.EQUITY_REFERENCE &&
                definition.equityReferenceProfile != null
        })
        val grouped = definitions.groupBy(BenchmarkDefinition::ref)
        grouped.forEach { (ref, duplicates) ->
            require(duplicates.all { it == duplicates.first() }) {
                "Conflicting equity-reference definitions were supplied for $ref."
            }
        }
        return grouped.mapValues { (_, duplicates) -> duplicates.first() }.also { validated ->
            cachedDefinitionSource = definitions
            cachedDefinitionsByRef = validated
        }
    }

    private fun marketSector(sector: MethodologyEquitySector): Sector = when (sector) {
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

    private fun safeLog1p(simpleReturn: Double): Double = ln(1.0 + simpleReturn.coerceIn(-.95, 5.0))

    private fun positiveMod(value: Long, modulus: Int): Int =
        (value.toULong() % modulus.toUInt()).toInt()

    private fun Double?.orZero(): Double = this ?: 0.0

    private fun Collection<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()

    private fun List<EquityReferencePosition>.repairWeightRounding(): List<EquityReferencePosition> {
        val difference = 1.0 - sumOf(EquityReferencePosition::weight)
        if (difference == 0.0) return this
        val index = indices.maxBy { this[it].weight }
        val targetsMatchCurrent = all { abs(it.weight - it.targetWeight) <= WEIGHT_EPSILON }
        return mapIndexed { candidateIndex, position ->
            if (candidateIndex == index) {
                position.copy(
                    weight = position.weight + difference,
                    targetWeight = if (targetsMatchCurrent) {
                        position.targetWeight + difference
                    } else {
                        position.targetWeight
                    },
                )
            } else {
                position
            }
        }
    }

    private fun <K> repairWeightMap(values: Map<K, Double>): Map<K, Double> {
        require(values.isNotEmpty())
        val result = values.toMutableMap()
        val difference = 1.0 - result.values.sum()
        if (difference != 0.0) {
            val key = result.maxBy { it.value }.key
            result[key] = result.getValue(key) + difference
        }
        return result
    }

    private fun <K> allocateWithCaps(
        orderedKeys: List<K>,
        raw: Map<K, Double>,
        caps: Map<K, Double>,
        total: Double,
    ): Map<K, Double> {
        require(orderedKeys.isNotEmpty())
        require(total.isFinite() && total > 0.0)
        require(raw.keys == orderedKeys.toSet() && caps.keys == raw.keys)
        require(raw.values.all { it.isFinite() && it > 0.0 })
        require(caps.values.all { it.isFinite() && it > 0.0 })
        require(caps.values.sum() >= total - WEIGHT_EPSILON) {
            "Representative basket cannot satisfy its declared caps."
        }
        val rawValues = DoubleArray(orderedKeys.size) { index ->
            raw.getValue(orderedKeys[index])
        }
        val capValues = DoubleArray(orderedKeys.size) { index ->
            caps.getValue(orderedKeys[index])
        }
        fun weightSumAt(scale: Double): Double {
            var sum = 0.0
            for (index in rawValues.indices) {
                sum += minOf(rawValues[index] * scale, capValues[index])
            }
            return sum
        }
        var low = 0.0
        var high = 1.0
        var expansionCount = 0
        while (weightSumAt(high) < total - WEIGHT_EPSILON) {
            high *= 2.0
            expansionCount += 1
            require(expansionCount <= MAX_SCALE_EXPANSIONS && high.isFinite())
        }
        repeat(BISECTION_ITERATIONS) {
            val middle = (low + high) / 2.0
            if (weightSumAt(middle) < total) low = middle else high = middle
        }
        val resultValues = DoubleArray(orderedKeys.size) { index ->
            minOf(rawValues[index] * high, capValues[index])
        }
        var residual = total - resultValues.sum()
        if (residual > 0.0) {
            for (index in resultValues.indices) {
                if (residual <= WEIGHT_EPSILON) break
                val headroom = capValues[index] - resultValues[index]
                val addition = minOf(residual, headroom.coerceAtLeast(0.0))
                resultValues[index] += addition
                residual -= addition
            }
        } else if (residual < 0.0) {
            for (index in resultValues.indices.reversed()) {
                if (residual >= -WEIGHT_EPSILON) break
                val removal = minOf(-residual, resultValues[index])
                resultValues[index] -= removal
                residual += removal
            }
        }
        val finalDifference = total - resultValues.sum()
        if (finalDifference != 0.0) {
            val repairIndex = resultValues.indices.firstOrNull { index ->
                resultValues[index] + finalDifference in 0.0..capValues[index]
            } ?: error("Unable to repair capped representative weights.")
            resultValues[repairIndex] += finalDifference
        }
        require(abs(resultValues.sum() - total) <= WEIGHT_EPSILON)
        require(resultValues.indices.all { index ->
            resultValues[index] >= 0.0 && resultValues[index] <= capValues[index] + WEIGHT_EPSILON
        })
        return orderedKeys.indices.associateTo(linkedMapOf()) { index ->
            orderedKeys[index] to resultValues[index]
        }
    }

    companion object {
        fun forCampaignSeed(campaignSeed: Long): EquityReferenceBookEngine =
            EquityReferenceBookEngine(
                seed = DeterministicRandom.mixSeed(campaignSeed, ENGINE_STREAM_ID),
                repository = EquityReferenceUniverseRepository.forCampaignSeed(campaignSeed),
            )

        private const val ENGINE_STREAM_ID: Long = 0x455155495459454EL
        private const val FIRST_YEAR: Int = 2026
        private const val LAST_YEAR: Int = 2040
        private const val MILLISECONDS_PER_YEAR: Double = 31_557_600_000.0
        private const val MIN_CAP_SCORE_VALUE: Double = 50_000_000.0
        private const val MAX_CAP_SCORE_VALUE: Double = 2_500_000_000_000.0
        private const val MIN_RAW_WEIGHT: Double = 1e-18
        private const val MAX_SELECTION_SCORE_POOL: Int = 1_024
        private const val INCUMBENT_SCORE_BONUS: Double = .035
        private const val COUNTRY_RESIDUAL_VOLATILITY: Double = .08
        private const val SECTOR_RESIDUAL_VOLATILITY: Double = .055
        private const val SECTOR_FACTOR_LOADING: Double = .25
        private const val THEME_ANNUAL_PREMIUM: Double = .01
        private const val THEME_ANNUAL_VOLATILITY: Double = .10
        private const val ACTIVE_ANNUAL_ALPHA: Double = .005
        private const val ACTIVE_ANNUAL_VOLATILITY: Double = .12
        private const val TRADING_DAYS_PER_YEAR: Double = 252.0
        private const val WEIGHT_MATERIALIZATION_YEAR_FRACTION: Double = 1.0 / 12.0
        private const val BOOTSTRAP_COUNTRY_VOLATILITY: Double = .14
        private const val BOOTSTRAP_SECTOR_VOLATILITY: Double = .08
        private const val BOOTSTRAP_IDIOSYNCRATIC_LOADING: Double = .35
        private const val LIVE_CLOSE_MARKET_LOADING: Double = .50
        private const val LIVE_COUNTRY_RESIDUAL_VOLATILITY: Double = .10
        private const val LIVE_CLOSE_SECTOR_LOADING: Double = .35
        private const val LIVE_SECTOR_RESIDUAL_VOLATILITY: Double = .07
        private const val LIVE_STYLE_VOLATILITY: Double = .06
        private const val LIVE_IDIOSYNCRATIC_LOADING: Double = .35
        private const val MAX_DAILY_ANCHOR_LOG_MOVE: Double = .50
        private const val MAX_BOOTSTRAP_PERIOD_LOG_MOVE: Double = 1.50
        private const val MAX_HOURLY_LOG_MOVE: Double = 1.0
        private const val WEIGHT_EPSILON: Double = 1e-10
        private const val MAX_SCALE_EXPANSIONS: Int = 128
        private const val BISECTION_ITERATIONS: Int = 160
        private val MAX_ADVANCE_DURATION = 48.hours
        private val STYLE_ANNUAL_PREMIA = mapOf(
            EquityReferenceStyleFactor.VALUE to .020,
            EquityReferenceStyleFactor.GROWTH to .012,
            EquityReferenceStyleFactor.QUALITY to .018,
            EquityReferenceStyleFactor.DIVIDEND to .010,
            EquityReferenceStyleFactor.MOMENTUM to .025,
            EquityReferenceStyleFactor.LOW_VOLATILITY to .008,
            EquityReferenceStyleFactor.SIZE to .015,
            EquityReferenceStyleFactor.BETA to .000,
            EquityReferenceStyleFactor.ESG to .004,
        )
        private val STYLE_ANNUAL_VOLATILITIES = mapOf(
            EquityReferenceStyleFactor.VALUE to .08,
            EquityReferenceStyleFactor.GROWTH to .09,
            EquityReferenceStyleFactor.QUALITY to .06,
            EquityReferenceStyleFactor.DIVIDEND to .05,
            EquityReferenceStyleFactor.MOMENTUM to .10,
            EquityReferenceStyleFactor.LOW_VOLATILITY to .05,
            EquityReferenceStyleFactor.SIZE to .08,
            EquityReferenceStyleFactor.BETA to .12,
            EquityReferenceStyleFactor.ESG to .04,
        )
    }
}
