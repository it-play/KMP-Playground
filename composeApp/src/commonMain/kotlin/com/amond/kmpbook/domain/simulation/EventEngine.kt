package com.amond.kmpbook.domain.simulation

import com.amond.kmpbook.domain.model.EventScope
import com.amond.kmpbook.domain.model.EventSeverity
import com.amond.kmpbook.domain.model.EventType
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
import com.amond.kmpbook.domain.model.Sector
import com.amond.kmpbook.domain.model.StockDefinition
import com.amond.kmpbook.domain.model.isDirectProductImpactFor
import com.amond.kmpbook.domain.model.resolvedImpactFor
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.time.Duration.Companion.hours
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

/**
 * 확률 이벤트가 발생한 순간 실제 [InstrumentTerminationTerms]를 만드는 선언형 규칙이다.
 * 최소 효력 시각과 가속상환 회수율은 생성된 [GameEvent]에 복사되어 이후 다시 추첨하지 않는다.
 */
data class EventTerminationTemplate(
    val kind: InstrumentTerminationKind,
    val valuationMethod: InstrumentTerminationValuationMethod,
    val accelerationRecoveryRate: ClosedFloatingPointRange<Double>? = null,
) {
    init {
        require(kind != InstrumentTerminationKind.CONTRACTUAL_MATURITY) {
            "계약상 만기일은 상품 조건에서 정확한 날짜를 읽어 런타임 공시로 생성해야 합니다."
        }
        require(
            (kind == InstrumentTerminationKind.ISSUER_ACCELERATION) ==
                (accelerationRecoveryRate != null),
        ) { "발행사 가속상환 템플릿에만 회수율 범위가 필요합니다." }
        accelerationRecoveryRate?.let { range ->
            require(
                range.start.isFinite() && range.endInclusive.isFinite() &&
                    range.start in 0.40..0.80 && range.endInclusive in 0.40..0.80 &&
                    range.start <= range.endInclusive,
            ) { "가속상환 회수율 범위는 40% 이상 80% 이하여야 합니다." }
        }
        val representativeTerms = InstrumentTerminationTerms(
            kind = kind,
            effectiveNotBefore = Instant.fromEpochSeconds(0),
            valuationMethod = valuationMethod,
            accelerationRecoveryRate = accelerationRecoveryRate?.start,
        )
        require(representativeTerms.semanticInvariantViolation() == null)
    }

    /** ID가 아니라 종료 사유와 상품 계약 속성으로 확률 이벤트 후보를 제한한다. */
    fun isEligibleFor(stock: StockDefinition): Boolean = when (kind) {
        InstrumentTerminationKind.CONTRACTUAL_MATURITY -> false
        InstrumentTerminationKind.ISSUER_ACCELERATION -> stock.instrumentType == InstrumentType.ETN
        InstrumentTerminationKind.OPTIONAL_CALL ->
            stock.instrumentType == InstrumentType.ETN && stock.identityProfile?.callable == true
        InstrumentTerminationKind.FUND_LIQUIDATION ->
            stock.instrumentType in setOf(InstrumentType.ETF, InstrumentType.CLOSED_END_FUND)
    }
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
    /** 구조적 제도·유통 변경처럼 캠페인에서 한 번만 발생해야 하는 사건이다. */
    val oneShot: Boolean = false,
    /** ID 규칙이 아닌 템플릿 자체가 선언하는 기록 출처다. */
    val recordKind: EventRecordKind = EventRecordKind.NEWS,
    /** 같은 사건이 서로 다른 시장·산업·종목에 전달되는 구조화된 분석 경로다. */
    val impactInsights: List<EventImpactInsight> = emptyList(),
    /** 분석 경로에 없는 스코프 대상으로 기본 영향을 확장할지 결정한다. */
    val impactCoveragePolicy: EventImpactCoveragePolicy =
        EventImpactCoveragePolicy.SCOPE_FALLBACK_WITH_OVERRIDES,
    /** 가격 효과와 독립적으로 상장 생애주기 엔진에 전달되는 거래소 감시 신호. */
    val listingRiskTags: Set<ListingRiskTag> = emptySet(),
    val listingRecoveryConditions: Set<ListingRecoveryCondition> = emptySet(),
    val listingFinalDispositionHint: ListingFinalDispositionType? = null,
    /** 상품 종료 공시는 생성 시 실제 효력 시각·현금평가 조건으로 고정된다. */
    val terminationTemplate: EventTerminationTemplate? = null,
    /** 공시가 유발하는 거래정지 조건은 생성된 이벤트에 그대로 복사한다. */
    val tradingHaltDirective: EventTradingHaltDirective? = null,
    val sourceLabel: String = "시뮬레이션 뉴스",
) {
    init {
        require(id.isNotBlank())
        require(titleTemplate.isNotBlank() && descriptionTemplate.isNotBlank())
        require(probabilityPerDay in 0.0..1.0)
        require(cooldownHours >= 0)
        require(!durationHours.isEmpty() && durationHours.first > 0)
        require(shockReturn.isFiniteOrdered() && shockReturn.start > -1.0)
        require(hourlyDrift.isFiniteOrdered())
        require(volatilityMultiplier.isFiniteOrdered() && volatilityMultiplier.start >= 0.0)
        require(volumeMultiplier.isFiniteOrdered() && volumeMultiplier.start >= 0.0)
        require(liquidityMultiplier.isFiniteOrdered() && liquidityMultiplier.start >= 0.0)
        require(
            sentiment.isFiniteOrdered() &&
                sentiment.start >= -1.0 && sentiment.endInclusive <= 1.0,
        )
        require(recordKind != EventRecordKind.MARKET_ACTION) {
            "Market-action records require a runtime MarketActionReference and cannot be stochastic templates"
        }
        require(
            impactCoveragePolicy != EventImpactCoveragePolicy.EXPLICIT_PATHS_ONLY ||
                impactInsights.isNotEmpty(),
        ) { "Explicit-path event templates require at least one impact insight" }
        terminationTemplate?.let { terms ->
            require(scope == EventScope.STOCK && recordKind == EventRecordKind.INSTRUMENT_LIFECYCLE) {
                "Instrument termination templates must be stock lifecycle records"
            }
            require(listingFinalDispositionHint == null && terms.kind.terminationRiskTag !in listingRiskTags) {
                "Instrument termination templates derive disposition and risk tags from their terms"
            }
        }
        tradingHaltDirective?.let { directive ->
            require(scope == EventScope.STOCK) {
                "Event-driven trading halts must target a stock event"
            }
            require(directive.semanticInvariantViolation() == null) {
                "Event-driven trading halt directive is invalid"
            }
        }
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

        for (template in templates) {
            if (newEvents.size >= context.maxNewEvents) break
            if (!conditionMatches(template.condition, context.macro)) continue
            if (template.oneShot && oneShotTriggerKey(template) in lastTriggeredAt) continue

            val availableTargets = eligibleTargets(template, context.stocks, context.macro).filter { target ->
                val key = triggerKey(template, target)
                key !in activeTriggerKeys && !isCoolingDown(template, key, context.timestamp)
            }
            if (availableTargets.isEmpty()) continue

            // The template owns one hazard draw per generation. More listed instruments only
            // increase the target pool; they never multiply the aggregate occurrence rate.
            val probability = probabilityForInterval(template.probabilityPerDay, context.elapsedHours)
            if (!random.nextBoolean(probability)) continue

            val target = random.choose(availableTargets)
            val key = triggerKey(template, target)
            val event = instantiate(template, target, context.timestamp)
            generatedActiveEvents += event
            newEvents += event
            activeTriggerKeys += key
            lastTriggeredAt[key] = context.timestamp.epochSeconds
        }
        return result(newEvents)
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
        )
    }

    /** Template-controlled fields are assembled in one place for both generation and validation. */
    private fun materializeGeneratedEvent(
        id: String,
        template: EventTemplate,
        target: SelectedEventTarget,
        timestamp: Instant,
        samples: GeneratedEventSamples,
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

        private fun probabilityForInterval(probabilityPerDay: Double, hours: Int): Double {
            if (probabilityPerDay == 0.0) return 0.0
            if (probabilityPerDay == 1.0) return 1.0
            return 1.0 - (1.0 - probabilityPerDay).pow(hours / 24.0)
        }
    }
}

