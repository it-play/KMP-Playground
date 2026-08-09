package com.amond.kmpbook.domain.simulation.schedule

import com.amond.kmpbook.domain.data.StockCatalog
import com.amond.kmpbook.domain.model.event.EventImpactHorizon
import com.amond.kmpbook.domain.model.event.EventImpactInsight
import com.amond.kmpbook.domain.model.event.EventImpactTargetKind
import com.amond.kmpbook.domain.model.event.EventRecordKind
import com.amond.kmpbook.domain.model.event.EventSeverity
import com.amond.kmpbook.domain.model.event.GameEvent
import com.amond.kmpbook.domain.model.event.GameEventImpact
import com.amond.kmpbook.domain.model.event.ImpactDirection
import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.market.Currency
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.schedule.EconomicReleaseVintage
import com.amond.kmpbook.domain.model.schedule.ReportedFact
import com.amond.kmpbook.domain.model.schedule.ScheduledEventEmission
import com.amond.kmpbook.domain.model.schedule.ScheduledEventKind
import com.amond.kmpbook.domain.model.schedule.ScheduledEventMetric
import com.amond.kmpbook.domain.model.schedule.ScheduledEventMetricKind
import com.amond.kmpbook.domain.model.schedule.ScheduledEventOccurrence
import com.amond.kmpbook.domain.model.schedule.ScheduledEventOutcome
import com.amond.kmpbook.domain.model.schedule.ScheduledEventReference
import com.amond.kmpbook.domain.model.schedule.ScheduledOutcomeComparison
import com.amond.kmpbook.domain.simulation.price.DeterministicRandom
import com.amond.kmpbook.domain.time.DefaultMarketHolidays
import com.amond.kmpbook.domain.time.GameCalendar
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.round
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Stateless-by-contract scheduled event generator. Its caches only memoize the pure calendar;
 * schedule and ids do not use [seed]. Outcome streams are keyed by occurrence id, so broad and
 * narrow queries produce identical figures and impacts without a sequence counter or save field.
 */
class ScheduledEventEngine(private val seed: Long) {
    private var cachedStocks: List<StockDefinition> = emptyList()
    private val yearCache = mutableMapOf<Int, List<ScheduledEventOccurrence>>()
    private var occurrenceIndexCache: Map<String, ScheduledEventOccurrence>? = null
    /**
     * 중앙은행 경로는 질의 순서가 아닌 전체 불변 일정에 기반한다. 임의의 구간을 먼저
     * 조회해도 각 회의의 예상치·결정치가 달라지지 않는다.
     */
    private val centralBankMeetingsByKind: Map<ScheduledEventKind, List<ScheduledEventOccurrence>> =
        (EconomicReleaseCatalog.FIRST_YEAR..EconomicReleaseCatalog.LAST_YEAR)
            .flatMap(EconomicReleaseCatalog::occurrencesForYear)
            .filter { it.kind == ScheduledEventKind.US_FOMC || it.kind == ScheduledEventKind.KR_BOK }
            .sortedWith(OCCURRENCE_ORDER)
            .groupBy(ScheduledEventOccurrence::kind)

    fun occurrencesForYear(
        year: Int,
        stocks: List<StockDefinition> = StockCatalog.definitions,
    ): List<ScheduledEventOccurrence> = annualCalendar(year, stocks)

    /** Returns every occurrence in the half-open interval [from, to), without an emission cap. */
    fun occurrencesBetween(
        from: Instant,
        to: Instant,
        stocks: List<StockDefinition> = StockCatalog.definitions,
    ): List<ScheduledEventOccurrence> {
        require(to >= from) { "Scheduled event interval cannot run backwards" }
        if (from == to) return emptyList()
        val firstYear = (from.toLocalDateTime(GameCalendar.KOREA_TIME_ZONE).year - 1)
            .coerceAtLeast(EconomicReleaseCatalog.FIRST_YEAR)
        val lastYear = (to.toLocalDateTime(GameCalendar.KOREA_TIME_ZONE).year + 1)
            .coerceAtMost(EconomicReleaseCatalog.LAST_YEAR)
        if (firstYear > lastYear) return emptyList()
        return (firstYear..lastYear)
            .flatMap { annualCalendar(it, stocks) }
            .filter { it.scheduledAt >= from && it.scheduledAt < to }
            .sortedWith(OCCURRENCE_ORDER)
    }

