package com.amond.kmpbook.domain.simulation

import com.amond.kmpbook.domain.model.EventScope
import com.amond.kmpbook.domain.model.EventSeverity
import com.amond.kmpbook.domain.model.EventType
import com.amond.kmpbook.domain.model.GameEvent
import com.amond.kmpbook.domain.model.GameEventImpact
import com.amond.kmpbook.domain.model.ImpactDirection
import com.amond.kmpbook.domain.model.InstrumentStrategy
import com.amond.kmpbook.domain.model.InstrumentType
import com.amond.kmpbook.domain.model.ListingFinalDispositionType
import com.amond.kmpbook.domain.model.ListingRecoveryCondition
import com.amond.kmpbook.domain.model.ListingRiskTag
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.Sector
import com.amond.kmpbook.domain.model.StockDefinition
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.time.Instant

enum class EventCondition {
    ALWAYS,
    POLICY_RATE_HIGH,
    POLICY_RATE_LOW,
    POLICY_RATE_RISING,
    POLICY_RATE_FALLING,
    INFLATION_HIGH,
    INFLATION_COOLING,
    GROWTH_NEGATIVE,
    GROWTH_STRONG,
    KRW_WEAK,
    KRW_STRONG,
    RISK_OFF,
    RISK_ON,
    HIGH_VOLATILITY,
    MARKET_DRAWDOWN,
    MARKET_RALLY,
}

/** Declarative event rule. No random or clock state is hidden in a template. */
data class EventTemplate(
    val id: String,
    val titleTemplate: String,
    val descriptionTemplate: String,
    val scope: EventScope,
    val type: EventType,
    val severity: EventSeverity,
    val direction: ImpactDirection,
    /** Probability of at least one occurrence during a 24-hour game interval. */
    val probabilityPerDay: Double,
    val cooldownHours: Int,
    val durationHours: IntRange,
    val shockReturn: ClosedFloatingPointRange<Double>,
    val hourlyDrift: ClosedFloatingPointRange<Double> = 0.0..0.0,
    val volatilityMultiplier: ClosedFloatingPointRange<Double> = 1.0..1.0,
    val volumeMultiplier: ClosedFloatingPointRange<Double> = 1.0..1.0,
    val liquidityMultiplier: ClosedFloatingPointRange<Double> = 1.0..1.0,
    val sentiment: ClosedFloatingPointRange<Double> = 0.0..0.0,
    val condition: EventCondition = EventCondition.ALWAYS,
    val eligibleMarkets: Set<Market> = emptySet(),
    val eligibleSectors: Set<Sector> = emptySet(),
    val eligibleInstrumentTypes: Set<InstrumentType> = emptySet(),
    /** 비어 있으면 모든 전략, 값이 있으면 실제 수익 구조가 일치하는 상품만 후보가 된다. */
    val eligibleStrategies: Set<InstrumentStrategy> = emptySet(),
    /** 가격 효과와 독립적으로 상장 생애주기 엔진에 전달되는 거래소 감시 신호. */
    val listingRiskTags: Set<ListingRiskTag> = emptySet(),
    val listingRecoveryConditions: Set<ListingRecoveryCondition> = emptySet(),
    val listingFinalDispositionHint: ListingFinalDispositionType? = null,
    val sourceLabel: String = "시뮬레이션 뉴스",
) {
    init {
        require(id.isNotBlank())
        require(titleTemplate.isNotBlank() && descriptionTemplate.isNotBlank())
        require(probabilityPerDay in 0.0..1.0)
        require(cooldownHours >= 0)
        require(!durationHours.isEmpty() && durationHours.first > 0)
        require(shockReturn.start > -1.0 && shockReturn.endInclusive > -1.0)
        require(volatilityMultiplier.start >= 0.0)
        require(volumeMultiplier.start >= 0.0)
        require(liquidityMultiplier.start >= 0.0)
        require(sentiment.start >= -1.0 && sentiment.endInclusive <= 1.0)
    }
}

