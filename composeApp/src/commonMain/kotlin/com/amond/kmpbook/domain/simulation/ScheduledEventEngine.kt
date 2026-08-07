package com.amond.kmpbook.domain.simulation

import com.amond.kmpbook.domain.data.StockCatalog
import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.EventSeverity
import com.amond.kmpbook.domain.model.GameEvent
import com.amond.kmpbook.domain.model.GameEventImpact
import com.amond.kmpbook.domain.model.ImpactDirection
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.ScheduledEventEmission
import com.amond.kmpbook.domain.model.ScheduledEventKind
import com.amond.kmpbook.domain.model.ScheduledEventMetric
import com.amond.kmpbook.domain.model.ScheduledEventOccurrence
import com.amond.kmpbook.domain.model.ScheduledEventOutcome
import com.amond.kmpbook.domain.model.ScheduledOutcomeComparison
import com.amond.kmpbook.domain.model.StockDefinition
import com.amond.kmpbook.domain.time.DefaultMarketHolidays
import com.amond.kmpbook.domain.time.GameCalendar
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.round
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

data class ScheduledEventGenerationResult(
    val emissions: List<ScheduledEventEmission>,
) {
    val newEvents: List<GameEvent> get() = emissions.map(ScheduledEventEmission::newsEvent)
}

/**
 * Stateless-by-contract scheduled event generator. Its caches only memoize the pure calendar;
 * schedule and ids do not use [seed]. Outcome streams are keyed by occurrence id, so broad and
 * narrow queries produce identical figures and impacts without a sequence counter or save field.
 */
class ScheduledEventEngine(private val seed: Long) {
    private var cachedStockIds: List<String> = emptyList()
    private val yearCache = mutableMapOf<Int, List<ScheduledEventOccurrence>>()

    fun occurrencesForYear(
        year: Int,
        stocks: List<StockDefinition> = StockCatalog.all,
    ): List<ScheduledEventOccurrence> = annualCalendar(year, stocks)

    /** Returns every occurrence in the half-open interval [from, to), without an emission cap. */
    fun occurrencesBetween(
        from: Instant,
        to: Instant,
        stocks: List<StockDefinition> = StockCatalog.all,
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
        stocks: List<StockDefinition> = StockCatalog.all,
    ): ScheduledEventGenerationResult = ScheduledEventGenerationResult(
        emissions = occurrencesBetween(from, to, stocks).map { emissionFor(it, stocks) },
    )