    fun generate(
        from: Instant,
        to: Instant,
        stocks: List<StockDefinition> = StockCatalog.definitions,
    ): ScheduledEventGenerationResult = ScheduledEventGenerationResult(
        emissions = occurrencesBetween(from, to, stocks).map { emissionFor(it, stocks) },
    )

    /**
     * Builds at most one year at a time and returns as soon as [limit] is satisfied. This keeps the
     * hourly UI preview bounded instead of materializing the full 2026–2040 earnings calendar.
     */
    fun upcoming(
        from: Instant,
        stocks: List<StockDefinition> = StockCatalog.definitions,
        limit: Int = 12,
    ): List<ScheduledEventOccurrence> {
        require(limit >= 0)
        if (limit == 0 || from > GameCalendar.endInstant) return emptyList()
        val startYear = minOf(
            from.toLocalDateTime(GameCalendar.KOREA_TIME_ZONE).year,
            from.toLocalDateTime(GameCalendar.NEW_YORK_TIME_ZONE).year,
        ).coerceAtLeast(EconomicReleaseCatalog.FIRST_YEAR)
        val result = mutableListOf<ScheduledEventOccurrence>()
        for (year in startYear..EconomicReleaseCatalog.LAST_YEAR) {
            result += annualCalendar(year, stocks).filter {
                it.scheduledAt >= from && it.scheduledAt <= GameCalendar.endInstant
            }
            result.sortWith(OCCURRENCE_ORDER)
            if (result.size >= limit) return result.take(limit)
        }
        return result.take(limit)
    }

    fun outcomeFor(
        occurrence: ScheduledEventOccurrence,
        stocks: List<StockDefinition> = StockCatalog.definitions,
    ): ScheduledEventOutcome {
        val random = DeterministicRandom.keyed(seed, occurrence.id)
        return if (occurrence.kind == ScheduledEventKind.EARNINGS) {
            earningsOutcome(occurrence, stocks, random)
        } else {
            economicOutcome(occurrence, random)
        }
    }

    fun emissionFor(
        occurrence: ScheduledEventOccurrence,
        stocks: List<StockDefinition> = StockCatalog.definitions,
    ): ScheduledEventEmission {
        val outcome = outcomeFor(occurrence, stocks)
        val impactStartsAt = nextImpactStart(occurrence)
        val effectDuration = effectDurationHours(occurrence.kind)
        val delayNanos = (impactStartsAt - occurrence.scheduledAt).inWholeNanoseconds.coerceAtLeast(0L)
        val delayHoursCeiling = ((delayNanos + NANOS_PER_HOUR - 1L) / NANOS_PER_HOUR).toInt()
        val description = buildDescription(occurrence, outcome)
        val newsEvent = GameEvent(
            id = occurrence.id,
            title = occurrence.title,
            description = description,
            scope = occurrence.scope,
            type = occurrence.kind.eventType,
            severity = outcome.severity,
            impact = outcome.impact,
            startsAt = occurrence.scheduledAt,
            durationHours = delayHoursCeiling + effectDuration,
            recordKind = EventRecordKind.SCHEDULED_RELEASE,
            scheduledEventReference = ScheduledEventReference.from(occurrence),
            effectStartsAt = impactStartsAt,
            effectDurationHours = effectDuration,
            affectedMarkets = occurrence.affectedMarkets,
            affectedStockIds = occurrence.affectedStockIds,
            sourceLabel = occurrence.labels.joinToString(" · "),
            impactInsights = impactInsightsFor(occurrence, outcome, stocks),
            reportedFacts = outcome.metrics.map { metric ->
                ReportedFact(
                    label = metric.label,
                    actual = "${formatMetric(metric.actual, metric.decimalPlaces)}${metric.unit}",
                    comparison = "예상 ${formatMetric(metric.consensus, metric.decimalPlaces)}${metric.unit}",
                )
            },
        )
        return ScheduledEventEmission(
            occurrence = occurrence,
            outcome = outcome,
            newsEvent = newsEvent,
            impactEvent = newsEvent.copy(
                startsAt = impactStartsAt,
                durationHours = effectDuration,
            ),
        )
    }

