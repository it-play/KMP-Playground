package com.amond.kmpbook.domain.simulation

import com.amond.kmpbook.domain.model.EventScope
import com.amond.kmpbook.domain.model.EventSeverity
import com.amond.kmpbook.domain.model.EventType
import com.amond.kmpbook.domain.model.CausalMarketRegimeSnapshot
import com.amond.kmpbook.domain.model.CausalSignalSeed
import com.amond.kmpbook.domain.model.EventImpactInsight
import com.amond.kmpbook.domain.model.EventImpactCoveragePolicy
import com.amond.kmpbook.domain.model.EventRecordKind
import com.amond.kmpbook.domain.model.EventTradingHaltDirective
import com.amond.kmpbook.domain.model.GameEvent
import com.amond.kmpbook.domain.model.GameEventImpact
import com.amond.kmpbook.domain.model.ImpactDirection
import com.amond.kmpbook.domain.model.InstrumentTerminationKind
import com.amond.kmpbook.domain.model.InstrumentTerminationTerms
import com.amond.kmpbook.domain.model.InstrumentTerminationValuationMethod
import com.amond.kmpbook.domain.model.InstrumentStrategy
import com.amond.kmpbook.domain.model.InstrumentType
import com.amond.kmpbook.domain.model.ListingFinalDispositionType
import com.amond.kmpbook.domain.model.ListingRecoveryCondition
import com.amond.kmpbook.domain.model.ListingRiskTag
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.MIN_CAUSAL_SIGNAL_STRENGTH
import com.amond.kmpbook.domain.model.Sector
import com.amond.kmpbook.domain.model.StockDefinition
import com.amond.kmpbook.domain.model.isDirectProductImpactFor
import com.amond.kmpbook.domain.model.resolvedImpactFor
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

private fun MacroEnvironment.toCausalMarketRegimeSnapshot(): CausalMarketRegimeSnapshot =
    CausalMarketRegimeSnapshot(
        riskSentiment = riskSentiment,
        volatilityRegime = volatilityRegime,
        usdKrwChangeRate = (usdKrw / previousUsdKrw - 1.0).coerceIn(-0.25, 0.25),
        marketHourlyReturns = marketHourlyReturns.toMap(),
        marketChangeFromPreviousClose = marketChangeFromPreviousClose.toMap(),
    )

/**
 * Rule-driven, seeded event generator. Active and cooldown state is isolated by
 * the selected target, while one-shot templates retain campaign-wide state.
 */
