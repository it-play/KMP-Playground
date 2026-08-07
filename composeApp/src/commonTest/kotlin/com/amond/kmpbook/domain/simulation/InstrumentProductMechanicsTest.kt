package com.amond.kmpbook.domain.simulation

import com.amond.kmpbook.domain.data.StockCatalog
import com.amond.kmpbook.domain.model.EventScope
import com.amond.kmpbook.domain.model.EventSeverity
import com.amond.kmpbook.domain.model.EventType
import com.amond.kmpbook.domain.model.GameEvent
import com.amond.kmpbook.domain.model.GameEventImpact
import com.amond.kmpbook.domain.model.ImpactDirection
import com.amond.kmpbook.domain.model.InstrumentStrategy
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.MarketSession
import com.amond.kmpbook.domain.model.StockDefinition
import com.amond.kmpbook.domain.model.Sector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class InstrumentProductMechanicsTest {
    private val time = Instant.parse("2026-08-07T01:00:00Z")

    @Test
    fun singleStockInverseFlipsUnderlyingShockButNotDirectProductShock() {
        val samsung = assertNotNull(StockCatalog.findBySymbol("005930", Market.KOSPI))
        val inverseWrapper = assertNotNull(StockCatalog.findBySymbol("0193L0", Market.KOSPI))
        assertEquals(setOf(samsung.id), inverseWrapper.identityProfile?.underlyingInstrumentIds)
        assertEquals(-2.0, inverseWrapper.etfProfile?.leverage)

        val underlyingImpulse = EventShockCalculator.aggregate(
            events = listOf(negativeEvent("underlying", samsung.id, EventType.EARNINGS)),
            stock = inverseWrapper,
            from = time,
            to = time + 1.hours,
        )
        val directProductImpulse = EventShockCalculator.aggregate(
            events = listOf(negativeEvent("wrapper", inverseWrapper.id, EventType.FUND_OPERATION)),
            stock = inverseWrapper,
            from = time,
            to = time + 1.hours,
        )

        assertTrue(underlyingImpulse.referenceReturnRate < 0.0)
        assertEquals(0.0, underlyingImpulse.directProductReturnRate, 1e-12)
        assertEquals(0.0, directProductImpulse.referenceReturnRate, 1e-12)
        assertTrue(directProductImpulse.directProductReturnRate < 0.0)

        val underlyingAttribution = eventAttribution(inverseWrapper, underlyingImpulse)
        val directProductAttribution = eventAttribution(inverseWrapper, directProductImpulse)
        val directWhileReferenceClosed = eventAttribution(
            inverseWrapper,
            directProductImpulse,
            referenceTradingFraction = 0.0,
        )
        assertTrue(underlyingAttribution > 0.0)
        assertTrue(directProductAttribution < 0.0)
        assertTrue(directWhileReferenceClosed < 0.0)
        val closedListingDirectCarry = PriceEngine(seed = 91L).referenceLogReturn(
            stock = inverseWrapper,
            macro = MacroEnvironment(),
            referenceTradingFraction = 0.0,
            fxTradingFraction = 1.0,
            eventImpulse = directProductImpulse,
        )
        assertTrue(closedListingDirectCarry < 0.0)
    }

    @Test
    fun negativeDurationAndInverseTreasuryHaveExpectedRateShockDirection() {
        val risr = assertNotNull(StockCatalog.findBySymbol("RISR", Market.NYSE_ARCA))
        val tyo = assertNotNull(StockCatalog.findBySymbol("TYO", Market.NYSE_ARCA))
        assertEquals(-5.0, risr.behavior.durationYears)
        assertEquals(7.5, tyo.behavior.durationYears)
        assertEquals(-3.0, tyo.etfProfile?.leverage)

        val longTreasuryControl = tyo.copy(
            symbol = "TYO-LONG-CONTROL",
            etfProfile = assertNotNull(tyo.etfProfile).copy(leverage = 1.0),
            behaviorProfile = tyo.behavior.copy(strategy = InstrumentStrategy.TREASURY),
        )
        val risrRateAttribution = rateAttribution(risr)
        val tyoRateAttribution = rateAttribution(tyo)
        val longTreasuryRateAttribution = rateAttribution(longTreasuryControl)

        assertTrue(risrRateAttribution > 0.0)
        assertTrue(tyoRateAttribution > 0.0)
        assertTrue(longTreasuryRateAttribution < 0.0)
        assertEquals(-3.0 * longTreasuryRateAttribution, tyoRateAttribution, 1e-12)
    }

    @Test
    fun sectorNewsUsesUnderlyingExposureInsteadOfFundCategory() {
        val broadInverse = assertNotNull(StockCatalog.findBySymbol("114800", Market.KOSPI))
        val samsungInverse = assertNotNull(StockCatalog.findBySymbol("0193L0", Market.KOSPI))
        val financialEvent = sectorEvent("financial", Sector.FINANCIALS)
        val semiconductorEvent = sectorEvent("semiconductor", Sector.SEMICONDUCTOR)

        assertFalse(financialEvent.affects(broadInverse))
        assertTrue(semiconductorEvent.affects(samsungInverse))
        assertTrue(Sector.SEMICONDUCTOR in samsungInverse.identityProfile?.exposedSectors.orEmpty())
    }

    private fun eventAttribution(
        stock: StockDefinition,
        impulse: PriceImpulse,
        referenceTradingFraction: Double = 1.0,
    ): Double =
        PriceEngine(seed = 91L).generateHour(
            PriceGenerationInput(
                stock = stock,
                startTime = time,
                previousPrice = stock.initialPrice,
                dailyBasePrice = stock.initialPrice,
                session = MarketSession.REGULAR,
                eventImpulse = impulse,
                averageDailyVolume = 0L,
                referenceTradingFraction = referenceTradingFraction,
            ),
        ).attribution.event

    private fun rateAttribution(stock: StockDefinition): Double = PriceEngine(seed = 92L).generateHour(
        PriceGenerationInput(
            stock = stock,
            startTime = time,
            previousPrice = stock.initialPrice,
            dailyBasePrice = stock.initialPrice,
            session = MarketSession.REGULAR,
            macro = MacroEnvironment(policyRate = 0.0, policyRateChange = 0.001),
            averageDailyVolume = 0L,
        ),
    ).attribution.ratesAndInflation

    private fun negativeEvent(id: String, stockId: String, type: EventType): GameEvent = GameEvent(
        id = id,
        title = "negative event",
        description = "negative event for product-mechanics testing",
        scope = EventScope.STOCK,
        type = type,
        severity = EventSeverity.MODERATE,
        impact = GameEventImpact(
            direction = ImpactDirection.NEGATIVE,
            shockReturn = -0.04,
        ),
        startsAt = time,
        durationHours = 24,
        affectedStockIds = setOf(stockId),
    )

    private fun sectorEvent(id: String, sector: Sector): GameEvent = GameEvent(
        id = id,
        title = "sector event",
        description = "sector exposure routing test",
        scope = EventScope.SECTOR,
        type = EventType.INDUSTRY_SUPPLY_DEMAND,
        severity = EventSeverity.MODERATE,
        impact = GameEventImpact(direction = ImpactDirection.NEGATIVE),
        startsAt = time,
        durationHours = 24,
        affectedSectors = setOf(sector),
    )
}