    /**
     * 구조화 참조를 현재 일정 카탈로그와 종목 정의에서 찾아 canonical 뉴스를 재생성한다.
     * ID의 접두사·날짜·종목 부분을 해석하지 않고, 정확한 ID와 종류가 모두 일치할 때만
     * 결과를 반환한다.
     */
    internal fun canonicalNewsEventFor(
        reference: ScheduledEventReference,
        stocks: List<StockDefinition> = StockCatalog.definitions,
    ): GameEvent? {
        val occurrence = occurrenceIndex(stocks)[reference.occurrenceId]
            ?.takeIf { it.kind == reference.kind }
            ?: return null
        return emissionFor(occurrence, stocks).newsEvent
    }

    /**
     * 저장된 정기 발표를 현재 seed·일정 카탈로그·종목으로 재생성한 canonical 뉴스와
     * [GameEvent.equals]로 전체 비교한다. 이 비교는 발표 실제치·예상치가 든 본문과 reportedFacts,
     * 방향·충격, 실제 반영 구간, 영향 경로를 포함한 모든 필드를 엄격히 검증한다.
     */
    internal fun isCanonicalNewsEvent(
        event: GameEvent,
        stocks: List<StockDefinition> = StockCatalog.definitions,
    ): Boolean {
        if (event.recordKind != EventRecordKind.SCHEDULED_RELEASE) return false
        val reference = event.scheduledEventReference ?: return false
        return canonicalNewsEventFor(reference, stocks) == event
    }

    /** Scheduled shocks overlapping [from, to), including releases from an earlier closed session. */
    fun impactEventsBetween(
        from: Instant,
        to: Instant,
        stocks: List<StockDefinition> = StockCatalog.definitions,
    ): List<GameEvent> {
        require(to >= from) { "Scheduled impact interval cannot run backwards" }
        if (from == to) return emptyList()
        val lookback = from - IMPACT_LOOKBACK_HOURS.hours
        return occurrencesBetween(lookback, to, stocks)
            .asSequence()
            .map { emissionFor(it, stocks).impactEvent }
            .filter { it.startsAt < to && it.endsAt > from }
            .sortedWith(compareBy(GameEvent::startsAt, GameEvent::id))
            .toList()
    }

    fun activeImpactEventsAt(
        time: Instant,
        stocks: List<StockDefinition> = StockCatalog.definitions,
    ): List<GameEvent> = impactEventsBetween(time - 1.hours, time + 1.hours, stocks)
        .filter { it.isActiveAt(time) }

    private fun annualCalendar(year: Int, stocks: List<StockDefinition>): List<ScheduledEventOccurrence> {
        val stockSnapshot = stocks.toList()
        val stockIds = stockSnapshot.map(StockDefinition::id)
        require(stockIds.distinct().size == stockIds.size) { "Scheduled event stocks must have unique ids" }
        if (stockSnapshot != cachedStocks) {
            cachedStocks = stockSnapshot
            yearCache.clear()
            occurrenceIndexCache = null
        }
        return yearCache.getOrPut(year) {
            (EconomicReleaseCatalog.occurrencesForYear(year) +
                EarningsCalendarCatalog.occurrencesForYear(year, stocks))
                .sortedWith(OCCURRENCE_ORDER)
        }
    }

    private fun occurrenceIndex(stocks: List<StockDefinition>): Map<String, ScheduledEventOccurrence> {
        // annualCalendar owns cache invalidation for the complete stock definitions, not just IDs.
        annualCalendar(EconomicReleaseCatalog.FIRST_YEAR, stocks)
        occurrenceIndexCache?.let { return it }
        val occurrences = (EconomicReleaseCatalog.FIRST_YEAR..EconomicReleaseCatalog.LAST_YEAR)
            .flatMap { annualCalendar(it, stocks) }
        val index = occurrences.associateBy(ScheduledEventOccurrence::id)
        require(index.size == occurrences.size) { "Scheduled occurrence ids must be unique across the campaign" }
        occurrenceIndexCache = index
        return index
    }

    private fun economicOutcome(
        occurrence: ScheduledEventOccurrence,
        random: DeterministicRandom,
    ): ScheduledEventOutcome {
        if (occurrence.kind == ScheduledEventKind.US_FOMC || occurrence.kind == ScheduledEventKind.KR_BOK) {
            val metric = centralBankRateMetric(occurrence)
            val surprise = (
                (metric.actual - metric.consensus) / DOUBLE_RATE_STEP_PERCENT
            ).coerceIn(-1.0, 1.0)
            return outcome(
                occurrence = occurrence,
                surprise = surprise,
                metrics = listOf(metric),
            )
        }
        val rawSurprise = (random.nextGaussian() / SURPRISE_NORMALIZER).coerceIn(-1.0, 1.0)
        val metric = economicMetric(occurrence.kind, rawSurprise, random)
        return outcome(
            occurrence = occurrence,
            surprise = rawSurprise,
            metrics = listOf(metric),
        )
    }

