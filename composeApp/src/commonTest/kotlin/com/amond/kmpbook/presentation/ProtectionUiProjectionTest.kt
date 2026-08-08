package com.amond.kmpbook.presentation

import com.amond.kmpbook.domain.model.InstrumentTradingHalt
import com.amond.kmpbook.domain.model.InstrumentType
import com.amond.kmpbook.domain.model.InvestmentAlertDesignation
import com.amond.kmpbook.domain.model.InvestmentAlertLevel
import com.amond.kmpbook.domain.model.InvestmentAlertStatus
import com.amond.kmpbook.domain.model.KrxCircuitBreakerLevel
import com.amond.kmpbook.domain.model.KrxCircuitBreakerPhase
import com.amond.kmpbook.domain.model.KrxCircuitBreakerState
import com.amond.kmpbook.domain.model.KrxSidecarPhase
import com.amond.kmpbook.domain.model.KrxSidecarState
import com.amond.kmpbook.domain.model.KrxViDirection
import com.amond.kmpbook.domain.model.KrxViKind
import com.amond.kmpbook.domain.model.KrxViPhase
import com.amond.kmpbook.domain.model.KrxViSession
import com.amond.kmpbook.domain.model.KrxViState
import com.amond.kmpbook.domain.model.ListingFinalDisposition
import com.amond.kmpbook.domain.model.ListingFinalDispositionType
import com.amond.kmpbook.domain.model.ListingLifecycleProfileId
import com.amond.kmpbook.domain.model.ListingLifecycleReason
import com.amond.kmpbook.domain.model.ListingLifecycleState
import com.amond.kmpbook.domain.model.ListingLifecycleStatus
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.MarketMoveDirection
import com.amond.kmpbook.domain.model.ProgramOrderSide
import com.amond.kmpbook.domain.model.TradingDayWindow
import com.amond.kmpbook.domain.model.TradingHaltOrderPolicy
import com.amond.kmpbook.domain.model.TradingHaltReason
import com.amond.kmpbook.domain.model.TradingHaltStatus
import com.amond.kmpbook.domain.model.TradingProtectionSnapshot
import com.amond.kmpbook.domain.model.UsLuldBands
import com.amond.kmpbook.domain.model.UsLuldPhase
import com.amond.kmpbook.domain.model.UsLuldState
import com.amond.kmpbook.domain.model.UsLuldTier
import com.amond.kmpbook.domain.model.UsMwcbLevel
import com.amond.kmpbook.domain.model.UsMwcbPhase
import com.amond.kmpbook.domain.model.UsMwcbState
import com.amond.kmpbook.domain.time.GameCalendar
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ProtectionUiProjectionTest {
    private val date = LocalDate(2026, 8, 10)
    private val now = Instant.parse("2026-08-10T01:10:00Z")

    @Test
    fun normalSnapshotDoesNotCreateNoise() {
        val stockId = "KOSPI:005930"
        val projection = buildProtectionUiProjection(
            snapshot = TradingProtectionSnapshot(),
            listingStates = mapOf(stockId to listedState(stockId)),
            selectedStockId = stockId,
        )

        assertNull(projection.marketStrip)
        assertTrue(projection.symbolBadges.isEmpty())
        assertNull(projection.selectedSymbolDetail)
    }

    @Test
    fun marketStripShowsStrongestBlockerAndCollapsesTheRest() {
        val cb = KrxCircuitBreakerState(
            market = Market.KOSPI,
            tradingDate = date,
            phase = KrxCircuitBreakerPhase.HALTED,
            triggeredLevels = setOf(KrxCircuitBreakerLevel.LEVEL_1),
            triggerIndexValues = mapOf(KrxCircuitBreakerLevel.LEVEL_1 to 2_500.0),
            activeLevel = KrxCircuitBreakerLevel.LEVEL_1,
            triggeredAt = now,
            haltEndsAt = now + 20.minutes,
            reopeningEndsAt = now + 30.minutes,
        )
        val sidecar = KrxSidecarState(
            market = Market.KOSDAQ,
            tradingDate = date,
            phase = KrxSidecarPhase.PROGRAM_FLOW_SUSPENDED,
            activationUsed = true,
            triggeredDirection = MarketMoveDirection.DOWN,
            suspendedProgramSide = ProgramOrderSide.SELL,
            triggeredAt = now,
            suspensionEndsAt = now + 5.minutes,
        )

        val projection = buildProtectionUiProjection(
            snapshot = TradingProtectionSnapshot(
                krxCircuitBreakers = mapOf(Market.KOSPI to cb),
                krxSidecars = mapOf(Market.KOSDAQ to sidecar),
            ),
            selectedStockId = "KOSPI:005930",
        )

        assertEquals("코스피 거래정지 외 1", projection.marketStrip?.badge?.text)
        assertEquals("코스피 거래가 잠시 멈췄어요", projection.marketStrip?.title)
        assertEquals(ProtectionBadgeEmphasis.FILL, projection.marketStrip?.badge?.emphasis)
        assertEquals(1, projection.marketStrip?.additionalCount)
        assertEquals(0, projection.selectedSymbolDetail?.additionalCount)
        assertEquals("코스피 거래정지", projection.selectedSymbolDetail?.badge?.text)
    }

    @Test
    fun symbolBadgeUsesOneStrongestStateAndOutsideCount() {
        val stockId = "KOSPI:005930"
        val listing = listedState(stockId).copy(
            status = ListingLifecycleStatus.DEFICIENCY_NOTICE,
            activeReason = ListingLifecycleReason.KRX_LISTING_MAINTENANCE,
            designatedOn = date,
            cureDeadline = LocalDate(2026, 9, 10),
            designationCount = 1,
        )
        val halt = InstrumentTradingHalt(
            stockId = stockId,
            reason = TradingHaltReason.MATERIAL_DISCLOSURE,
            detail = "중요 공시 내용을 확인하고 있어요.",
            startedAt = now,
            policy = TradingHaltOrderPolicy(
                acceptsNewOrders = false,
                allowsCancellation = true,
                allowsExecution = false,
            ),
            scheduledReleaseAt = now + 30.minutes,
        )
        val vi = KrxViState(
            stockId = stockId,
            market = Market.KOSPI,
            phase = KrxViPhase.CALL_AUCTION,
            kind = KrxViKind.DYNAMIC,
            session = KrxViSession.CONTINUOUS_AUCTION,
            referencePrice = 75_000.0,
            triggerRate = 0.03,
            direction = KrxViDirection.LOWER,
            triggeredAt = now,
            auctionEndsAt = now + 2.minutes,
            triggerCount = 1,
        )
        val danger = InvestmentAlertDesignation(
            stockId = stockId,
            level = InvestmentAlertLevel.DANGER,
            reasonCodes = setOf("SHORT_TERM_SURGE"),
            summary = "단기 급등",
            designatedAt = now,
            designatedOn = date,
            releaseReviewWindow = TradingDayWindow(date, LocalDate(2026, 8, 14)),
        )

        val projection = buildProtectionUiProjection(
            snapshot = TradingProtectionSnapshot(
                krxVolatilityInterruptions = mapOf(stockId to vi),
                instrumentTradingHalts = mapOf(stockId to halt),
                investmentAlerts = mapOf(stockId to danger),
            ),
            listingStates = mapOf(stockId to listing),
            selectedStockId = stockId,
        )

        val badge = projection.symbolBadges.getValue(stockId)
        assertEquals("거래정지 외 3", badge.text)
        assertEquals(ProtectionUiTone.CRITICAL, badge.tone)
        assertEquals("이 종목은 거래가 멈춰 있어요", projection.selectedSymbolDetail?.primary?.title)
        assertEquals(3, projection.selectedSymbolDetail?.additionalCount)
    }

    @Test
    fun priorAlertLevelRemainsUntilTheNewDesignationEffectiveDate() {
        val stockId = "KOSPI:005930"
        val dangerFromTomorrow = InvestmentAlertDesignation(
            stockId = stockId,
            level = InvestmentAlertLevel.DANGER,
            reasonCodes = setOf("DANGER_NOTICE_SURGE"),
            summary = "투자위험 지정 공시",
            designatedAt = now,
            designatedOn = LocalDate(2026, 8, 11),
            releaseReviewWindow = TradingDayWindow(
                LocalDate(2026, 8, 11),
                LocalDate(2026, 9, 30),
            ),
            priorLevelUntilEffective = InvestmentAlertLevel.WARNING,
        )

        val beforeEffective = buildProtectionUiProjection(
            snapshot = TradingProtectionSnapshot(
                investmentAlerts = mapOf(stockId to dangerFromTomorrow),
            ),
            selectedStockId = stockId,
            selectedMarket = Market.KOSPI,
            at = now,
        )

        assertEquals("투자경고", beforeEffective.symbolBadges.getValue(stockId).text)
        assertEquals(ProtectionBadgeEmphasis.WEAK, beforeEffective.symbolBadges.getValue(stockId).emphasis)
        assertTrue(beforeEffective.selectedSymbolDetail?.primary?.summary.orEmpty().contains("내일부터 투자위험"))

        val effectiveAt = GameCalendar.fromGameLocalDateTime(LocalDateTime(2026, 8, 11, 9, 0))
        val afterEffective = buildProtectionUiProjection(
            snapshot = TradingProtectionSnapshot(
                investmentAlerts = mapOf(stockId to dangerFromTomorrow),
            ),
            selectedStockId = stockId,
            selectedMarket = Market.KOSPI,
            at = effectiveAt,
        )

        assertEquals("투자위험", afterEffective.symbolBadges.getValue(stockId).text)
        assertEquals(ProtectionBadgeEmphasis.FILL, afterEffective.symbolBadges.getValue(stockId).emphasis)
    }

    @Test
    fun designationWithoutPriorLevelStaysAnUpcomingWeakBadgeUntilEffective() {
        val stockId = "KOSPI:035420"
        val warningFromTomorrow = InvestmentAlertDesignation(
            stockId = stockId,
            level = InvestmentAlertLevel.WARNING,
            reasonCodes = setOf("WARNING_NOTICE_SURGE"),
            summary = "투자경고 지정 공시",
            designatedAt = now,
            designatedOn = LocalDate(2026, 8, 11),
            releaseReviewWindow = TradingDayWindow(
                LocalDate(2026, 8, 11),
                LocalDate(2026, 9, 30),
            ),
        )

        val beforeEffective = buildProtectionUiProjection(
            snapshot = TradingProtectionSnapshot(
                investmentAlerts = mapOf(stockId to warningFromTomorrow),
            ),
            selectedStockId = stockId,
            selectedMarket = Market.KOSPI,
            at = now,
        )

        assertEquals("투자경고 예정", beforeEffective.symbolBadges.getValue(stockId).text)
        assertEquals(ProtectionBadgeEmphasis.WEAK, beforeEffective.symbolBadges.getValue(stockId).emphasis)
        assertTrue(beforeEffective.selectedSymbolDetail?.primary?.title.orEmpty().startsWith("내일부터"))
        assertTrue(beforeEffective.selectedSymbolDetail?.primary?.orderImpact.orEmpty().contains("제한되지는 않아요"))

        val effectiveAt = GameCalendar.fromGameLocalDateTime(LocalDateTime(2026, 8, 11, 9, 0))
        val afterEffective = buildProtectionUiProjection(
            snapshot = TradingProtectionSnapshot(
                investmentAlerts = mapOf(stockId to warningFromTomorrow),
            ),
            selectedStockId = stockId,
            selectedMarket = Market.KOSPI,
            at = effectiveAt,
        )

        assertEquals("투자경고", afterEffective.symbolBadges.getValue(stockId).text)
    }

    @Test
    fun releasedAlertRemainsVisibleUntilTheNextTradingDateTakesEffect() {
        val stockId = "KOSPI:000660"
        val releaseTomorrow = InvestmentAlertDesignation(
            stockId = stockId,
            level = InvestmentAlertLevel.WARNING,
            reasonCodes = setOf("WARNING_60_100"),
            summary = "투자경고 해제 판단",
            designatedAt = Instant.parse("2026-08-01T00:00:00Z"),
            designatedOn = LocalDate(2026, 8, 3),
            releaseReviewWindow = TradingDayWindow(date, LocalDate(2026, 9, 30)),
            status = InvestmentAlertStatus.RELEASED,
            releasedAt = now,
            releasedOn = date,
            releaseEffectiveOn = LocalDate(2026, 8, 11),
            releaseReason = "해제 기준 충족",
        )

        val beforeEffective = buildProtectionUiProjection(
            snapshot = TradingProtectionSnapshot(
                investmentAlerts = mapOf(stockId to releaseTomorrow),
            ),
            selectedStockId = stockId,
            selectedMarket = Market.KOSPI,
            at = now,
        )

        assertEquals("투자경고", beforeEffective.symbolBadges.getValue(stockId).text)
        assertTrue(beforeEffective.selectedSymbolDetail?.primary?.summary.orEmpty().contains("내일부터"))
        assertTrue(beforeEffective.selectedSymbolDetail?.primary?.summary.orEmpty().contains("해제"))

        val effectiveAt = GameCalendar.fromGameLocalDateTime(LocalDateTime(2026, 8, 11, 9, 0))
        val afterEffective = buildProtectionUiProjection(
            snapshot = TradingProtectionSnapshot(
                investmentAlerts = mapOf(stockId to releaseTomorrow),
            ),
            selectedStockId = stockId,
            selectedMarket = Market.KOSPI,
            at = effectiveAt,
        )

        assertTrue(stockId !in afterEffective.symbolBadges)
        assertNull(afterEffective.selectedSymbolDetail)
    }

    @Test
    fun alertEffectiveDateUsesTheStocksMarketDateInsteadOfUtcDate() {
        val stockId = "KOSPI:051910"
        val effectiveAtMidnightKst = Instant.parse("2026-08-09T15:30:00Z")
        val warning = InvestmentAlertDesignation(
            stockId = stockId,
            level = InvestmentAlertLevel.WARNING,
            reasonCodes = setOf("WARNING_NOTICE_SURGE"),
            summary = "투자경고 지정 공시",
            designatedAt = Instant.parse("2026-08-09T06:00:00Z"),
            designatedOn = date,
            releaseReviewWindow = TradingDayWindow(date, LocalDate(2026, 9, 30)),
        )

        val projection = buildProtectionUiProjection(
            snapshot = TradingProtectionSnapshot(
                investmentAlerts = mapOf(stockId to warning),
            ),
            selectedStockId = stockId,
            at = effectiveAtMidnightKst,
        )

        assertEquals("투자경고", projection.symbolBadges.getValue(stockId).text)
        assertTrue(!projection.selectedSymbolDetail?.primary?.title.orEmpty().contains("예정"))
    }

    @Test
    fun releasedNoticesAreNotShownAsCurrentStatus() {
        val stockId = "KOSPI:005930"
        val releasedHalt = InstrumentTradingHalt(
            stockId = stockId,
            reason = TradingHaltReason.TECHNICAL_DISRUPTION,
            detail = "시스템 점검",
            startedAt = now,
            policy = TradingHaltOrderPolicy(
                acceptsNewOrders = false,
                allowsCancellation = false,
                allowsExecution = false,
            ),
            status = TradingHaltStatus.RELEASED,
            releasedAt = now + 5.minutes,
            releaseNote = "정상화",
        )

        val projection = buildProtectionUiProjection(
            snapshot = TradingProtectionSnapshot(
                instrumentTradingHalts = mapOf(stockId to releasedHalt),
            ),
            selectedStockId = stockId,
        )

        assertTrue(stockId !in projection.symbolBadges)
        assertNull(projection.selectedSymbolDetail)
    }

    @Test
    fun selectedUsStockCombinesItsLuldPauseWithUsMarketReopening() {
        val stockId = "NASDAQ:AAPL"
        val luld = UsLuldState(
            stockId = stockId,
            primaryMarket = Market.NASDAQ,
            tradingDate = date,
            tier = UsLuldTier.TIER_1,
            previousClose = 200.0,
            referencePrice = 190.0,
            referencePriceEffectiveAt = now,
            bands = UsLuldBands(
                referencePrice = 190.0,
                lower = 180.5,
                upper = 199.5,
                bandAmount = 0.05,
                doubledForClosingWindow = false,
            ),
            phase = UsLuldPhase.TRADING_PAUSE,
            pauseStartedAt = now,
            pauseEndsAt = now + 5.minutes,
        )
        val mwcb = UsMwcbState(
            tradingDate = date,
            phase = UsMwcbPhase.REOPENING_AUCTIONS,
            triggeredLevels = setOf(UsMwcbLevel.LEVEL_1),
            activeLevel = UsMwcbLevel.LEVEL_1,
            triggeredAt = now - 15.minutes,
            haltEndsAt = now,
        )

        val projection = buildProtectionUiProjection(
            snapshot = TradingProtectionSnapshot(
                usMarketWideCircuitBreaker = mwcb,
                usLuldStates = mapOf(stockId to luld),
            ),
            selectedStockId = stockId,
            selectedMarket = Market.NASDAQ,
        )

        assertEquals("LULD 거래정지", projection.symbolBadges.getValue(stockId).text)
        assertEquals("LULD 거래정지 외 1", projection.selectedSymbolDetail?.badge?.text)
        assertEquals("가격 급변으로 거래가 잠시 멈췄어요", projection.selectedSymbolDetail?.primary?.title)
        assertEquals("미국 재개 경매", projection.selectedSymbolDetail?.additional?.single()?.badgeLabel)
    }

    @Test
    fun terminalListingExplainsTheFinalDisposition() {
        val stockId = "NASDAQ:TEST"
        val delisted = ListingLifecycleState(
            stockId = stockId,
            market = Market.NASDAQ,
            instrumentType = InstrumentType.STOCK,
            profileId = ListingLifecycleProfileId.NASDAQ_EQUITY_PUBLIC_RULE_WITH_GAME_APPROXIMATION,
            status = ListingLifecycleStatus.DELISTED,
            finalDisposition = ListingFinalDisposition(
                type = ListingFinalDispositionType.OTC_TRANSFER,
                effectiveOn = date,
            ),
        )

        val projection = buildProtectionUiProjection(
            snapshot = TradingProtectionSnapshot(),
            listingStates = mapOf(stockId to delisted),
            selectedStockId = stockId,
        )

        assertEquals("상장폐지", projection.symbolBadges.getValue(stockId).text)
        assertTrue(projection.selectedSymbolDetail?.primary?.summary.orEmpty().contains("장외시장"))
        assertEquals(ProtectionBadgeEmphasis.FILL, projection.symbolBadges.getValue(stockId).emphasis)
    }

    @Test
    fun terminalListingSuppressesStaleTradingProtectionStatuses() {
        val stockId = "NASDAQ:TERMINAL"
        val terminal = ListingLifecycleState(
            stockId = stockId,
            market = Market.NASDAQ,
            instrumentType = InstrumentType.STOCK,
            profileId = ListingLifecycleProfileId.NASDAQ_EQUITY_PUBLIC_RULE_WITH_GAME_APPROXIMATION,
            status = ListingLifecycleStatus.DELISTED,
            finalDisposition = ListingFinalDisposition(
                type = ListingFinalDispositionType.WORTHLESS_DISPOSITION,
                effectiveOn = date,
            ),
        )
        val staleHalt = InstrumentTradingHalt(
            stockId = stockId,
            reason = TradingHaltReason.DELISTING_PROCESS,
            detail = "상장폐지 절차 거래정지",
            startedAt = now - 30.minutes,
            policy = TradingHaltOrderPolicy(
                acceptsNewOrders = false,
                allowsCancellation = true,
                allowsExecution = false,
            ),
        )
        val staleVi = KrxViState(
            stockId = stockId,
            market = Market.KOSPI,
            phase = KrxViPhase.CALL_AUCTION,
            kind = KrxViKind.STATIC,
            session = KrxViSession.CONTINUOUS_AUCTION,
            referencePrice = 100.0,
            triggerRate = 0.10,
            direction = KrxViDirection.LOWER,
            triggeredAt = now,
            auctionEndsAt = now + 2.minutes,
            triggerCount = 1,
        )
        val staleAlert = InvestmentAlertDesignation(
            stockId = stockId,
            level = InvestmentAlertLevel.DANGER,
            reasonCodes = setOf("TERMINAL_STALE_ALERT"),
            summary = "상장 종료 전 투자위험",
            designatedAt = now - 30.minutes,
            designatedOn = date,
            releaseReviewWindow = TradingDayWindow(date, LocalDate(2026, 9, 30)),
        )
        val staleLuld = UsLuldState(
            stockId = stockId,
            primaryMarket = Market.NASDAQ,
            tradingDate = date,
            tier = UsLuldTier.TIER_2,
            previousClose = 1.0,
            referencePrice = 0.8,
            referencePriceEffectiveAt = now,
            bands = UsLuldBands(
                referencePrice = 0.8,
                lower = 0.64,
                upper = 0.96,
                bandAmount = 0.20,
                doubledForClosingWindow = false,
            ),
            phase = UsLuldPhase.TRADING_PAUSE,
            pauseStartedAt = now,
            pauseEndsAt = now + 5.minutes,
        )
        val marketReopening = UsMwcbState(
            tradingDate = date,
            phase = UsMwcbPhase.REOPENING_AUCTIONS,
            triggeredLevels = setOf(UsMwcbLevel.LEVEL_1),
            activeLevel = UsMwcbLevel.LEVEL_1,
            triggeredAt = now - 15.minutes,
            haltEndsAt = now,
        )

        val projection = buildProtectionUiProjection(
            snapshot = TradingProtectionSnapshot(
                instrumentTradingHalts = mapOf(stockId to staleHalt),
                krxVolatilityInterruptions = mapOf(stockId to staleVi),
                investmentAlerts = mapOf(stockId to staleAlert),
                usLuldStates = mapOf(stockId to staleLuld),
                usMarketWideCircuitBreaker = marketReopening,
            ),
            listingStates = mapOf(stockId to terminal),
            selectedStockId = stockId,
            selectedMarket = Market.NASDAQ,
            at = now,
        )

        assertEquals("상장폐지", projection.symbolBadges.getValue(stockId).text)
        assertEquals(1, projection.selectedSymbolDetail?.statuses?.size)
        assertEquals("상장폐지", projection.selectedSymbolDetail?.primary?.badgeLabel)
        assertEquals(0, projection.selectedSymbolDetail?.additionalCount)
        assertTrue(projection.marketStrip != null)
    }

    private fun listedState(stockId: String): ListingLifecycleState = ListingLifecycleState(
        stockId = stockId,
        market = Market.KOSPI,
        instrumentType = InstrumentType.STOCK,
        profileId = ListingLifecycleProfileId.KRX_EQUITY_GAME_APPROXIMATION,
    )
}
