package com.amond.kmpbook.domain.simulation

import com.amond.kmpbook.domain.model.EventScope
import com.amond.kmpbook.domain.model.EventSeverity
import com.amond.kmpbook.domain.model.EventType
import com.amond.kmpbook.domain.model.GameEvent
import com.amond.kmpbook.domain.model.GameEventImpact
import com.amond.kmpbook.domain.model.ImpactDirection
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.Sector
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventEngineTest {
    private val time = Instant.parse("2026-08-07T03:00:00Z")
    private val stocks = listOf(
        testStock(symbol = "KR", market = Market.KOSPI, sector = Sector.SEMICONDUCTOR),
        testStock(symbol = "US", market = Market.NASDAQ, sector = Sector.INFORMATION_TECHNOLOGY),
    )

    @Test
    fun defaultLibraryHasBroadUniqueRuleCoverage() {
        val templates = DefaultEventTemplates.all
        val ids = templates.map(EventTemplate::id)

        assertTrue(templates.size >= 30)
        assertEquals(ids.size, ids.distinct().size)
        assertTrue(listOf(
            "surprise_rate_hike",
            "inflation_hot",
            "krw_weakens",
            "growth_recession",
            "chip_shortage",
            "earnings_beat",
            "guidance_cut",
            "product_recall",
            "contract_win",
            "accounting_issue",
            "ceo_departure",
            "military_conflict",
            "major_earthquake",
            "dividend_raise",
            "stock_split",
            "rights_offering",
        ).all(ids::contains))
        assertTrue(templates.map(EventTemplate::type).distinct().size >= 10)
        assertTrue(templates.map(EventTemplate::scope).toSet().containsAll(
            setOf(EventScope.GLOBAL, EventScope.COUNTRY, EventScope.MARKET, EventScope.SECTOR, EventScope.STOCK),
        ))
    }

    @Test
    fun sameSeedAndContextProduceIdenticalEvents() {
        val templates = listOf(
            template("one", EventScope.STOCK),
            template("two", EventScope.SECTOR),
            template("three", EventScope.MARKET),
        )
        val context = EventGenerationContext(time, stocks, maxNewEvents = 3)

        val first = EventEngine(99L, templates).generate(context)
        val second = EventEngine(99L, templates).generate(context)

        assertEquals(first.newEvents, second.newEvents)
        assertEquals(3, first.newEvents.size)
    }

    @Test
    fun conditionsAreRequiredBeforeProbabilityIsEvaluated() {
        val rule = template("weak_krw_only", condition = EventCondition.KRW_WEAK)
        val engine = EventEngine(1L, listOf(rule))

        val normal = engine.generate(
            EventGenerationContext(time, stocks, macro = MacroEnvironment(usdKrw = 1_350.0)),
        )
        val weak = engine.generate(
            EventGenerationContext(time + 1.hours, stocks, macro = MacroEnvironment(usdKrw = 1_500.0)),
        )

        assertTrue(normal.newEvents.isEmpty())
        assertEquals(1, weak.newEvents.size)
    }

    @Test
    fun activeDuplicateAndCooldownPreventRepeatedTemplate() {
        val rule = template(
            id = "cooldown",
            duration = 2..2,
            cooldownHours = 10,
        )
        val engine = EventEngine(5L, listOf(rule))

        assertEquals(1, engine.generate(EventGenerationContext(time, stocks)).newEvents.size)
        assertTrue(engine.generate(EventGenerationContext(time + 1.hours, stocks)).newEvents.isEmpty())
        assertTrue(engine.generate(EventGenerationContext(time + 3.hours, stocks)).newEvents.isEmpty())
        assertEquals(1, engine.generate(EventGenerationContext(time + 10.hours, stocks)).newEvents.size)
    }

    @Test
    fun existingExternalEventIsAlsoDeduplicated() {
        val rule = template("external", duration = 12..12)
        val existing = event(
            id = "external:${time.epochSeconds}:44",
            startsAt = time,
            stockId = stocks.first().id,
        )
        val result = EventEngine(5L, listOf(rule)).generate(
            EventGenerationContext(time + 1.hours, stocks, existingEvents = listOf(existing)),
        )

        assertTrue(result.newEvents.isEmpty())
    }

    @Test
    fun snapshotRestoresRandomCooldownAndSequenceState() {
        val templates = listOf(template("snapshot", duration = 1..1, cooldownHours = 1))
        val firstEngine = EventEngine(123L, templates)
        firstEngine.generate(EventGenerationContext(time, stocks))
        val snapshot = firstEngine.snapshot()

        val expected = firstEngine.generate(EventGenerationContext(time + 2.hours, stocks))
        val restoredEngine = EventEngine(999L, templates)
        restoredEngine.restore(snapshot)
        val actual = restoredEngine.generate(EventGenerationContext(time + 2.hours, stocks))

        assertEquals(expected.newEvents, actual.newEvents)
        assertEquals(expected.snapshot, actual.snapshot)
    }

    @Test
    fun companyTargetIsResolvedAndInterpolatedFromCatalogData() {
        val result = EventEngine(7L, listOf(template("target", EventScope.STOCK))).generate(
            EventGenerationContext(time, stocks),
        ).newEvents.single()

        assertEquals(1, result.affectedStockIds.size)
        val target = stocks.single { it.id in result.affectedStockIds }
        assertTrue(target.name in result.title)
        assertTrue(result.affects(target))
        assertFalse(result.affects(stocks.single { it.id != target.id }))
    }

    @Test
    fun shockDecaysAndAggregateOnlyAffectsMatchingStock() {
        val target = stocks.first()
        val event = event(
            id = "shock:${time.epochSeconds}:0",
            startsAt = time,
            stockId = target.id,
            shock = 0.12,
            duration = 12,
        )

        assertEquals(0.12, EventShockCalculator.levelShockAt(event, time), 1e-12)
        val afterThree = EventShockCalculator.levelShockAt(event, time + 3.hours)
        val afterSix = EventShockCalculator.levelShockAt(event, time + 6.hours)
        assertTrue(afterThree in 0.0..0.12)
        assertTrue(afterSix in 0.0..afterThree)
        assertEquals(0.0, EventShockCalculator.levelShockAt(event, time + 12.hours))

        val firstHour = EventShockCalculator.aggregate(
            listOf(event), target, time, time + 1.hours,
        )
        val nextHour = EventShockCalculator.aggregate(
            listOf(event), target, time + 1.hours, time + 2.hours,
        )
        val unaffected = EventShockCalculator.aggregate(
            listOf(event), stocks.last(), time, time + 1.hours,
        )
        assertTrue(firstHour.returnRate > 0.0)
        assertTrue(nextHour.returnRate < 0.0)
        assertEquals(0.0, unaffected.returnRate)
        assertEquals(1.0, unaffected.volatilityMultiplier)
        assertEquals(1.0, unaffected.volumeMultiplier)
    }

    @Test
    fun intervalProbabilityScalesWithoutExceedingOne() {
        val oneHour = EventEngine.probabilityForInterval(0.24, 1)
        val oneDay = EventEngine.probabilityForInterval(0.24, 24)
        val oneWeek = EventEngine.probabilityForInterval(0.24, 168)

        assertTrue(oneHour in 0.0..<oneDay)
        assertEquals(0.24, oneDay, 1e-12)
        assertTrue(oneWeek in oneDay..1.0)
    }

    private fun template(
        id: String,
        scope: EventScope = EventScope.STOCK,
        condition: EventCondition = EventCondition.ALWAYS,
        duration: IntRange = 4..8,
        cooldownHours: Int = 24,
    ): EventTemplate = EventTemplate(
        id = id,
        titleTemplate = if (scope == EventScope.STOCK) "{company} 테스트 이벤트" else "테스트 이벤트 $id",
        descriptionTemplate = "결정론과 규칙 동작을 검증하는 이벤트다.",
        scope = scope,
        type = EventType.EARNINGS,
        severity = EventSeverity.MODERATE,
        direction = ImpactDirection.POSITIVE,
        probabilityPerDay = 1.0,
        cooldownHours = cooldownHours,
        durationHours = duration,
        shockReturn = 0.02..0.04,
        volatilityMultiplier = 1.2..1.4,
        volumeMultiplier = 1.3..1.6,
        condition = condition,
    )

    private fun event(
        id: String,
        startsAt: Instant,
        stockId: String,
        shock: Double = 0.04,
        duration: Int = 12,
    ): GameEvent = GameEvent(
        id = id,
        title = "테스트 충격",
        description = "가격 충격 감쇠 테스트",
        scope = EventScope.STOCK,
        type = EventType.EARNINGS,
        severity = EventSeverity.MODERATE,
        impact = GameEventImpact(
            direction = ImpactDirection.POSITIVE,
            shockReturn = shock,
            volatilityMultiplier = 1.8,
            volumeMultiplier = 2.0,
        ),
        startsAt = startsAt,
        durationHours = duration,
        affectedStockIds = setOf(stockId),
    )
}