    private fun earningsOutcome(
        occurrence: ScheduledEventOccurrence,
        stocks: List<StockDefinition>,
        random: DeterministicRandom,
    ): ScheduledEventOutcome {
        val stockId = occurrence.affectedStockIds.single()
        val stock = stocks.firstOrNull { it.id == stockId }
            ?: error("Earnings occurrence references unknown stock '$stockId'")
        val epsSurprise = (random.nextGaussian() / SURPRISE_NORMALIZER).coerceIn(-1.0, 1.0)
        val revenueSurprise = (random.nextGaussian() / SURPRISE_NORMALIZER).coerceIn(-1.0, 1.0)
        // marketCap/current shares is invariant to a split when the runtime updates the dynamic
        // StockDefinition. initialPrice is intentionally not used because it is never split-adjusted.
        val currentPriceAnchor = stock.marketCap / stock.sharesOutstanding.toDouble()
        val epsConsensus = currentPriceAnchor * random.nextDouble(0.010, 0.025)
        val revenueConsensus = stock.marketCap / 1_000_000_000.0 * random.nextDouble(0.018, 0.040)
        val eps = ScheduledEventMetric(
            label = "EPS",
            actual = epsConsensus * (1.0 + epsSurprise * 0.28),
            consensus = epsConsensus,
            unit = if (stock.currency == Currency.KRW) "KRW" else "USD",
            decimalPlaces = if (stock.currency == Currency.KRW) 0 else 2,
            kind = ScheduledEventMetricKind.EARNINGS_DILUTED_EPS,
        )
        val revenue = ScheduledEventMetric(
            label = "매출",
            actual = revenueConsensus * (1.0 + revenueSurprise * 0.16),
            consensus = revenueConsensus,
            unit = if (stock.currency == Currency.KRW) "십억 KRW" else "십억 USD",
            decimalPlaces = if (stock.currency == Currency.KRW) 0 else 1,
            kind = ScheduledEventMetricKind.EARNINGS_REVENUE,
            valueScale = 1_000_000_000.0,
        )
        return outcome(
            occurrence = occurrence,
            surprise = (epsSurprise * 0.6 + revenueSurprise * 0.4).coerceIn(-1.0, 1.0),
            metrics = listOf(eps, revenue),
        )
    }

    private fun economicMetric(
        kind: ScheduledEventKind,
        surprise: Double,
        random: DeterministicRandom,
    ): ScheduledEventMetric {
        fun metric(
            label: String,
            consensus: Double,
            surpriseScale: Double,
            unit: String,
            decimals: Int,
        ) = ScheduledEventMetric(
            label = label,
            actual = consensus + surprise * surpriseScale,
            consensus = consensus,
            unit = unit,
            decimalPlaces = decimals,
        )

        return when (kind) {
            ScheduledEventKind.US_EMPLOYMENT -> metric(
                "비농업 고용", random.nextDouble(120.0, 260.0), 100.0, "천명", 0,
            )
            ScheduledEventKind.US_CPI -> metric(
                "CPI 전년비", random.nextDouble(1.8, 3.8), 0.55, "%", 1,
            )
            ScheduledEventKind.US_PCE -> metric(
                "근원 PCE 전년비", random.nextDouble(1.7, 3.4), 0.45, "%", 1,
            )
            ScheduledEventKind.US_GDP -> metric(
                "GDP 연율", random.nextDouble(0.5, 3.6), 1.15, "%", 1,
            )
            ScheduledEventKind.US_FOMC,
            ScheduledEventKind.KR_BOK,
            -> error("Central-bank rates use the continuous meeting series")
            ScheduledEventKind.US_RETAIL_SALES -> metric(
                "소매판매 전월비", random.nextDouble(-0.3, 1.0), 0.85, "%", 1,
            )
            ScheduledEventKind.US_WEEKLY_CLAIMS -> metric(
                "신규 청구", random.nextDouble(185.0, 285.0), 55.0, "천건", 0,
            )
            ScheduledEventKind.KR_CPI -> metric(
                "CPI 전년비", random.nextDouble(1.5, 3.5), 0.50, "%", 1,
            )
            ScheduledEventKind.KR_EMPLOYMENT -> metric(
                "취업자 증감", random.nextDouble(100.0, 420.0), 120.0, "천명", 0,
            )
            ScheduledEventKind.KR_GDP -> metric(
                "GDP 전년비", random.nextDouble(0.8, 3.4), 0.90, "%", 1,
            )
            ScheduledEventKind.EARNINGS -> error("Earnings uses two company metrics")
        }
    }