data class EventGenerationContext(
    val timestamp: Instant,
    val stocks: List<StockDefinition>,
    val macro: MacroEnvironment = MacroEnvironment(),
    val elapsedHours: Int = 1,
    /** Events restored or owned by an outer game state are also deduplicated. */
    val existingEvents: List<GameEvent> = emptyList(),
    val maxNewEvents: Int = 3,
) {
    init {
        require(elapsedHours > 0)
        require(maxNewEvents >= 0)
        require(stocks.map(StockDefinition::id).distinct().size == stocks.size) {
            "Event candidates must have unique stock ids"
        }
    }
}

data class EventEngineSnapshot(
    val randomState: Long,
    val sequence: Long,
    val lastTriggeredEpochSeconds: Map<String, Long>,
    val activeEvents: List<GameEvent>,
)

data class EventGenerationResult(
    val newEvents: List<GameEvent>,
    val activeEvents: List<GameEvent>,
    val snapshot: EventEngineSnapshot,
)

/**
 * Rule-driven, seeded event generator. A template cannot trigger while another
 * instance is active, and its cooldown starts at the prior trigger timestamp.
 */
class EventEngine(
    seed: Long,
    val templates: List<EventTemplate> = DefaultEventTemplates.all,
) {
    private val random = DeterministicRandom(seed)
    private var sequence: Long = 0L
    private val lastTriggeredAt = mutableMapOf<String, Long>()
    private val generatedActiveEvents = mutableListOf<GameEvent>()

    init {
        require(templates.map(EventTemplate::id).distinct().size == templates.size) {
            "Event template ids must be unique"
        }
    }

    fun generate(context: EventGenerationContext): EventGenerationResult {
        generatedActiveEvents.removeAll { !it.isActiveAt(context.timestamp) }
        if (context.maxNewEvents == 0 || context.stocks.isEmpty()) return result(emptyList())

        val externallyActive = context.existingEvents.filter { it.isActiveAt(context.timestamp) }
        val activeTemplateIds = (generatedActiveEvents + externallyActive)
            .mapNotNullTo(mutableSetOf(), ::templateIdFromEvent)
        val newEvents = mutableListOf<GameEvent>()

        for (template in templates) {
            if (newEvents.size >= context.maxNewEvents) break
            if (template.id in activeTemplateIds) continue
            if (!conditionMatches(template.condition, context.macro)) continue
            if (!hasEligibleTarget(template, context.stocks)) continue
            if (isCoolingDown(template, context.timestamp)) continue

            val probability = probabilityForInterval(template.probabilityPerDay, context.elapsedHours)
            if (!random.nextBoolean(probability)) continue

            val target = selectTarget(template, context.stocks) ?: continue
            val event = instantiate(template, target, context.timestamp)
            generatedActiveEvents += event
            newEvents += event
            activeTemplateIds += template.id
            lastTriggeredAt[template.id] = context.timestamp.epochSeconds
        }
        return result(newEvents)
    }

    fun snapshot(): EventEngineSnapshot = EventEngineSnapshot(
        randomState = random.snapshot(),
        sequence = sequence,
        lastTriggeredEpochSeconds = lastTriggeredAt.toMap(),
        activeEvents = generatedActiveEvents.toList(),
    )

    fun restore(snapshot: EventEngineSnapshot) {
        require(snapshot.sequence >= 0L)
        random.restore(snapshot.randomState)
        sequence = snapshot.sequence
        lastTriggeredAt.clear()
        lastTriggeredAt.putAll(snapshot.lastTriggeredEpochSeconds)
        generatedActiveEvents.clear()
        generatedActiveEvents.addAll(snapshot.activeEvents)
    }

    private fun result(newEvents: List<GameEvent>): EventGenerationResult {
        val state = snapshot()
        return EventGenerationResult(
            newEvents = newEvents,
            activeEvents = generatedActiveEvents.toList(),
            snapshot = state,
        )
    }

    private fun instantiate(
        template: EventTemplate,
        target: SelectedEventTarget,
        timestamp: Instant,
    ): GameEvent {
        val duration = template.durationHours.first +
            random.nextInt(template.durationHours.last - template.durationHours.first + 1)
        val title = interpolate(template.titleTemplate, target)
        val description = interpolate(template.descriptionTemplate, target)
        val id = "${template.id}:${timestamp.epochSeconds}:${sequence++}"
        return GameEvent(
            id = id,
            title = title,
            description = description,
            scope = template.scope,
            type = template.type,
            severity = template.severity,
            impact = GameEventImpact(
                direction = template.direction,
                shockReturn = randomIn(template.shockReturn),
                hourlyDrift = randomIn(template.hourlyDrift),
                volatilityMultiplier = randomIn(template.volatilityMultiplier),
                volumeMultiplier = randomIn(template.volumeMultiplier),
                liquidityMultiplier = randomIn(template.liquidityMultiplier),
                sentiment = randomIn(template.sentiment),
            ),
            startsAt = timestamp,
            durationHours = duration,
            affectedMarkets = target.markets,
            affectedSectors = target.sectors,
            affectedStockIds = target.stockIds,
            sourceLabel = template.sourceLabel,
            listingRiskTags = template.listingRiskTags,
            listingRecoveryConditions = template.listingRecoveryConditions,
            listingFinalDispositionHint = template.listingFinalDispositionHint,
        )
    }

    private fun selectTarget(
        template: EventTemplate,
        stocks: List<StockDefinition>,
    ): SelectedEventTarget? {
        val candidates = stocks.filter { stock ->
            (template.eligibleMarkets.isEmpty() || stock.market in template.eligibleMarkets) &&
                (template.eligibleSectors.isEmpty() || stock.sector in template.eligibleSectors) &&
                (template.eligibleInstrumentTypes.isEmpty() || stock.instrumentType in template.eligibleInstrumentTypes) &&
                (template.eligibleStrategies.isEmpty() || stock.behavior.strategy in template.eligibleStrategies)
        }
        if (candidates.isEmpty() && template.scope != EventScope.GLOBAL) return null

        return when (template.scope) {
            EventScope.GLOBAL -> SelectedEventTarget()
            EventScope.COUNTRY -> {
                val countries = candidates.groupBy { it.market.isKorean }
                val korean = random.choose(countries.keys.toList().sortedDescending())
                val countryMarkets = Market.entries.filterTo(mutableSetOf()) {
                    it.isKorean == korean && (template.eligibleMarkets.isEmpty() || it in template.eligibleMarkets)
                }
                SelectedEventTarget(
                    markets = countryMarkets,
                    marketName = if (korean) "한국" else "미국",
                )
            }

            EventScope.MARKET -> {
                val market = random.choose(candidates.map(StockDefinition::market).distinct().sortedBy(Market::name))
                SelectedEventTarget(markets = setOf(market), marketName = market.displayName)
            }

            EventScope.SECTOR -> {
                val sector = random.choose(candidates.map(StockDefinition::sector).distinct().sortedBy(Sector::name))
                SelectedEventTarget(sectors = setOf(sector), sectorName = sector.displayName)
            }

            EventScope.STOCK -> {
                val stock = random.choose(candidates.sortedBy(StockDefinition::id))
                SelectedEventTarget(
                    markets = setOf(stock.market),
                    sectors = setOf(stock.sector),
                    stockIds = setOf(stock.id),
                    companyName = stock.name,
                    symbol = stock.symbol,
                    marketName = stock.market.displayName,
                    sectorName = stock.sector.displayName,
                )
            }
        }
    }

    private fun hasEligibleTarget(template: EventTemplate, stocks: List<StockDefinition>): Boolean {
        if (template.scope == EventScope.GLOBAL) return true
        return stocks.any { stock ->
            (template.eligibleMarkets.isEmpty() || stock.market in template.eligibleMarkets) &&
                (template.eligibleSectors.isEmpty() || stock.sector in template.eligibleSectors) &&
                (template.eligibleInstrumentTypes.isEmpty() || stock.instrumentType in template.eligibleInstrumentTypes) &&
                (template.eligibleStrategies.isEmpty() || stock.behavior.strategy in template.eligibleStrategies)
        }
    }

    private fun isCoolingDown(template: EventTemplate, timestamp: Instant): Boolean {
        val previousEpoch = lastTriggeredAt[template.id] ?: return false
        val elapsedSeconds = timestamp.epochSeconds - previousEpoch
        return elapsedSeconds < template.cooldownHours.toLong() * SECONDS_PER_HOUR
    }

    private fun conditionMatches(condition: EventCondition, macro: MacroEnvironment): Boolean = when (condition) {
        EventCondition.ALWAYS -> true
        EventCondition.POLICY_RATE_HIGH -> macro.policyRate >= 0.045
        EventCondition.POLICY_RATE_LOW -> macro.policyRate <= 0.015
        EventCondition.POLICY_RATE_RISING -> macro.policyRateChange > 0.0
        EventCondition.POLICY_RATE_FALLING -> macro.policyRateChange < 0.0
        EventCondition.INFLATION_HIGH -> macro.inflationRate >= 0.035 || macro.inflationSurprise >= 0.75
        EventCondition.INFLATION_COOLING -> macro.inflationRate <= 0.022 && macro.inflationSurprise < 0.0
        EventCondition.GROWTH_NEGATIVE -> macro.growthRate < 0.0 || macro.growthSurprise <= -0.75
        EventCondition.GROWTH_STRONG -> macro.growthRate >= 0.03 || macro.growthSurprise >= 0.75
        EventCondition.KRW_WEAK -> macro.usdKrw >= 1_450.0
        EventCondition.KRW_STRONG -> macro.usdKrw <= 1_200.0
        EventCondition.RISK_OFF -> macro.riskSentiment <= -0.45
        EventCondition.RISK_ON -> macro.riskSentiment >= 0.45
        EventCondition.HIGH_VOLATILITY -> macro.volatilityRegime >= 1.5
        EventCondition.MARKET_DRAWDOWN -> macro.marketChangeFromPreviousClose.values.any { it <= -0.05 }
        EventCondition.MARKET_RALLY -> macro.marketChangeFromPreviousClose.values.any { it >= 0.04 }
    }

    private fun interpolate(template: String, target: SelectedEventTarget): String = template
        .replace("{company}", target.companyName)
        .replace("{symbol}", target.symbol)
        .replace("{market}", target.marketName)
        .replace("{sector}", target.sectorName)

    private fun randomIn(range: ClosedFloatingPointRange<Double>): Double =
        random.nextDouble(range.start, range.endInclusive)

    private fun templateIdFromEvent(event: GameEvent): String? =
        templates.firstOrNull { event.id.startsWith("${it.id}:") }?.id

    private data class SelectedEventTarget(
        val markets: Set<Market> = emptySet(),
        val sectors: Set<Sector> = emptySet(),
        val stockIds: Set<String> = emptySet(),
        val companyName: String = "해당 기업",
        val symbol: String = "",
        val marketName: String = "해당 시장",
        val sectorName: String = "해당 산업",
    )

    companion object {
        private const val SECONDS_PER_HOUR: Long = 3_600L

        internal fun probabilityForInterval(probabilityPerDay: Double, hours: Int): Double {
            if (probabilityPerDay == 0.0) return 0.0
            if (probabilityPerDay == 1.0) return 1.0
            return 1.0 - (1.0 - probabilityPerDay).pow(hours / 24.0)
        }
    }
}

