package com.amond.kmpbook.domain.model

import com.amond.kmpbook.domain.simulation.testStock
import com.amond.kmpbook.domain.tax.FifoCostBasisBook
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class InstrumentMechanicsTest {
    @Test
    fun reverseSplitChangesUnitsButPreservesFifoTotalBasis() {
        val original = FifoCostBasisBook().addPurchase(
            lotId = "lot-1",
            stockId = "NASDAQ:QQQY",
            acquiredOn = LocalDate(2026, 8, 7),
            quantity = 30.0,
            purchasePriceKrw = 300_000L,
        )

        val adjusted = original.applyQuantityMultiplier("NASDAQ:QQQY", 1.0 / 3.0)

        assertEquals(10.0, adjusted.lots.single().remainingQuantity, 1e-12)
        assertEquals(300_000L, adjusted.lots.single().remainingCostBasisKrw)
    }

    @Test
    fun returnOfCapitalReducesBasisAndOnlyExcessBecomesGain() {
        val original = FifoCostBasisBook().addPurchase(
            lotId = "lot-1",
            stockId = "NYSE_ARCA:YMAX",
            acquiredOn = LocalDate(2026, 8, 7),
            quantity = 10.0,
            purchasePriceKrw = 100_000L,
        )

        val (reduced, excess) = original.applyReturnOfCapital("NYSE_ARCA:YMAX", 120_000L)

        assertEquals(0L, reduced.lots.single().remainingCostBasisKrw)
        assertEquals(20_000L, excess)
    }

    @Test
    fun returnOfCapitalIsAllocatedByQuantityAcrossLotsBeforeExcessGain() {
        val stockId = "NYSE_ARCA:YMAX"
        val otherStockId = "NASDAQ:AAPL"
        val original = FifoCostBasisBook()
            .addPurchase(
                lotId = "thin-basis",
                stockId = stockId,
                acquiredOn = LocalDate(2026, 7, 1),
                quantity = 1.0,
                purchasePriceKrw = 5_000L,
            )
            .addPurchase(
                lotId = "deep-basis",
                stockId = stockId,
                acquiredOn = LocalDate(2026, 8, 1),
                quantity = 3.0,
                purchasePriceKrw = 90_000L,
            )
            .addPurchase(
                lotId = "unrelated",
                stockId = otherStockId,
                acquiredOn = LocalDate(2026, 8, 2),
                quantity = 2.0,
                purchasePriceKrw = 40_000L,
            )

        val (reduced, excess) = original.applyReturnOfCapital(stockId, 40_000L)
        val lotsById = reduced.lots.associateBy { it.lotId }

        // Four units receive KRW 10,000 each. The first lot can absorb only KRW 5,000,
        // while the three-unit lot absorbs its full KRW 30,000 allocation.
        assertEquals(0L, lotsById.getValue("thin-basis").remainingCostBasisKrw)
        assertEquals(60_000L, lotsById.getValue("deep-basis").remainingCostBasisKrw)
        assertEquals(40_000L, lotsById.getValue("unrelated").remainingCostBasisKrw)
        assertEquals(5_000L, excess)
    }

    @Test
    fun inverseFundReceivesOppositeDirectionForBroadMarketNews() {
        val inverse = testStock(symbol = "INV").copy(
            etfProfile = EtfProfile(
                benchmark = "F-KOSPI200 일간 -1배",
                assetClass = EtfAssetClass.BROAD_EQUITY,
                taxCategory = EtfTaxCategory.KOREAN_OTHER,
                annualExpenseRatio = 0.006,
                leverage = -1.0,
            ),
        )
        val event = GameEvent(
            id = "market-rally",
            title = "시장 상승",
            description = "기초시장이 상승했다.",
            scope = EventScope.MARKET,
            type = EventType.MARKET_SENTIMENT,
            severity = EventSeverity.MODERATE,
            impact = GameEventImpact(direction = ImpactDirection.POSITIVE, shockReturn = 0.02),
            startsAt = Instant.parse("2026-08-07T00:00:00Z"),
            durationHours = 24,
            affectedMarkets = setOf(Market.KOSPI),
        )

        assertEquals(ImpactDirection.NEGATIVE, event.directionFor(inverse))
        assertTrue(event.relevanceTo(listOf(inverse), setOf(inverse.id), emptySet()).isHoldingRelated)
    }

    @Test
    fun coveredCallInferenceCapsUpsideAndAllowsPrincipalErosion() {
        val coveredCall = testStock(symbol = "INCOME", market = Market.NYSE_ARCA).copy(
            englishName = "Test Covered Call Option Income ETF",
            etfProfile = EtfProfile(
                benchmark = "Covered Call Option Income",
                assetClass = EtfAssetClass.BROAD_EQUITY,
                taxCategory = EtfTaxCategory.FOREIGN_LISTED,
                annualExpenseRatio = 0.009,
            ),
        )

        assertEquals(InstrumentStrategy.COVERED_CALL, coveredCall.behavior.strategy)
        assertTrue(coveredCall.behavior.upsideParticipation < coveredCall.behavior.downsideParticipation)
        assertTrue(coveredCall.behavior.distributionCoverageRatio < 1.0)
        assertEquals(PrincipalRisk.OPTION_INCOME_EROSION, coveredCall.behavior.principalRisk)
    }
}