    /**
     * 이전 회의의 결정치를 다음 회의 예상치로 이어 붙인다. 모든 의사결정 무작위는
     * 발생 ID와 캠페인 시드에만 묶이므로 넓은 구간·좁은 구간 조회가 같은 결과를 낸다.
     */
    private fun centralBankRateMetric(occurrence: ScheduledEventOccurrence): ScheduledEventMetric {
        val specification = CentralBankRateSpecification.forKind(occurrence.kind)
        var state = CentralBankRateState(actual = specification.initialRate)
        for (meeting in centralBankMeetingsByKind.getValue(occurrence.kind)) {
            if (OCCURRENCE_ORDER.compare(meeting, occurrence) >= 0) break
            state = applyCentralBankDecision(meeting, state, specification)
        }
        val consensus = state.actual
        val decided = applyCentralBankDecision(occurrence, state, specification)
        return ScheduledEventMetric(
            label = specification.metricLabel,
            actual = decided.actual,
            consensus = consensus,
            unit = "%",
            decimalPlaces = 2,
        )
    }

    /**
     * 동결이 대부분이고, 변경은 25bp가 표준이며 50bp는 드물다. 중립금리로의 완만한
     * 회귀와 직전 비동결 방향의 약한 연속성을 두어 무작위 직지그재그보다 긴축·완화
     * 주기에 가까운 경로를 만든다.
     */
    private fun applyCentralBankDecision(
        meeting: ScheduledEventOccurrence,
        previous: CentralBankRateState,
        specification: CentralBankRateSpecification,
    ): CentralBankRateState {
        val random = DeterministicRandom.keyed(seed, "central-bank-rate:${meeting.id}")
        val distanceFromNeutralSteps = abs(previous.actual - specification.neutralRate) / RATE_STEP_PERCENT
        val moveProbability = (
            BASE_RATE_MOVE_PROBABILITY + distanceFromNeutralSteps * DISTANCE_MOVE_PROBABILITY
        ).coerceIn(BASE_RATE_MOVE_PROBABILITY, MAX_RATE_MOVE_PROBABILITY)
        if (!random.nextBoolean(moveProbability)) return previous

        val magnitude = if (random.nextBoolean(RARE_DOUBLE_STEP_PROBABILITY)) {
            DOUBLE_RATE_STEP_PERCENT
        } else {
            RATE_STEP_PERCENT
        }
        val equilibriumBias = (
            (specification.neutralRate - previous.actual) / RATE_STEP_PERCENT * EQUILIBRIUM_DIRECTION_BIAS
        )
        val momentumBias = when {
            previous.lastPolicyMove > 0.0 -> POLICY_MOMENTUM_BIAS
            previous.lastPolicyMove < 0.0 -> -POLICY_MOMENTUM_BIAS
            else -> 0.0
        }
        val upwardProbability = (0.5 + equilibriumBias + momentumBias).coerceIn(
            MIN_DIRECTION_PROBABILITY,
            MAX_DIRECTION_PROBABILITY,
        )
        val direction = when {
            previous.actual <= specification.minimumRate -> 1.0
            previous.actual >= specification.maximumRate -> -1.0
            random.nextBoolean(upwardProbability) -> 1.0
            else -> -1.0
        }
        val actual = (previous.actual + direction * magnitude)
            .coerceIn(specification.minimumRate, specification.maximumRate)
        return CentralBankRateState(
            actual = actual,
            lastPolicyMove = actual - previous.actual,
        )
    }