/** Converts active event level shocks and decaying liquidity effects to one price step. */
object EventShockCalculator {
    fun levelShockAt(event: GameEvent, time: Instant): Double {
        if (!event.isActiveAt(time)) return 0.0
        val elapsedHours = (time - event.startsAt).inWholeNanoseconds / NANOS_PER_HOUR
        return event.impact.shockReturn * decayWeight(event, elapsedHours)
    }

    fun returnBetween(event: GameEvent, from: Instant, to: Instant): Double {
        require(to >= from) { "Event return interval cannot run backwards" }
        if (to == from || to <= event.startsAt || from >= event.endsAt) return 0.0

        // The left boundary represents the state immediately before events at
        // that exact instant, so a newly starting event contributes its shock.
        val fromLevel = if (from <= event.startsAt) 0.0 else levelShockAt(event, from)
        val toLevel = if (to >= event.endsAt) 0.0 else levelShockAt(event, to)
        val levelReturn = (1.0 + toLevel) / (1.0 + fromLevel) - 1.0

        val overlapStart = maxOf(from, event.startsAt)
        val overlapEnd = minOf(to, event.endsAt)
        val overlapHours = if (overlapEnd <= overlapStart) 0.0 else {
            (overlapEnd - overlapStart).inWholeNanoseconds / NANOS_PER_HOUR
        }
        val driftReturn = exp(event.impact.hourlyDrift * overlapHours) - 1.0
        return ((1.0 + levelReturn) * (1.0 + driftReturn) - 1.0).coerceAtLeast(-0.95)
    }