class EventEngine(
    seed: Long,
    val templates: List<EventTemplate> = DefaultEventTemplates.all,
) {
    private val random = DeterministicRandom(seed)
    private var sequence: Long = 0L
    private val lastTriggeredAt = mutableMapOf<String, Long>()
    private val generatedActiveEvents = mutableListOf<GameEvent>()
    private val templatesById = templates.associateBy(EventTemplate::id)

    init {
        require(templates.map(EventTemplate::id).distinct().size == templates.size) {
            "Event template ids must be unique"
        }
    }

    fun generate(context: EventGenerationContext): EventGenerationResult {
        advanceTo(context.timestamp)
        if (context.maxNewEvents == 0 || context.stocks.isEmpty()) return result(emptyList())

        val externallyActive = context.existingEvents.filter { it.isActiveAt(context.timestamp) }
        val activeTriggerKeys = (generatedActiveEvents + externallyActive)
            .mapNotNullTo(mutableSetOf(), ::triggerKeyFromEvent)
        val newEvents = mutableListOf<GameEvent>()

        var cursor = context.timestamp
        var remainingHours = context.elapsedHours.toDouble()
        while (newEvents.size < context.maxNewEvents && remainingHours > 0.0) {
            val candidates = templates.mapNotNull { template ->
                if (template.id in context.suppressedTemplateIds) return@mapNotNull null
                if (!conditionMatches(template.condition, context.macro)) return@mapNotNull null
                if (template.oneShot && oneShotTriggerKey(template) in lastTriggeredAt) {
                    return@mapNotNull null
                }
                val availableTargets = eligibleTargets(template, context.stocks, context.macro)
                    .filter { target ->
                        val key = triggerKey(template, target)
                        key !in activeTriggerKeys && !isCoolingDown(template, key, cursor)
                    }
                if (availableTargets.isEmpty()) return@mapNotNull null
                val hourlyHazard = hourlyHazard(template, context)
                if (hourlyHazard <= 0.0) null else EventHazardCandidate(
                    template = template,
                    targets = availableTargets,
                    hourlyHazard = hourlyHazard,
                )
            }
            val totalHazard = candidates.sumOf(EventHazardCandidate::hourlyHazard)
            if (totalHazard <= 0.0) break

            // Competing-risk clock: every template participates in one aggregate point process.
            // This removes declaration-order bias while still allowing related events to cluster
            // through the bounded Hawkes intensity supplied by MarketDynamicsEngine.
            val waitingHours = -ln(random.nextDouble().coerceAtLeast(MIN_HAZARD_UNIFORM)) / totalHazard
            if (waitingHours >= remainingHours) break
            cursor += waitingHours.hours
            remainingHours -= waitingHours

            val selected = chooseHazardCandidate(candidates, totalHazard)
            val target = random.choose(selected.targets)
            val key = triggerKey(selected.template, target)
            // Keep the point-process occurrence time. EventShockCalculator can then integrate
            // only the remaining fraction of this hour instead of backdating every shock to the
            // turn boundary (which used to make large news reach circuit thresholds too early).
            val event = instantiate(selected.template, target, cursor, context.macro)
            generatedActiveEvents += event
            newEvents += event
            activeTriggerKeys += key
            lastTriggeredAt[key] = cursor.epochSeconds
        }
        return result(newEvents)
    }

    private fun hourlyHazard(template: EventTemplate, context: EventGenerationContext): Double {
        if (template.probabilityPerDay <= 0.0) return 0.0
        val boundedDailyProbability = template.probabilityPerDay.coerceAtMost(MAX_HAZARD_PROBABILITY)
        val baseline = -ln(1.0 - boundedDailyProbability) / HOURS_PER_DAY
        val forces = context.externalForces
        val typeMultiplier = when (template.type) {
            EventType.GEOPOLITICAL -> exp(1.75 * (forces.worldTension - 0.5))
            EventType.MARKET_SENTIMENT -> exp(0.95 * (forces.chaos - 0.5))
            EventType.CURRENCY,
            EventType.COMMODITY,
            -> exp(0.55 * (forces.chaos - 0.5) + 0.65 * (forces.worldTension - 0.5))
            EventType.FUND_OPERATION -> 0.78 +
                0.22 * (forces.retailBuyingPower + forces.institutionalBuyingPower)
            EventType.ECONOMIC_INDICATOR,
            EventType.CENTRAL_BANK,
            -> exp(0.42 * (forces.chaos - 0.5))
            EventType.INDUSTRY_SUPPLY_DEMAND,
            EventType.EARNINGS,
            EventType.CORPORATE_ACTION,
            EventType.PRODUCT_TECHNOLOGY,
            -> 0.88 + forces.economicMomentum * 0.24
            EventType.REGULATION_POLICY,
            EventType.NATURAL_DISASTER,
            EventType.HEALTH_CRISIS,
            -> exp(0.32 * (forces.chaos - 0.5))
        }
        return (baseline * context.newsHazardMultiplier * typeMultiplier)
            .coerceIn(0.0, MAX_TEMPLATE_HOURLY_HAZARD)
    }

    private fun chooseHazardCandidate(
        candidates: List<EventHazardCandidate>,
        totalHazard: Double,
    ): EventHazardCandidate {
        var threshold = random.nextDouble() * totalHazard
        for (candidate in candidates) {
            threshold -= candidate.hourlyHazard
            if (threshold <= 0.0) return candidate
        }
        return candidates.last()
    }

    /**
     * Advances the engine-owned effect window to [timestamp] and returns the canonical active set.
     * Runtime and persistence call this at the same turn boundary so an event cannot be ended in
     * the news UI while remaining active in the generator snapshot until the next turn.
     */
    fun advanceTo(timestamp: Instant): List<GameEvent> {
        generatedActiveEvents.removeAll { event -> !event.isActiveAt(timestamp) }
        return generatedActiveEvents.toList()
    }

    fun snapshot(): EventEngineSnapshot = EventEngineSnapshot(
        randomState = random.snapshot(),
        sequence = sequence,
        lastTriggeredEpochSeconds = lastTriggeredAt.toMap(),
        activeEvents = generatedActiveEvents.toList(),
    )

    /**
     * Returns every cooldown/active-deduplication key that the current template set can produce
     * from [stocks]. Transient macro conditions are deliberately excluded: they decide when a
     * structurally valid target can fire, not whether that target belongs to the current schema.
     */
    internal fun possibleTriggerKeys(stocks: List<StockDefinition>): Set<String> {
        requireUniqueStockIds(stocks)
        return templates.flatMapTo(linkedSetOf()) { template ->
            structurallyEligibleTargets(template, stocks).map { target -> triggerKey(template, target) }
        }
    }

    /** 현재 템플릿이 만든 이벤트의 불투명 dedup/cooldown key. ID 문자열은 해석하지 않는다. */
    internal fun triggerKeyFor(event: GameEvent): String? = triggerKeyFromEvent(event)

    /**
     * [event]가 현재 템플릿과 [stocks] 카탈로그에서 생성될 수 있는 정확한 payload인지
     * 확인한다. 표시용 이벤트 ID를 해석하지 않고 [GameEvent.generatorTemplateId]만 사용한다.
     * `null`이면 유효하고, 문자열이면 저장 복원을 거부해야 할 첫 불일치다.
     */
    internal fun generatedEventInvariantViolation(
        event: GameEvent,
        stocks: List<StockDefinition>,
    ): String? {
        if (stocks.map(StockDefinition::id).distinct().size != stocks.size) {
            return "Event candidates must have unique stock ids"
        }
        if (event.id.isBlank()) return "Generated event id must not be blank"

        val templateId = event.generatorTemplateId
            ?: return "Generated event '${event.id}' does not reference a generator template"
        val template = templatesById[templateId]
            ?: return "Generated event '${event.id}' references unknown template '$templateId'"
        val matchingTargets = structurallyEligibleTargets(template, stocks)
            .filter { target -> target.matches(event) }
        if (matchingTargets.size != 1) {
            return "Generated event '${event.id}' target is not an exact eligible target for template '$templateId'"
        }

        val samples = GeneratedEventSamples(
            durationHours = event.durationHours,
            shockReturn = event.impact.shockReturn,
            hourlyDrift = event.impact.hourlyDrift,
            volatilityMultiplier = event.impact.volatilityMultiplier,
            volumeMultiplier = event.impact.volumeMultiplier,
            liquidityMultiplier = event.impact.liquidityMultiplier,
            sentiment = event.impact.sentiment,
            accelerationRecoveryRate = event.instrumentTermination?.accelerationRecoveryRate,
        )
        template.sampleInvariantViolation(samples)?.let { violation ->
            return "Generated event '${event.id}' $violation"
        }

        val expected = materializeGeneratedEvent(
            id = event.id,
            template = template,
            target = matchingTargets.single(),
            timestamp = event.startsAt,
            samples = samples,
            marketRegimeSnapshot = event.marketRegimeSnapshot,
        )
        return generatedPayloadMismatch(expected = expected, actual = event)?.let { field ->
            "Generated event '${event.id}' $field does not match template '$templateId'"
        }
    }

    fun restore(snapshot: EventEngineSnapshot, stocks: List<StockDefinition>) {
        require(snapshot.sequence >= 0L)
        requireUniqueStockIds(stocks)

        val possibleTriggerKeys = possibleTriggerKeys(stocks)
        require(snapshot.lastTriggeredEpochSeconds.keys.all(possibleTriggerKeys::contains)) {
            "Event-engine cooldown state contains a trigger key unavailable to the current templates and stocks"
        }

        val activeTriggerKeys = linkedSetOf<String>()
        snapshot.activeEvents.forEach { event ->
            val violation = generatedEventInvariantViolation(event, stocks)
            require(violation == null) { requireNotNull(violation) }
            val templateId = requireNotNull(event.generatorTemplateId)
            val template = templatesById.getValue(templateId)
            require(activeTriggerKeys.add(triggerKey(template, event))) {
                "Event-engine active events contain a duplicate trigger target for template '$templateId'"
            }
        }
        require(activeTriggerKeys.all(snapshot.lastTriggeredEpochSeconds::containsKey)) {
            "Every event-engine active target must have a matching cooldown record"
        }
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
        macro: MacroEnvironment,
    ): GameEvent {
        val duration = template.durationHours.first +
            random.nextInt(template.durationHours.last - template.durationHours.first + 1)
        val id = "${template.id}:${timestamp.epochSeconds}:${sequence++}"
        val samples = GeneratedEventSamples(
            durationHours = duration,
            shockReturn = randomIn(template.shockReturn),
            hourlyDrift = randomIn(template.hourlyDrift),
            volatilityMultiplier = randomIn(template.volatilityMultiplier),
            volumeMultiplier = randomIn(template.volumeMultiplier),
            liquidityMultiplier = randomIn(template.liquidityMultiplier),
            sentiment = randomIn(template.sentiment),
            accelerationRecoveryRate = template.terminationTemplate
                ?.accelerationRecoveryRate
                ?.let(::randomIn),
        )
        check(template.sampleInvariantViolation(samples) == null)
        return materializeGeneratedEvent(
            id = id,
            template = template,
            target = target,
            timestamp = timestamp,
            samples = samples,
            marketRegimeSnapshot = macro.toCausalMarketRegimeSnapshot(),
        )
    }

    /** Template-controlled fields are assembled in one place for both generation and validation. */
    private fun materializeGeneratedEvent(
        id: String,
        template: EventTemplate,
        target: SelectedEventTarget,
        timestamp: Instant,
        samples: GeneratedEventSamples,
        marketRegimeSnapshot: CausalMarketRegimeSnapshot,
    ): GameEvent = GameEvent(
        id = id,
        generatorTemplateId = template.id,
        title = interpolate(template.titleTemplate, target),
        description = interpolate(template.descriptionTemplate, target),
        scope = template.scope,
        type = template.type,
        severity = template.severity,
        impact = GameEventImpact(
            direction = template.direction,
            shockReturn = samples.shockReturn,
            hourlyDrift = samples.hourlyDrift,
            volatilityMultiplier = samples.volatilityMultiplier,
            volumeMultiplier = samples.volumeMultiplier,
            liquidityMultiplier = samples.liquidityMultiplier,
            sentiment = samples.sentiment,
        ),
        startsAt = timestamp,
        durationHours = samples.durationHours,
        recordKind = template.recordKind,
        impactCoveragePolicy = template.impactCoveragePolicy,
        effectStartsAt = timestamp,
        effectDurationHours = samples.durationHours,
        affectedMarkets = target.markets,
        affectedSectors = target.sectors,
        affectedStockIds = target.stockIds,
        sourceLabel = template.sourceLabel,
        impactInsights = materializeImpactInsights(template, target),
        causalSignals = template.causalSignals,
        marketRegimeSnapshot = marketRegimeSnapshot,
        reportedFacts = emptyList(),
        marketAction = null,
        instrumentTermination = materializeInstrumentTermination(template, timestamp, samples),
        tradingHaltDirective = template.tradingHaltDirective,
        listingRiskTags = template.listingRiskTags,
        listingRecoveryConditions = template.listingRecoveryConditions,
        listingFinalDispositionHint = template.listingFinalDispositionHint,
    )

    private fun materializeImpactInsights(
        template: EventTemplate,
        target: SelectedEventTarget,
    ): List<EventImpactInsight> = template.impactInsights.map { insight ->
        val interpolated = insight.copy(
            targetLabel = interpolate(insight.targetLabel, target),
            rationale = interpolate(insight.rationale, target),
            stockId = insight.stockId?.let { interpolate(it, target) },
        )
        val shouldBindSelectedMarket = interpolated.markets.isEmpty() && interpolated.sector != null &&
            template.scope in setOf(EventScope.COUNTRY, EventScope.MARKET)
        if (shouldBindSelectedMarket) interpolated.copy(markets = target.markets) else interpolated
    }

    private fun materializeInstrumentTermination(
        template: EventTemplate,
        timestamp: Instant,
        samples: GeneratedEventSamples,
    ): InstrumentTerminationTerms? = template.terminationTemplate?.let { termination ->
        InstrumentTerminationTerms(
            kind = termination.kind,
            effectiveNotBefore = timestamp + samples.durationHours.hours,
            valuationMethod = termination.valuationMethod,
            accelerationRecoveryRate = samples.accelerationRecoveryRate,
        )
    }

    private fun EventTemplate.sampleInvariantViolation(samples: GeneratedEventSamples): String? {
        if (samples.durationHours !in durationHours) {
            return "duration ${samples.durationHours}h is outside template range $durationHours"
        }
        sampledDoubleViolation("shock return", samples.shockReturn, shockReturn)?.let { return it }
        sampledDoubleViolation("hourly drift", samples.hourlyDrift, hourlyDrift)?.let { return it }
        sampledDoubleViolation(
            "volatility multiplier",
            samples.volatilityMultiplier,
            volatilityMultiplier,
        )?.let { return it }
        sampledDoubleViolation("volume multiplier", samples.volumeMultiplier, volumeMultiplier)?.let { return it }
        sampledDoubleViolation(
            "liquidity multiplier",
            samples.liquidityMultiplier,
            liquidityMultiplier,
        )?.let { return it }
        sampledDoubleViolation("sentiment", samples.sentiment, sentiment)?.let { return it }

        val recoveryRange = terminationTemplate?.accelerationRecoveryRate
        val recoveryRate = samples.accelerationRecoveryRate
        return when {
            recoveryRange == null && recoveryRate != null ->
                "contains an acceleration recovery rate not declared by its template"
            recoveryRange != null && recoveryRate == null ->
                "is missing the acceleration recovery rate declared by its template"
            recoveryRange != null && (!requireNotNull(recoveryRate).isFinite() || recoveryRate !in recoveryRange) ->
                "acceleration recovery rate $recoveryRate is outside template range $recoveryRange"
            else -> null
        }
    }

    private fun sampledDoubleViolation(
        label: String,
        value: Double,
        range: ClosedFloatingPointRange<Double>,
    ): String? = if (!value.isFinite() || value !in range) {
        "$label $value is outside template range $range"
    } else {
        null
    }

    private fun generatedPayloadMismatch(expected: GameEvent, actual: GameEvent): String? = when {
        actual.generatorTemplateId != expected.generatorTemplateId -> "generator template"
        actual.title != expected.title -> "interpolated title"
        actual.description != expected.description -> "interpolated description"
        actual.scope != expected.scope -> "scope"
        actual.type != expected.type -> "type"
        actual.severity != expected.severity -> "severity"
        actual.impact != expected.impact -> "impact"
        actual.durationHours != expected.durationHours -> "duration"
        actual.recordKind != expected.recordKind -> "record kind"
        actual.impactCoveragePolicy != expected.impactCoveragePolicy -> "impact coverage policy"
        actual.effectStartsAt != expected.effectStartsAt ||
            actual.effectDurationHours != expected.effectDurationHours -> "effect window"
        actual.affectedMarkets != expected.affectedMarkets ||
            actual.affectedSectors != expected.affectedSectors ||
            actual.affectedStockIds != expected.affectedStockIds -> "target"
        actual.sourceLabel != expected.sourceLabel -> "source"
        actual.impactInsights != expected.impactInsights -> "interpolated impact insights"
        actual.causalSignals != expected.causalSignals -> "causal signals"
        actual.reportedFacts != expected.reportedFacts -> "reported facts"
        actual.marketAction != expected.marketAction -> "market action reference"
        actual.instrumentTermination != expected.instrumentTermination -> "instrument termination terms"
        actual.tradingHaltDirective != expected.tradingHaltDirective -> "trading-halt directive"
        actual.listingRiskTags != expected.listingRiskTags -> "listing risk tags"
        actual.listingRecoveryConditions != expected.listingRecoveryConditions -> "listing recovery conditions"
        actual.listingFinalDispositionHint != expected.listingFinalDispositionHint -> "listing disposition"
        actual != expected -> "payload"
        else -> null
    }

    private fun eligibleTargets(
        template: EventTemplate,
        stocks: List<StockDefinition>,
        macro: MacroEnvironment,
    ): List<SelectedEventTarget> = structurallyEligibleTargets(template, stocks).filter { target ->
        template.scope != EventScope.MARKET ||
            target.markets.singleOrNull()?.let { market ->
                marketMatchesCondition(template.condition, market, macro)
            } == true
    }

    private fun structurallyEligibleTargets(
        template: EventTemplate,
        stocks: List<StockDefinition>,
    ): List<SelectedEventTarget> {
        val candidates = stocks.filter { stock ->
            (template.eligibleMarkets.isEmpty() || stock.market in template.eligibleMarkets) &&
                (template.eligibleSectors.isEmpty() || stock.sector in template.eligibleSectors) &&
                (template.eligibleInstrumentTypes.isEmpty() || stock.instrumentType in template.eligibleInstrumentTypes) &&
                (template.eligibleStrategies.isEmpty() || stock.behavior.strategy in template.eligibleStrategies) &&
                (template.terminationTemplate?.isEligibleFor(stock) != false) &&
                (template.tradingHaltDirective?.eligibleMarkets?.contains(stock.market) != false)
        }
        if (candidates.isEmpty() && template.scope != EventScope.GLOBAL) return emptyList()

        return when (template.scope) {
            EventScope.GLOBAL -> listOf(SelectedEventTarget())
            EventScope.COUNTRY -> candidates
                .groupBy { it.market.isKorean }
                .keys
                .sortedDescending()
                .map { korean ->
                    val countryMarkets = Market.entries.filterTo(mutableSetOf()) {
                        it.isKorean == korean && (template.eligibleMarkets.isEmpty() || it in template.eligibleMarkets)
                    }
                    SelectedEventTarget(
                        markets = countryMarkets,
                        marketName = if (korean) "한국" else "미국",
                    )
                }

            EventScope.MARKET -> candidates
                .map(StockDefinition::market)
                .distinct()
                .sortedBy(Market::name)
                .map { market -> SelectedEventTarget(markets = setOf(market), marketName = market.displayName) }

            EventScope.SECTOR -> candidates
                .map(StockDefinition::sector)
                .distinct()
                .sortedBy(Sector::name)
                .map { sector -> SelectedEventTarget(sectors = setOf(sector), sectorName = sector.displayName) }

            EventScope.STOCK -> candidates
                .sortedBy(StockDefinition::id)
                .map { stock ->
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

    private fun requireUniqueStockIds(stocks: List<StockDefinition>) {
        require(stocks.map(StockDefinition::id).distinct().size == stocks.size) {
            "Event candidates must have unique stock ids"
        }
    }

    private fun isCoolingDown(template: EventTemplate, triggerKey: String, timestamp: Instant): Boolean {
        val previousEpoch = lastTriggeredAt[triggerKey] ?: return false
        val elapsedSeconds = timestamp.epochSeconds - previousEpoch
        return elapsedSeconds < template.cooldownHours.toLong() * SECONDS_PER_HOUR
    }

    private fun marketMatchesCondition(
        condition: EventCondition,
        market: Market,
        macro: MacroEnvironment,
    ): Boolean = when (condition) {
        EventCondition.MARKET_DRAWDOWN -> macro.marketChangeFromPreviousClose[market]?.let { it <= -0.05 } == true
        EventCondition.MARKET_RALLY -> macro.marketChangeFromPreviousClose[market]?.let { it >= 0.04 } == true
        else -> true
    }

    private fun conditionMatches(condition: EventCondition, macro: MacroEnvironment): Boolean = when (condition) {
        EventCondition.ALWAYS -> true
        EventCondition.POLICY_RATE_HIGH -> macro.policyRate >= 0.045
        EventCondition.POLICY_RATE_LOW -> macro.policyRate <= 0.015
        EventCondition.POLICY_RATE_RISING -> macro.policyRateChange > 0.0
        EventCondition.POLICY_RATE_FALLING -> macro.policyRateChange < 0.0
        EventCondition.INFLATION_HIGH -> macro.inflationRate >= 0.035 || macro.inflationSurprise >= 0.75
        EventCondition.INFLATION_COOLING -> macro.inflationRate <= 0.022 && macro.inflationSurprise <= 0.0
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

    private fun triggerKeyFromEvent(event: GameEvent): String? {
        val templateId = event.generatorTemplateId ?: return null
        val template = templatesById[templateId] ?: return null
        return triggerKey(template, event)
    }

    private fun triggerKey(template: EventTemplate, target: SelectedEventTarget): String =
        if (template.oneShot) {
            oneShotTriggerKey(template)
        } else {
            targetTriggerKey(
                template = template,
                markets = target.markets,
                sectors = target.sectors,
                stockIds = target.stockIds,
            )
        }

    private fun triggerKey(template: EventTemplate, event: GameEvent): String =
        if (template.oneShot) {
            oneShotTriggerKey(template)
        } else {
            targetTriggerKey(
                template = template,
                markets = event.affectedMarkets,
                sectors = event.affectedSectors,
                stockIds = event.affectedStockIds,
            )
        }

    private fun targetTriggerKey(
        template: EventTemplate,
        markets: Set<Market>,
        sectors: Set<Sector>,
        stockIds: Set<String>,
    ): String {
        val targetIdentity = when (template.scope) {
            EventScope.GLOBAL -> "global"
            EventScope.COUNTRY -> "country:${markets.sortedBy(Market::name).joinToString(",") { it.name }}"
            EventScope.MARKET -> "market:${markets.sortedBy(Market::name).joinToString(",") { it.name }}"
            EventScope.SECTOR -> "sector:${sectors.sortedBy(Sector::name).joinToString(",") { it.name }}"
            EventScope.STOCK -> "stock:${stockIds.sorted().joinToString(",")}"
        }
        return "${template.id}::$targetIdentity"
    }

    private fun oneShotTriggerKey(template: EventTemplate): String = "${template.id}::one-shot"

    private data class GeneratedEventSamples(
        val durationHours: Int,
        val shockReturn: Double,
        val hourlyDrift: Double,
        val volatilityMultiplier: Double,
        val volumeMultiplier: Double,
        val liquidityMultiplier: Double,
        val sentiment: Double,
        val accelerationRecoveryRate: Double?,
    )

    private data class EventHazardCandidate(
        val template: EventTemplate,
        val targets: List<SelectedEventTarget>,
        val hourlyHazard: Double,
    )

    private data class SelectedEventTarget(
        val markets: Set<Market> = emptySet(),
        val sectors: Set<Sector> = emptySet(),
        val stockIds: Set<String> = emptySet(),
        val companyName: String = "해당 기업",
        val symbol: String = "",
        val marketName: String = "해당 시장",
        val sectorName: String = "해당 산업",
    ) {
        fun matches(event: GameEvent): Boolean =
            markets == event.affectedMarkets &&
                sectors == event.affectedSectors &&
                stockIds == event.affectedStockIds
    }

    companion object {
        private const val SECONDS_PER_HOUR: Long = 3_600L
        private const val HOURS_PER_DAY: Double = 24.0
        private const val MIN_HAZARD_UNIFORM: Double = 1e-12
        private const val MAX_HAZARD_PROBABILITY: Double = 1.0 - 1e-12
        private const val MAX_TEMPLATE_HOURLY_HAZARD: Double = 0.35

    }
}

/** Converts active event level shocks and decaying liquidity effects to one price step. */
object EventShockCalculator {
    fun levelShockAt(event: GameEvent, time: Instant): Double =
        levelShockAt(event, event.impact, time)

    private fun levelShockAt(event: GameEvent, impact: GameEventImpact, time: Instant): Double {
        if (!event.isActiveAt(time)) return 0.0
        val elapsedHours = (time - event.startsAt).inWholeNanoseconds / NANOS_PER_HOUR
        return impact.shockReturn * decayWeight(event, elapsedHours)
    }

    fun returnBetween(event: GameEvent, from: Instant, to: Instant): Double =
        returnBetween(event, event.impact, from, to)

    private fun returnBetween(
        event: GameEvent,
        impact: GameEventImpact,
        from: Instant,
        to: Instant,
    ): Double {
        require(to >= from) { "Event return interval cannot run backwards" }
        if (to == from || to <= event.startsAt || from >= event.endsAt) return 0.0

        // The left boundary represents the state immediately before events at
        // that exact instant, so a newly starting event contributes its shock.
        val fromLevel = if (from <= event.startsAt) 0.0 else levelShockAt(event, impact, from)
        val toLevel = if (to >= event.endsAt) 0.0 else levelShockAt(event, impact, to)
        val levelReturn = (1.0 + toLevel) / (1.0 + fromLevel) - 1.0

        val overlapStart = maxOf(from, event.startsAt)
        val overlapEnd = minOf(to, event.endsAt)
        val overlapHours = if (overlapEnd <= overlapStart) 0.0 else {
            (overlapEnd - overlapStart).inWholeNanoseconds / NANOS_PER_HOUR
        }
        val driftReturn = exp(impact.hourlyDrift * overlapHours) - 1.0
        return ((1.0 + levelReturn) * (1.0 + driftReturn) - 1.0).coerceAtLeast(-0.95)
    }

    fun aggregate(
        events: Iterable<GameEvent>,
        stock: StockDefinition,
        from: Instant,
        to: Instant,
    ): PriceImpulse {
        require(to >= from)
        var returnLogSum = 0.0
        var directProductReturnLogSum = 0.0
        var volatilityLogSum = 0.0
        var volumeLogSum = 0.0

        for (event in events) {
            if (!event.affects(stock)) continue
            val stockSpecificImpact = event.impactFor(stock)
            val eventReturn = returnBetween(event, stockSpecificImpact, from, to)
            returnLogSum += ln(1.0 + eventReturn)
            if (event.isDirectProductImpactFor(stock)) {
                directProductReturnLogSum += ln(1.0 + eventReturn)
            }
            val weight = effectWeightBetween(event, from, to)
            volatilityLogSum += ln(
                1.0 + (stockSpecificImpact.volatilityMultiplier - 1.0) * weight,
            )
            volumeLogSum += ln(1.0 + (stockSpecificImpact.volumeMultiplier - 1.0) * weight)
        }
        val boundedReturnLog = softBoundLogReturn(returnLogSum)
        val boundedDirectProductLog = softBoundLogReturn(directProductReturnLogSum)
        return PriceImpulse(
            returnRate = exp(boundedReturnLog) - 1.0,
            directProductReturnRate = exp(boundedDirectProductLog) - 1.0,
            volatilityMultiplier = exp(
                softBoundSigned(
                    volatilityLogSum,
                    negativeLimit = -ln(MIN_EVENT_VOLATILITY_MULTIPLIER),
                    positiveLimit = ln(MAX_EVENT_VOLATILITY_MULTIPLIER),
                    knee = ln(EVENT_VOLATILITY_SOFT_KNEE),
                ),
            ),
            volumeMultiplier = exp(
                softBoundSigned(
                    volumeLogSum,
                    negativeLimit = -ln(MIN_EVENT_VOLUME_MULTIPLIER),
                    positiveLimit = ln(MAX_EVENT_VOLUME_MULTIPLIER),
                    knee = ln(EVENT_VOLUME_SOFT_KNEE),
                ),
            ),
        )
    }

    /** Instantaneous liquidity used by point-in-time order-book snapshots. */
    fun liquidityMultiplierAt(
        events: Iterable<GameEvent>,
        stock: StockDefinition,
        time: Instant,
    ): Double {
        var logSum = 0.0
        for (event in events) {
            if (!event.affects(stock)) continue
            val weight = effectWeightAt(event, time)
            val stockSpecificImpact = event.impactFor(stock)
            logSum += ln(1.0 + (stockSpecificImpact.liquidityMultiplier - 1.0) * weight)
        }
        return exp(softBoundLiquidityLog(logSum))
    }

    /** Time-weighted liquidity over a simulation interval. */
    fun liquidityMultiplierBetween(
        events: Iterable<GameEvent>,
        stock: StockDefinition,
        from: Instant,
        to: Instant,
    ): Double {
        require(to >= from) { "Event liquidity interval cannot run backwards" }
        var logSum = 0.0
        for (event in events) {
            if (!event.affects(stock)) continue
            val weight = effectWeightBetween(event, from, to)
            val stockSpecificImpact = event.impactFor(stock)
            logSum += ln(1.0 + (stockSpecificImpact.liquidityMultiplier - 1.0) * weight)
        }
        return exp(softBoundLiquidityLog(logSum))
    }

    /**
     * 관련 사건은 로그 공간에서 실제로 누적한다. 다만 시장의 유한한 가격발견·호가흡수
     * 능력을 넘는 구간만 매끄럽게 포화시켜, 임의의 hard clip 경계에서 흐름이 꺾이지 않는다.
     */
    private fun softBoundLogReturn(value: Double): Double = softBoundSigned(
        value = value,
        negativeLimit = -ln(MIN_EVENT_RETURN_FACTOR),
        positiveLimit = ln(MAX_EVENT_RETURN_FACTOR),
        knee = EVENT_RETURN_LOG_SOFT_KNEE,
    )

    private fun softBoundLiquidityLog(value: Double): Double = softBoundSigned(
        value = value,
        negativeLimit = -ln(MIN_EVENT_LIQUIDITY_MULTIPLIER),
        positiveLimit = ln(MAX_EVENT_LIQUIDITY_MULTIPLIER),
        knee = ln(EVENT_LIQUIDITY_SOFT_KNEE),
    )

    private fun softBoundSigned(
        value: Double,
        negativeLimit: Double,
        positiveLimit: Double,
        knee: Double,
    ): Double {
        val limit = if (value < 0.0) negativeLimit else positiveLimit
        val magnitude = kotlin.math.abs(value)
        if (magnitude <= knee) return value
        val remaining = (limit - knee).coerceAtLeast(1e-9)
        val boundedMagnitude = knee + remaining * kotlin.math.tanh((magnitude - knee) / remaining)
        return if (value < 0.0) -boundedMagnitude else boundedMagnitude
    }

    private fun effectWeightAt(event: GameEvent, time: Instant): Double {
        if (!event.isActiveAt(time)) return 0.0
        val elapsedHours = (time - event.startsAt).inWholeNanoseconds / NANOS_PER_HOUR
        return decayWeight(event, elapsedHours)
    }

    /**
     * Average decaying exposure over [from, to). Time outside the event contributes zero, so an
     * event covering only part of a turn is weighted by that exact overlap instead of an endpoint
     * sample. A zero-length interval remains a point-in-time query for deterministic callers.
     */
    private fun effectWeightBetween(event: GameEvent, from: Instant, to: Instant): Double {
        require(to >= from) { "Event effect interval cannot run backwards" }
        if (to == from) return effectWeightAt(event, from)

        val overlapStart = maxOf(from, event.startsAt)
        val overlapEnd = minOf(to, event.endsAt)
        if (overlapEnd <= overlapStart) return 0.0

        val intervalHours = (to - from).inWholeNanoseconds / NANOS_PER_HOUR
        val overlapStartHours = (overlapStart - event.startsAt).inWholeNanoseconds / NANOS_PER_HOUR
        val overlapEndHours = (overlapEnd - event.startsAt).inWholeNanoseconds / NANOS_PER_HOUR
        val halfLife = max(1.0, event.durationHours / 3.0)
        val decayRate = LN_2 / halfLife
        val integratedWeight = (
            exp(-decayRate * overlapStartHours) - exp(-decayRate * overlapEndHours)
            ) / decayRate
        return (integratedWeight / intervalHours).coerceIn(0.0, 1.0)
    }

    /** 가격 방향과 해외 전염 강도를 종목별 충격에 함께 반영한다. */
    private fun GameEvent.impactFor(stock: StockDefinition): GameEventImpact {
        val resolved = resolvedImpactFor(stock)
        fun directional(value: Double): Double = when (resolved.direction) {
            ImpactDirection.POSITIVE -> kotlin.math.abs(value) * resolved.relativeSensitivity
            ImpactDirection.NEGATIVE -> -kotlin.math.abs(value) * resolved.relativeSensitivity
            ImpactDirection.MIXED -> value * resolved.relativeSensitivity
            ImpactDirection.NEUTRAL -> 0.0
        }
        val transmissions = resolved.causalImpact?.marketTransmissionsByFactor.orEmpty().values
        val transmissionScale = transmissions.takeIf { it.isNotEmpty() }?.fold(1.0) { survival, trace ->
            survival * (1.0 - trace.responseIntensity / MarketContagionEngine.MAX_RESPONSE_INTENSITY)
        }?.let { survival ->
            MarketContagionEngine.MAX_RESPONSE_INTENSITY * (1.0 - survival)
        } ?: 1.0
        /** 로그 공간에서 포화해 큰 유한 입력도 Infinity/NaN으로 바뀌지 않게 한다. */
        fun transmittedMultiplier(value: Double, upperBound: Double): Double {
            if (value == 0.0) return 0.0
            val boundedLog = (ln(value) * transmissionScale).coerceAtMost(ln(upperBound))
            return exp(boundedLog).coerceIn(0.0, upperBound)
        }
        return impact.copy(
            shockReturn = directional(impact.shockReturn).coerceAtLeast(-0.95),
            hourlyDrift = directional(impact.hourlyDrift),
            volatilityMultiplier = transmittedMultiplier(impact.volatilityMultiplier, 20.0),
            volumeMultiplier = transmittedMultiplier(impact.volumeMultiplier, 100.0),
            liquidityMultiplier = transmittedMultiplier(impact.liquidityMultiplier, 20.0),
            sentiment = (impact.sentiment * transmissionScale).coerceIn(-1.0, 1.0),
        )
    }

    private fun decayWeight(event: GameEvent, elapsedHours: Double): Double {
        val halfLife = max(1.0, event.durationHours / 3.0)
        return exp(-LN_2 * elapsedHours.coerceAtLeast(0.0) / halfLife)
    }

    private const val NANOS_PER_HOUR: Double = 3_600_000_000_000.0
    private const val MIN_EVENT_RETURN_FACTOR: Double = 0.30
    private const val MAX_EVENT_RETURN_FACTOR: Double = 2.50
    private const val EVENT_RETURN_LOG_SOFT_KNEE: Double = 0.14
    private const val MIN_EVENT_VOLATILITY_MULTIPLIER: Double = 0.35
    private const val MAX_EVENT_VOLATILITY_MULTIPLIER: Double = 5.0
    private const val EVENT_VOLATILITY_SOFT_KNEE: Double = 1.8
    private const val MIN_EVENT_VOLUME_MULTIPLIER: Double = 0.30
    private const val MAX_EVENT_VOLUME_MULTIPLIER: Double = 12.0
    private const val EVENT_VOLUME_SOFT_KNEE: Double = 2.5
    private const val MIN_EVENT_LIQUIDITY_MULTIPLIER: Double = 0.20
    private const val MAX_EVENT_LIQUIDITY_MULTIPLIER: Double = 5.0
    private const val EVENT_LIQUIDITY_SOFT_KNEE: Double = 1.8
    private val LN_2: Double = ln(2.0)
}