    private fun outcome(
        occurrence: ScheduledEventOccurrence,
        surprise: Double,
        metrics: List<ScheduledEventMetric>,
    ): ScheduledEventOutcome {
        val comparison = when {
            surprise > COMPARISON_THRESHOLD -> ScheduledOutcomeComparison.ABOVE
            surprise < -COMPARISON_THRESHOLD -> ScheduledOutcomeComparison.BELOW
            else -> ScheduledOutcomeComparison.INLINE
        }
        val marketPolarity = when (occurrence.kind) {
            ScheduledEventKind.US_CPI,
            ScheduledEventKind.US_PCE,
            ScheduledEventKind.US_FOMC,
            ScheduledEventKind.US_WEEKLY_CLAIMS,
            ScheduledEventKind.KR_CPI,
            ScheduledEventKind.KR_BOK,
            -> -1.0
            else -> 1.0
        }
        val signal = surprise * marketPolarity
        val direction = when {
            signal > DIRECTION_THRESHOLD -> ImpactDirection.POSITIVE
            signal < -DIRECTION_THRESHOLD -> ImpactDirection.NEGATIVE
            else -> ImpactDirection.NEUTRAL
        }
        val magnitude = abs(signal)
        val severity = when {
            magnitude >= 0.82 -> EventSeverity.MAJOR
            magnitude >= 0.38 -> EventSeverity.MODERATE
            else -> EventSeverity.MINOR
        }
        val vintageMultiplier = when (occurrence.vintage) {
            com.amond.kmpbook.domain.model.schedule.EconomicReleaseVintage.SECOND -> 0.72
            com.amond.kmpbook.domain.model.schedule.EconomicReleaseVintage.THIRD -> 0.52
            else -> 1.0
        }
        val shock = if (direction == ImpactDirection.NEUTRAL) 0.0 else {
            baseShock(occurrence.kind) * vintageMultiplier * signal * (0.45 + magnitude * 0.55)
        }
        return ScheduledEventOutcome(
            surpriseScore = surprise,
            comparison = comparison,
            metrics = metrics,
            direction = direction,
            severity = severity,
            impact = GameEventImpact(
                direction = direction,
                shockReturn = shock,
                // The decaying level shock is offset by drift over the effect window, leaving the
                // scheduled surprise as a lasting repricing instead of mechanically reversing it.
                hourlyDrift = if (shock == 0.0) {
                    0.0
                } else {
                    ln(1.0 + shock) / effectDurationHours(occurrence.kind)
                },
                volatilityMultiplier = 1.0 + magnitude * 0.9,
                volumeMultiplier = 1.0 + magnitude * 1.8,
                liquidityMultiplier = 1.0 - magnitude * 0.28,
                sentiment = (signal * 0.72).coerceIn(-1.0, 1.0),
            ),
        )
    }

    private fun buildDescription(
        occurrence: ScheduledEventOccurrence,
        outcome: ScheduledEventOutcome,
    ): String = buildString {
        append(outcome.comparison.displayName)
        append(" · ")
        append(
            outcome.metrics.joinToString(" · ") { metric ->
                "${metric.label} 실제 ${formatMetric(metric.actual, metric.decimalPlaces)}${metric.unit}, " +
                    "예상 ${formatMetric(metric.consensus, metric.decimalPlaces)}${metric.unit}"
            },
        )
        occurrence.referencePeriod?.let { append(" · $it") }
    }

    private fun impactInsightsFor(
        occurrence: ScheduledEventOccurrence,
        outcome: ScheduledEventOutcome,
        stocks: List<StockDefinition>,
    ): List<EventImpactInsight> {
        val rationale = "${outcome.comparison.displayName}으로 발표된 결과가 단기 기대와 위험선호에 반영된다."
        if (occurrence.kind == ScheduledEventKind.EARNINGS) {
            val stockId = occurrence.affectedStockIds.single()
            val stock = stocks.firstOrNull { it.id == stockId }
                ?: error("Earnings occurrence references unknown stock '$stockId'")
            return listOf(
                EventImpactInsight(
                    targetKind = EventImpactTargetKind.STOCK,
                    targetLabel = "${stock.name} (${stock.symbol})",
                    direction = outcome.direction,
                    rationale = rationale,
                    sector = stock.sector,
                    markets = setOf(stock.market),
                    stockId = stock.id,
                    horizon = EventImpactHorizon.IMMEDIATE,
                ),
            )
        }
        val markets = occurrence.affectedMarkets
        val targetLabel = when {
            markets.all(Market::isKorean) -> "대한민국 주식시장"
            markets.all(Market::isUnitedStates) -> "미국 주식시장"
            else -> markets.sortedBy(Market::name).joinToString("·", transform = Market::displayName)
        }
        return listOf(
            EventImpactInsight(
                targetKind = EventImpactTargetKind.MARKET,
                targetLabel = targetLabel,
                direction = outcome.direction,
                rationale = rationale,
                markets = markets,
                horizon = EventImpactHorizon.IMMEDIATE,
            ),
        )
    }