    fun aggregate(
        events: Iterable<GameEvent>,
        stock: StockDefinition,
        from: Instant,
        to: Instant,
    ): PriceImpulse {
        require(to >= from)
        var returnFactor = 1.0
        var directProductReturnFactor = 1.0
        var volatilityMultiplier = 1.0
        var volumeMultiplier = 1.0
        val sampleTime = if (to > from) to else from

        for (event in events) {
            if (!event.affects(stock)) continue
            val eventReturn = returnBetween(event, from, to)
            returnFactor *= 1.0 + eventReturn
            if (event.scope == com.amond.kmpbook.domain.model.EventScope.STOCK && stock.id in event.affectedStockIds) {
                directProductReturnFactor *= 1.0 + eventReturn
            }
            val weight = effectWeightAt(event, sampleTime)
            volatilityMultiplier *= 1.0 + (event.impact.volatilityMultiplier - 1.0) * weight
            volumeMultiplier *= 1.0 + (event.impact.volumeMultiplier - 1.0) * weight
        }
        return PriceImpulse(
            returnRate = (returnFactor - 1.0).coerceIn(-0.90, 1.50),
            directProductReturnRate = (directProductReturnFactor - 1.0).coerceIn(-0.90, 1.50),
            volatilityMultiplier = volatilityMultiplier.coerceIn(0.0, 20.0),
            volumeMultiplier = volumeMultiplier.coerceIn(0.0, 100.0),
        )
    }

    fun liquidityMultiplierAt(
        events: Iterable<GameEvent>,
        stock: StockDefinition,
        time: Instant,
    ): Double {
        var multiplier = 1.0
        for (event in events) {
            if (!event.affects(stock)) continue
            val weight = effectWeightAt(event, time)
            multiplier *= 1.0 + (event.impact.liquidityMultiplier - 1.0) * weight
        }
        return multiplier.coerceIn(0.05, 20.0)
    }

    private fun effectWeightAt(event: GameEvent, time: Instant): Double {
        if (!event.isActiveAt(time)) return 0.0
        val elapsedHours = (time - event.startsAt).inWholeNanoseconds / NANOS_PER_HOUR
        return decayWeight(event, elapsedHours)
    }

    private fun decayWeight(event: GameEvent, elapsedHours: Double): Double {
        val halfLife = max(1.0, event.durationHours / 3.0)
        return exp(-LN_2 * elapsedHours.coerceAtLeast(0.0) / halfLife)
    }

    private const val NANOS_PER_HOUR: Double = 3_600_000_000_000.0
    private val LN_2: Double = ln(2.0)
}