    /**
     * Builds at most one year at a time and returns as soon as [limit] is satisfied. This keeps the
     * hourly UI preview bounded instead of materializing the full 2026–2040 earnings calendar.
     */
    fun upcoming(
        from: Instant,
        stocks: List<StockDefinition> = StockCatalog.all,
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
        stocks: List<StockDefinition> = StockCatalog.all,
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
        stocks: List<StockDefinition> = StockCatalog.all,
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
            affectedMarkets = occurrence.affectedMarkets,
            affectedStockIds = occurrence.affectedStockIds,
            sourceLabel = occurrence.labels.joinToString(" · "),
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

    /** Scheduled shocks overlapping [from, to), including releases from an earlier closed session. */
    fun impactEventsBetween(
        from: Instant,
        to: Instant,
        stocks: List<StockDefinition> = StockCatalog.all,
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
        stocks: List<StockDefinition> = StockCatalog.all,
    ): List<GameEvent> = impactEventsBetween(time - 1.hours, time + 1.hours, stocks)
        .filter { it.isActiveAt(time) }

    private fun annualCalendar(year: Int, stocks: List<StockDefinition>): List<ScheduledEventOccurrence> {
        val stockIds = stocks.map(StockDefinition::id)
        require(stockIds.distinct().size == stockIds.size) { "Scheduled event stocks must have unique ids" }
        if (stockIds != cachedStockIds) {
            cachedStockIds = stockIds.toList()
            yearCache.clear()
        }
        return yearCache.getOrPut(year) {
            (EconomicReleaseCatalog.occurrencesForYear(year) +
                EarningsCalendarCatalog.occurrencesForYear(year, stocks))
                .sortedWith(OCCURRENCE_ORDER)
        }
    }

    private fun economicOutcome(
        occurrence: ScheduledEventOccurrence,
        random: DeterministicRandom,
    ): ScheduledEventOutcome {
        val rawSurprise = (random.nextGaussian() / SURPRISE_NORMALIZER).coerceIn(-1.0, 1.0)
        val metric = economicMetric(occurrence.kind, rawSurprise, random)
        val surprise = when (occurrence.kind) {
            ScheduledEventKind.US_FOMC, ScheduledEventKind.KR_BOK -> {
                ((metric.actual - metric.consensus) / RATE_STEP_PERCENT).coerceIn(-1.0, 1.0)
            }
            else -> rawSurprise
        }
        return outcome(
            occurrence = occurrence,
            surprise = surprise,
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
        val epsConsensus = stock.initialPrice * random.nextDouble(0.010, 0.025)
        val revenueConsensus = stock.marketCap / 1_000_000_000.0 * random.nextDouble(0.018, 0.040)
        val eps = ScheduledEventMetric(
            label = "EPS",
            actual = epsConsensus * (1.0 + epsSurprise * 0.28),
            consensus = epsConsensus,
            unit = if (stock.currency == Currency.KRW) "KRW" else "USD",
            decimalPlaces = if (stock.currency == Currency.KRW) 0 else 2,
        )
        val revenue = ScheduledEventMetric(
            label = "매출",
            actual = revenueConsensus * (1.0 + revenueSurprise * 0.16),
            consensus = revenueConsensus,
            unit = if (stock.currency == Currency.KRW) "십억 KRW" else "십억 USD",
            decimalPlaces = if (stock.currency == Currency.KRW) 0 else 1,
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
            ScheduledEventKind.US_FOMC -> rateMetric("연방기금금리", random, surprise)
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
            ScheduledEventKind.KR_BOK -> rateMetric("한국 기준금리", random, surprise)
            ScheduledEventKind.KR_GDP -> metric(
                "GDP 전년비", random.nextDouble(0.8, 3.4), 0.90, "%", 1,
            )
            ScheduledEventKind.EARNINGS -> error("Earnings uses two company metrics")
        }
    }

    private fun rateMetric(
        label: String,
        random: DeterministicRandom,
        surprise: Double,
    ): ScheduledEventMetric {
        val consensus = round(random.nextDouble(1.5, 5.0) * 4.0).toLong() / 4.0
        val step = when {
            surprise >= RATE_SURPRISE_THRESHOLD -> RATE_STEP_PERCENT
            surprise <= -RATE_SURPRISE_THRESHOLD -> -RATE_STEP_PERCENT
            else -> 0.0
        }
        return ScheduledEventMetric(label, consensus + step, consensus, "%", 2)
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
            com.amond.kmpbook.domain.model.EconomicReleaseVintage.SECOND -> 0.72
            com.amond.kmpbook.domain.model.EconomicReleaseVintage.THIRD -> 0.52
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
        append(". 모든 발표값은 실제 자료가 아닌 occurrence-keyed 게임 수치입니다.")
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

    private companion object {
        val OCCURRENCE_ORDER = compareBy(ScheduledEventOccurrence::scheduledAt, ScheduledEventOccurrence::id)
        const val NANOS_PER_HOUR: Long = 3_600_000_000_000L
        const val IMPACT_LOOKBACK_HOURS: Int = 24 * 10
        const val MAX_SESSION_SEARCH_DAYS: Int = 16
        const val SURPRISE_NORMALIZER: Double = 2.25
        const val COMPARISON_THRESHOLD: Double = 0.10
        const val DIRECTION_THRESHOLD: Double = 0.08
        const val RATE_SURPRISE_THRESHOLD: Double = 0.52
        const val RATE_STEP_PERCENT: Double = 0.25
    }
}