    private fun nextImpactStart(occurrence: ScheduledEventOccurrence): Instant =
        occurrence.affectedMarkets.minOf { market -> nextRegularTradingInstant(market, occurrence.scheduledAt) }

    private fun nextRegularTradingInstant(market: Market, releaseAt: Instant): Instant {
        val zone = GameCalendar.timeZoneFor(market)
        var date: LocalDate = releaseAt.toLocalDateTime(zone).date
        repeat(MAX_SESSION_SEARCH_DAYS) {
            val closedDates = if (date.year in EconomicReleaseCatalog.FIRST_YEAR..EconomicReleaseCatalog.LAST_YEAR) {
                DefaultMarketHolidays.closedDates(market, date.year)
            } else {
                emptySet()
            }
            val window = GameCalendar.regularSessionWindow(market, date, closedDates)
            if (window != null && releaseAt < window.closesAt) {
                return maxOf(releaseAt, window.opensAt)
            }
            date = date.plus(1, DateTimeUnit.DAY)
        }
        error("Could not find a regular session after $releaseAt for $market")
    }

    private fun baseShock(kind: ScheduledEventKind): Double = when (kind) {
        ScheduledEventKind.EARNINGS -> 0.12
        ScheduledEventKind.US_FOMC, ScheduledEventKind.KR_BOK -> 0.045
        ScheduledEventKind.US_GDP, ScheduledEventKind.KR_GDP -> 0.032
        ScheduledEventKind.US_EMPLOYMENT, ScheduledEventKind.KR_EMPLOYMENT -> 0.026
        ScheduledEventKind.US_CPI, ScheduledEventKind.US_PCE, ScheduledEventKind.KR_CPI -> 0.030
        ScheduledEventKind.US_RETAIL_SALES -> 0.022
        ScheduledEventKind.US_WEEKLY_CLAIMS -> 0.015
    }

    private fun effectDurationHours(kind: ScheduledEventKind): Int = when (kind) {
        ScheduledEventKind.EARNINGS -> 30
        ScheduledEventKind.US_FOMC, ScheduledEventKind.KR_BOK -> 30
        else -> 18
    }

    private fun formatMetric(value: Double, decimalPlaces: Int): String {
        val scale = 10.0.pow(decimalPlaces)
        val scaled = round(value * scale).toLong()
        if (decimalPlaces == 0) return scaled.toString()
        val absolute = abs(scaled)
        val whole = absolute / scale.toLong()
        val fraction = (absolute % scale.toLong()).toString().padStart(decimalPlaces, '0')
        return (if (scaled < 0) "-" else "") + "$whole.$fraction"
    }

    companion object {
        /** Stable substream shared by runtime, projections, and current-save validation. */
        const val STREAM_ID: Long = 0x5343484544554C45L

        private val OCCURRENCE_ORDER = compareBy(ScheduledEventOccurrence::scheduledAt, ScheduledEventOccurrence::id)
        private const val NANOS_PER_HOUR: Long = 3_600_000_000_000L
        private const val IMPACT_LOOKBACK_HOURS: Int = 24 * 10
        private const val MAX_SESSION_SEARCH_DAYS: Int = 16
        private const val SURPRISE_NORMALIZER: Double = 2.25
        private const val COMPARISON_THRESHOLD: Double = 0.10
        private const val DIRECTION_THRESHOLD: Double = 0.08
        private const val RATE_STEP_PERCENT: Double = 0.25
        private const val DOUBLE_RATE_STEP_PERCENT: Double = 0.50
        private const val BASE_RATE_MOVE_PROBABILITY: Double = 0.34
        private const val DISTANCE_MOVE_PROBABILITY: Double = 0.025
        private const val MAX_RATE_MOVE_PROBABILITY: Double = 0.58
        private const val RARE_DOUBLE_STEP_PROBABILITY: Double = 0.06
        private const val EQUILIBRIUM_DIRECTION_BIAS: Double = 0.04
        private const val POLICY_MOMENTUM_BIAS: Double = 0.08
        private const val MIN_DIRECTION_PROBABILITY: Double = 0.15
        private const val MAX_DIRECTION_PROBABILITY: Double = 0.85
    }

}