private fun ClosedFloatingPointRange<Double>.isFiniteOrdered(): Boolean =
    start.isFinite() && endInclusive.isFinite() && start <= endInclusive

private val InstrumentTerminationKind.terminationRiskTag: ListingRiskTag
    get() = when (this) {
        InstrumentTerminationKind.FUND_LIQUIDATION -> ListingRiskTag.ETF_LIQUIDATION_APPROVED
        else -> ListingRiskTag.ETN_MATURITY_OR_EARLY_REDEMPTION
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
        var returnFactor = 1.0
        var directProductReturnFactor = 1.0
        var volatilityMultiplier = 1.0
        var volumeMultiplier = 1.0

        for (event in events) {
            if (!event.affects(stock)) continue
            val stockSpecificImpact = event.impactFor(stock)
            val eventReturn = returnBetween(event, stockSpecificImpact, from, to)
            returnFactor *= 1.0 + eventReturn
            if (event.isDirectProductImpactFor(stock)) {
                directProductReturnFactor *= 1.0 + eventReturn
            }
            val weight = effectWeightBetween(event, from, to)
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

    /** Instantaneous liquidity used by point-in-time order-book snapshots. */
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

    /** Time-weighted liquidity over a simulation interval. */
    fun liquidityMultiplierBetween(
        events: Iterable<GameEvent>,
        stock: StockDefinition,
        from: Instant,
        to: Instant,
    ): Double {
        require(to >= from) { "Event liquidity interval cannot run backwards" }
        var multiplier = 1.0
        for (event in events) {
            if (!event.affects(stock)) continue
            val weight = effectWeightBetween(event, from, to)
            multiplier *= 1.0 + (event.impact.liquidityMultiplier - 1.0) * weight
        }
        return multiplier.coerceIn(0.05, 20.0)
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

    /**
     * 가격 방향은 가장 구체적인 분석 경로로 바꾸되, 변동성·거래량·유동성은 사건 전체의
     * 시장 충격을 유지한다. 인버스 배율은 PriceEngine이 기초자산 수익에 한 번만 적용한다.
     */
    private fun GameEvent.impactFor(stock: StockDefinition): GameEventImpact {
        val resolved = resolvedImpactFor(stock)
        fun directional(value: Double): Double = when (resolved.direction) {
            ImpactDirection.POSITIVE -> kotlin.math.abs(value) * resolved.relativeSensitivity
            ImpactDirection.NEGATIVE -> -kotlin.math.abs(value) * resolved.relativeSensitivity
            ImpactDirection.MIXED -> value * resolved.relativeSensitivity
            ImpactDirection.NEUTRAL -> 0.0
        }
        return impact.copy(
            shockReturn = directional(impact.shockReturn).coerceAtLeast(-0.95),
            hourlyDrift = directional(impact.hourlyDrift),
        )
    }

    private fun decayWeight(event: GameEvent, elapsedHours: Double): Double {
        val halfLife = max(1.0, event.durationHours / 3.0)
        return exp(-LN_2 * elapsedHours.coerceAtLeast(0.0) / halfLife)
    }

    private const val NANOS_PER_HOUR: Double = 3_600_000_000_000.0
    private val LN_2: Double = ln(2.0)
}
