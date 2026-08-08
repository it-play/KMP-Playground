package com.amond.kmpbook.domain.simulation

import com.amond.kmpbook.domain.model.InvestmentAlertDesignation
import com.amond.kmpbook.domain.model.InvestmentAlertLevel
import com.amond.kmpbook.domain.model.InvestmentAlertStatus
import com.amond.kmpbook.domain.model.KrxCircuitBreakerEvent
import com.amond.kmpbook.domain.model.KrxCircuitBreakerLevel
import com.amond.kmpbook.domain.model.KrxCircuitBreakerObservation
import com.amond.kmpbook.domain.model.KrxCircuitBreakerPhase
import com.amond.kmpbook.domain.model.KrxCircuitBreakerState
import com.amond.kmpbook.domain.model.KrxSidecarEvent
import com.amond.kmpbook.domain.model.KrxSidecarObservation
import com.amond.kmpbook.domain.model.KrxSidecarPhase
import com.amond.kmpbook.domain.model.KrxSidecarReleaseReason
import com.amond.kmpbook.domain.model.KrxViEvent
import com.amond.kmpbook.domain.model.KrxViKind
import com.amond.kmpbook.domain.model.KrxViObservation
import com.amond.kmpbook.domain.model.KrxViPhase
import com.amond.kmpbook.domain.model.KrxViProductClass
import com.amond.kmpbook.domain.model.KrxViSession
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.ProgramOrderSide
import com.amond.kmpbook.domain.model.TradingDayWindow
import com.amond.kmpbook.domain.model.TradingExecutionMode
import com.amond.kmpbook.domain.model.TradingHaltReason
import com.amond.kmpbook.domain.model.TradingPermissionDecision
import com.amond.kmpbook.domain.model.TradingProtectionAction
import com.amond.kmpbook.domain.model.TradingProtectionRequest
import com.amond.kmpbook.domain.model.TradingProtectionSnapshot
import com.amond.kmpbook.domain.model.TradingRestrictionSource
import com.amond.kmpbook.domain.model.TriggeringQuotationDisposition
import com.amond.kmpbook.domain.model.UsLuldEvent
import com.amond.kmpbook.domain.model.UsLuldLimitSide
import com.amond.kmpbook.domain.model.UsLuldObservation
import com.amond.kmpbook.domain.model.UsLuldPhase
import com.amond.kmpbook.domain.model.UsLuldTier
import com.amond.kmpbook.domain.model.UsMwcbEvent
import com.amond.kmpbook.domain.model.UsMwcbLevel
import com.amond.kmpbook.domain.model.UsMwcbObservation
import com.amond.kmpbook.domain.model.UsMwcbPhase
import com.amond.kmpbook.domain.model.UsMwcbVenuePhase
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class TradingProtectionEngineTest {
    private val date = LocalDate(2026, 8, 10)
    private val nextDate = LocalDate(2026, 8, 11)
    private val time = Instant.parse("2026-08-10T01:00:00Z")

    @Test
    fun krxCircuitBreakerRequiresFullMinuteAndUsesIntrabarCrossingTime() {
        val initial = TradingProtectionEngine.initialKrxCircuitBreaker(Market.KOSPI, date)
        val early = TradingProtectionEngine.evaluateKrxCircuitBreaker(
            initial,
            cbObservation(
                observedAt = time,
                indexValue = 91.9,
                conditionSatisfiedSince = time - 59.seconds,
            ),
        )

        assertEquals(KrxCircuitBreakerEvent.PERSISTENCE_STARTED, early.event)
        assertEquals(KrxCircuitBreakerPhase.NORMAL, early.state.phase)
        assertEquals(KrxCircuitBreakerLevel.LEVEL_1, early.state.pendingLevel)

        val exactMinute = TradingProtectionEngine.evaluateKrxCircuitBreaker(
            early.state,
            cbObservation(
                observedAt = time + 1.seconds,
                indexValue = 91.8,
                conditionSatisfiedSince = time - 59.seconds,
            ),
        )

        assertEquals(KrxCircuitBreakerEvent.LEVEL_1_TRIGGERED, exactMinute.event)
        assertEquals(KrxCircuitBreakerPhase.HALTED, exactMinute.state.phase)
        assertEquals(time + 20.minutes + 1.seconds, exactMinute.state.haltEndsAt)
        assertEquals(time + 30.minutes + 1.seconds, exactMinute.state.reopeningEndsAt)
    }

    @Test
    fun krxCircuitBreakerRunsTwentyMinuteHaltThenTenMinuteAuctionAndDoesNotRepeatLevel() {
        val triggered = triggerKrxLevelOne(Market.KOSPI, indexValue = 91.9)
        val haltEnd = requireNotNull(triggered.haltEndsAt)
        val reopeningEnd = requireNotNull(triggered.reopeningEndsAt)

        assertEquals(
            KrxCircuitBreakerPhase.HALTED,
            TradingProtectionEngine.advanceKrxCircuitBreaker(triggered, haltEnd - 1.seconds).state.phase,
        )
        assertEquals(
            KrxCircuitBreakerPhase.REOPENING_CALL_AUCTION,
            TradingProtectionEngine.advanceKrxCircuitBreaker(triggered, haltEnd).state.phase,
        )
        val reopened = TradingProtectionEngine.advanceKrxCircuitBreaker(triggered, reopeningEnd)
        assertEquals(KrxCircuitBreakerEvent.REOPENING_COMPLETED, reopened.event)
        assertEquals(KrxCircuitBreakerPhase.NORMAL, reopened.state.phase)

        val stillDown = TradingProtectionEngine.evaluateKrxCircuitBreaker(
            reopened.state,
            cbObservation(
                observedAt = reopeningEnd + 2.minutes,
                indexValue = 91.0,
                conditionSatisfiedSince = reopeningEnd,
            ),
        )
        assertNull(stillDown.state.pendingLevel)
        assertEquals(setOf(KrxCircuitBreakerLevel.LEVEL_1), stillDown.state.triggeredLevels)
    }

    @Test
    fun krxLevelTwoAndThreeRequirePriorStageAndAdditionalOnePercentDecline() {
        // A gap can trigger L1 at a value already below the nominal L2 threshold.
        val levelOne = triggerKrxLevelOne(Market.KOSPI, indexValue = 84.0)
        val reopenedAfterOne = TradingProtectionEngine.advanceKrxCircuitBreaker(
            levelOne,
            requireNotNull(levelOne.reopeningEndsAt),
        ).state
        val notOnePercentLower = TradingProtectionEngine.evaluateKrxCircuitBreaker(
            reopenedAfterOne,
            cbObservation(
                observedAt = time + 31.minutes,
                indexValue = 83.5,
                conditionSatisfiedSince = time + 30.minutes,
            ),
        )
        assertNull(notOnePercentLower.state.pendingLevel)

        val levelTwo = TradingProtectionEngine.evaluateKrxCircuitBreaker(
            notOnePercentLower.state,
            cbObservation(
                observedAt = time + 32.minutes,
                indexValue = 83.0,
                conditionSatisfiedSince = time + 31.minutes,
            ),
        )
        assertEquals(KrxCircuitBreakerEvent.LEVEL_2_TRIGGERED, levelTwo.event)

        val reopenedAfterTwo = TradingProtectionEngine.advanceKrxCircuitBreaker(
            levelTwo.state,
            requireNotNull(levelTwo.state.reopeningEndsAt),
        ).state
        val levelThree = TradingProtectionEngine.evaluateKrxCircuitBreaker(
            reopenedAfterTwo,
            cbObservation(
                observedAt = time + 63.minutes,
                indexValue = 79.0,
                conditionSatisfiedSince = time + 62.minutes,
                minutesUntilClose = 5.0,
            ),
        )
        assertEquals(KrxCircuitBreakerEvent.LEVEL_3_TRIGGERED, levelThree.event)
        assertEquals(KrxCircuitBreakerPhase.CLOSED_FOR_DAY, levelThree.state.phase)
    }

    @Test
    fun krxLevelOneAndTwoRespectExactFortyMinuteCutoffButLevelThreeDoesNot() {
        val initial = TradingProtectionEngine.initialKrxCircuitBreaker(Market.KOSDAQ, date)
        val atCutoff = TradingProtectionEngine.evaluateKrxCircuitBreaker(
            initial,
            cbObservation(
                market = Market.KOSDAQ,
                indexValue = 90.0,
                minutesUntilClose = 40.0,
                conditionSatisfiedSince = time - 2.minutes,
            ),
        )
        assertEquals(KrxCircuitBreakerPhase.NORMAL, atCutoff.state.phase)
        assertNull(atCutoff.state.pendingLevel)

        val priorStages = KrxCircuitBreakerState(
            market = Market.KOSDAQ,
            tradingDate = date,
            triggeredLevels = setOf(KrxCircuitBreakerLevel.LEVEL_1, KrxCircuitBreakerLevel.LEVEL_2),
            triggerIndexValues = mapOf(
                KrxCircuitBreakerLevel.LEVEL_1 to 92.0,
                KrxCircuitBreakerLevel.LEVEL_2 to 82.0,
            ),
        )
        val levelThree = TradingProtectionEngine.evaluateKrxCircuitBreaker(
            priorStages,
            cbObservation(
                market = Market.KOSDAQ,
                indexValue = 80.0,
                minutesUntilClose = 0.5,
                conditionSatisfiedSince = time - 1.minutes,
            ),
        )
        assertEquals(KrxCircuitBreakerEvent.LEVEL_3_TRIGGERED, levelThree.event)
    }

    @Test
    fun krxCircuitBreakersAreIndependentByMarketAndResetNextDay() {
        val kospi = triggerKrxLevelOne(Market.KOSPI)
        val kosdaq = TradingProtectionEngine.initialKrxCircuitBreaker(Market.KOSDAQ, date)
        assertTrue(KrxCircuitBreakerLevel.LEVEL_1 in kospi.triggeredLevels)
        assertTrue(kosdaq.triggeredLevels.isEmpty())

        val reset = TradingProtectionEngine.evaluateKrxCircuitBreaker(
            kospi,
            cbObservation(
                market = Market.KOSPI,
                tradingDate = nextDate,
                observedAt = time + (24 * 60).minutes,
                indexValue = 100.0,
            ),
        )
        assertEquals(KrxCircuitBreakerEvent.SESSION_RESET, reset.event)
        assertTrue(reset.state.triggeredLevels.isEmpty())
    }

    @Test
    fun kospiSidecarStartsAtMinuteFiveAndSuspendsOnlyProgramBuyAfterRise() {
        val initial = TradingProtectionEngine.initialKrxSidecar(Market.KOSPI, date)
        val tooEarly = TradingProtectionEngine.evaluateKrxSidecar(
            initial,
            sidecarObservation(minutesAfterOpen = 4.99, conditionSatisfiedSince = time - 2.minutes),
        )
        assertEquals(KrxSidecarPhase.IDLE, tooEarly.state.phase)

        val activated = TradingProtectionEngine.evaluateKrxSidecar(
            tooEarly.state,
            sidecarObservation(minutesAfterOpen = 5.0, conditionSatisfiedSince = time - 1.minutes),
        )
        assertEquals(KrxSidecarEvent.ACTIVATED, activated.event)
        assertEquals(ProgramOrderSide.BUY, activated.state.suspendedProgramSide)
        assertEquals(time + 5.minutes, activated.state.suspensionEndsAt)
    }

    @Test
    fun kosdaqSidecarRequiresFuturesAndSpotInSameDirectionForOneMinute() {
        val initial = TradingProtectionEngine.initialKrxSidecar(Market.KOSDAQ, date)
        val mismatched = TradingProtectionEngine.evaluateKrxSidecar(
            initial,
            sidecarObservation(
                market = Market.KOSDAQ,
                futuresChangeRate = 0.061,
                spotChangeRate = -0.031,
                conditionSatisfiedSince = time - 1.minutes,
            ),
        )
        assertEquals(KrxSidecarPhase.IDLE, mismatched.state.phase)

        val activated = TradingProtectionEngine.evaluateKrxSidecar(
            mismatched.state,
            sidecarObservation(
                market = Market.KOSDAQ,
                futuresChangeRate = -0.061,
                spotChangeRate = -0.031,
                conditionSatisfiedSince = time - 1.minutes,
            ),
        )
        assertEquals(KrxSidecarEvent.ACTIVATED, activated.event)
        assertEquals(ProgramOrderSide.SELL, activated.state.suspendedProgramSide)
    }

    @Test
    fun sidecarNoticeCancelsOnRecoveryAndActivationReleasesOnceWithoutRetrigger() {
        val initial = TradingProtectionEngine.initialKrxSidecar(Market.KOSPI, date)
        val notice = TradingProtectionEngine.evaluateKrxSidecar(initial, sidecarObservation())
        assertEquals(KrxSidecarEvent.NOTICE_STARTED, notice.event)
        val recovered = TradingProtectionEngine.evaluateKrxSidecar(
            notice.state,
            sidecarObservation(observedAt = time + 30.seconds, futuresChangeRate = 0.049),
        )
        assertEquals(KrxSidecarEvent.NOTICE_CANCELLED, recovered.event)

        val activated = TradingProtectionEngine.evaluateKrxSidecar(
            recovered.state,
            sidecarObservation(
                observedAt = time + 1.minutes,
                conditionSatisfiedSince = time,
            ),
        ).state
        val released = TradingProtectionEngine.evaluateKrxSidecar(
            activated,
            sidecarObservation(
                observedAt = time + 6.minutes,
                futuresChangeRate = -0.06,
            ),
        )
        assertEquals(KrxSidecarReleaseReason.FIVE_MINUTES_ELAPSED, released.state.releaseReason)
        assertEquals(KrxSidecarPhase.FINISHED_FOR_DAY, released.state.phase)

        val opposite = TradingProtectionEngine.evaluateKrxSidecar(
            released.state,
            sidecarObservation(
                observedAt = time + 8.minutes,
                futuresChangeRate = -0.06,
                conditionSatisfiedSince = time + 6.minutes,
            ),
        )
        assertEquals(KrxSidecarPhase.FINISHED_FOR_DAY, opposite.state.phase)
        assertEquals(KrxSidecarEvent.NONE, opposite.event)
    }

    @Test
    fun sidecarDefersReleaseUntilCircuitBreakerResumption() {
        val active = TradingProtectionEngine.evaluateKrxSidecar(
            TradingProtectionEngine.initialKrxSidecar(Market.KOSPI, date),
            sidecarObservation(conditionSatisfiedSince = time - 1.minutes),
        ).state
        val cbTakesOver = TradingProtectionEngine.evaluateKrxSidecar(
            active,
            sidecarObservation(
                observedAt = time + 1.minutes,
                circuitBreakerPhase = KrxCircuitBreakerPhase.HALTED,
            ),
        )
        assertEquals(KrxSidecarEvent.CIRCUIT_BREAKER_TAKES_PRECEDENCE, cbTakesOver.event)
        assertTrue(cbTakesOver.state.releaseOnCircuitBreakerResume)

        val resumed = TradingProtectionEngine.evaluateKrxSidecar(
            cbTakesOver.state,
            sidecarObservation(
                observedAt = time + 31.minutes,
                circuitBreakerPhase = KrxCircuitBreakerPhase.NORMAL,
            ),
        )
        assertEquals(KrxSidecarEvent.RELEASED, resumed.event)
        assertEquals(KrxSidecarReleaseReason.CIRCUIT_BREAKER_RESUMPTION, resumed.state.releaseReason)
    }

    @Test
    fun krxViUsesProductAndSessionRatesIncludingExpirationException() {
        assertEquals(
            0.03,
            TradingProtectionEngine.krxViRate(
                KrxViKind.DYNAMIC,
                KrxViProductClass.KOSPI200_CONSTITUENT,
                KrxViSession.CONTINUOUS_AUCTION,
            ),
        )
        assertEquals(
            0.04,
            TradingProtectionEngine.krxViRate(
                KrxViKind.DYNAMIC,
                KrxViProductClass.OTHER_ETP,
                KrxViSession.CLOSING_CALL_AUCTION,
            ),
        )
        assertEquals(
            0.01,
            TradingProtectionEngine.krxViRate(
                KrxViKind.DYNAMIC,
                KrxViProductClass.OTHER_EQUITY,
                KrxViSession.CLOSING_CALL_AUCTION,
                isEquityDerivativesExpirationClosingAuction = true,
            ),
        )
        assertEquals(
            0.10,
            TradingProtectionEngine.krxViRate(
                KrxViKind.STATIC,
                KrxViProductClass.OTHER_EQUITY,
                KrxViSession.OPENING_CALL_AUCTION,
            ),
        )
        assertNull(
            TradingProtectionEngine.krxViRate(
                KrxViKind.STATIC,
                KrxViProductClass.OTHER_EQUITY,
                KrxViSession.AFTER_HOURS_PERIODIC_CALL_AUCTION,
            ),
        )
    }

    @Test
    fun krxViRejectsTriggeringQuotationRunsTwoMinuteAuctionAndCanRepeat() {
        val initial = TradingProtectionEngine.initialKrxVi("005930", Market.KOSPI)
        val first = TradingProtectionEngine.evaluateKrxVi(
            initial,
            viObservation(potentialPrice = 103.0),
        )
        assertEquals(KrxViEvent.TRIGGERED, first.event)
        assertEquals(KrxViPhase.CALL_AUCTION, first.state.phase)
        assertEquals(
            TriggeringQuotationDisposition.NOT_EXECUTED_AND_ENTERED_INTO_CALL_AUCTION,
            first.triggeringQuotationDisposition,
        )
        assertEquals(time + 2.minutes, first.state.auctionEndsAt)

        val repeated = TradingProtectionEngine.evaluateKrxVi(
            first.state,
            viObservation(observedAt = time + 2.minutes, potentialPrice = 97.0),
        )
        assertEquals(KrxViEvent.TRIGGERED, repeated.event)
        assertEquals(2, repeated.state.triggerCount)
        assertEquals(time + 4.minutes, repeated.state.auctionEndsAt)
    }

    @Test
    fun krxViExtendsAnExistingPeriodicAuctionByTwoMinutes() {
        val transition = TradingProtectionEngine.evaluateKrxVi(
            TradingProtectionEngine.initialKrxVi("005930", Market.KOSPI),
            viObservation(potentialPrice = 102.0).copy(
                session = KrxViSession.CLOSING_CALL_AUCTION,
                existingCallAuctionEndsAt = time + 5.minutes,
            ),
        )

        assertEquals(KrxViEvent.TRIGGERED, transition.event)
        assertEquals(time + 7.minutes, transition.state.auctionEndsAt)
    }

    @Test
    fun marketCircuitBreakerCancelsActiveVi() {
        val active = TradingProtectionEngine.evaluateKrxVi(
            TradingProtectionEngine.initialKrxVi("005930", Market.KOSPI),
            viObservation(potentialPrice = 103.0),
        ).state
        val cancelled = TradingProtectionEngine.evaluateKrxVi(
            active,
            viObservation(
                observedAt = time + 30.seconds,
                potentialPrice = 103.0,
                circuitBreakerPhase = KrxCircuitBreakerPhase.HALTED,
            ),
        )
        assertEquals(KrxViEvent.CANCELLED_BY_MARKET_CIRCUIT_BREAKER, cancelled.event)
        assertEquals(KrxViPhase.IDLE, cancelled.state.phase)
    }

    @Test
    fun genericInstrumentHaltHasExplicitStartReleaseAndPermissions() {
        val halt = TradingProtectionEngine.startInstrumentTradingHalt(
            stockId = "005930",
            reason = TradingHaltReason.MATERIAL_DISCLOSURE,
            detail = "중요 공시 확인",
            startedAt = time,
            scheduledReleaseAt = time + 30.minutes,
        )
        val snapshot = TradingProtectionSnapshot(instrumentTradingHalts = mapOf("005930" to halt))

        assertFalse(TradingProtectionEngine.isInstrumentHaltActive(halt, time - 1.seconds))
        assertTrue(TradingProtectionEngine.isInstrumentHaltActive(halt, time))
        assertTrue(TradingProtectionEngine.isInstrumentHaltActive(halt, time + 30.minutes - 1.seconds))
        assertFalse(TradingProtectionEngine.isInstrumentHaltActive(halt, time + 30.minutes))

        assertAllowed(snapshot, Market.KOSPI, "005930", TradingProtectionAction.SUBMIT_ORDER, time - 1.seconds)
        assertDenied(snapshot, Market.KOSPI, "005930", TradingProtectionAction.SUBMIT_ORDER, time)
        assertAllowed(snapshot, Market.KOSPI, "005930", TradingProtectionAction.CANCEL_ORDER, time)
        assertDenied(snapshot, Market.KOSPI, "005930", TradingProtectionAction.EXECUTE_TRADE, time)
        assertDenied(
            snapshot,
            Market.KOSPI,
            "005930",
            TradingProtectionAction.SUBMIT_ORDER,
            time + 30.minutes - 1.seconds,
        )
        assertAllowed(snapshot, Market.KOSPI, "005930", TradingProtectionAction.SUBMIT_ORDER, time + 30.minutes)

        val released = TradingProtectionEngine.releaseInstrumentTradingHalt(halt, time + 10.minutes, "공시 확인 완료")
        assertFalse(TradingProtectionEngine.isInstrumentHaltActive(released, time + 11.minutes))
    }

    @Test
    fun investmentAlertUsesListingSuppliedReleaseAndRedesignationWindows() {
        val first = alertDesignation(
            designatedOn = date,
            releaseWindow = TradingDayWindow(LocalDate(2026, 8, 17), LocalDate(2026, 8, 21)),
            redesignationWindow = TradingDayWindow(LocalDate(2026, 8, 22), LocalDate(2026, 9, 4)),
        )
        assertFalse(TradingProtectionEngine.canReleaseInvestmentAlert(first, date))
        assertTrue(TradingProtectionEngine.canReleaseInvestmentAlert(first, LocalDate(2026, 8, 18)))
        assertFailsWith<IllegalArgumentException> {
            TradingProtectionEngine.releaseInvestmentAlert(
                first,
                time + (7 * 24 * 60).minutes,
                LocalDate(2026, 8, 16),
                "기준 해소",
                criteriaCleared = true,
            )
        }
        val released = TradingProtectionEngine.releaseInvestmentAlert(
            first,
            time + (8 * 24 * 60).minutes,
            LocalDate(2026, 8, 18),
            "기준 해소",
            criteriaCleared = true,
        )
        assertEquals(InvestmentAlertStatus.RELEASED, released.status)

        val redesignatedCandidate = alertDesignation(
            designatedOn = LocalDate(2026, 8, 25),
            releaseWindow = TradingDayWindow(LocalDate(2026, 9, 1), LocalDate(2026, 9, 7)),
        )
        assertTrue(TradingProtectionEngine.designateInvestmentAlert(redesignatedCandidate, released).isRedesignation)
    }

    @Test
    fun usMwcbUsesExactCutoffAndFifteenMinuteMinimumBeforeVenueAuctions() {
        val initial = TradingProtectionEngine.initialUsMwcb(date, time)
        val beforeCutoff = TradingProtectionEngine.evaluateUsMwcb(
            initial,
            mwcbObservation(easternTime = LocalTime(15, 24, 59), sp500 = 93.0),
        )
        assertEquals(UsMwcbEvent.LEVEL_1_TRIGGERED, beforeCutoff.event)
        assertEquals(time + 15.minutes, beforeCutoff.state.haltEndsAt)

        val atCutoff = TradingProtectionEngine.evaluateUsMwcb(
            initial,
            mwcbObservation(easternTime = LocalTime(15, 25), sp500 = 93.0),
        )
        assertEquals(UsMwcbPhase.NORMAL, atCutoff.state.phase)

        assertEquals(
            UsMwcbPhase.HALTED,
            TradingProtectionEngine.advanceUsMwcb(beforeCutoff.state, time + 15.minutes - 1.seconds).state.phase,
        )
        val auctions = TradingProtectionEngine.advanceUsMwcb(beforeCutoff.state, time + 15.minutes)
        assertEquals(UsMwcbEvent.REOPENING_AUCTIONS_STARTED, auctions.event)
        assertTrue(auctions.state.venueStatuses.values.all { it.phase == UsMwcbVenuePhase.REOPENING_AUCTION })
    }

    @Test
    fun usMwcbReopensVenuesIndividuallyAndEachLevelOnlyOnce() {
        val levelOne = TradingProtectionEngine.evaluateUsMwcb(
            TradingProtectionEngine.initialUsMwcb(date, time),
            mwcbObservation(sp500 = 93.0),
        ).state
        var state = TradingProtectionEngine.advanceUsMwcb(levelOne, time + 15.minutes).state
        val markets = state.venueStatuses.keys.toList()
        for (market in markets.dropLast(1)) {
            state = TradingProtectionEngine.completeUsMwcbVenueReopening(state, market, time + 16.minutes).state
            assertEquals(UsMwcbPhase.REOPENING_AUCTIONS, state.phase)
        }
        val complete = TradingProtectionEngine.completeUsMwcbVenueReopening(
            state,
            markets.last(),
            time + 17.minutes,
        )
        assertEquals(UsMwcbEvent.ALL_VENUES_REOPENED, complete.event)
        assertEquals(UsMwcbPhase.NORMAL, complete.state.phase)

        val noRepeat = TradingProtectionEngine.evaluateUsMwcb(
            complete.state,
            mwcbObservation(observedAt = time + 18.minutes, sp500 = 92.0),
        )
        assertEquals(UsMwcbPhase.NORMAL, noRepeat.state.phase)

        val levelTwo = TradingProtectionEngine.evaluateUsMwcb(
            noRepeat.state,
            mwcbObservation(observedAt = time + 19.minutes, sp500 = 87.0),
        )
        assertEquals(UsMwcbEvent.LEVEL_2_TRIGGERED, levelTwo.event)
    }

    @Test
    fun usMwcbPermissionReturnsContinuousTradingForAReopenedVenue() {
        val levelOne = TradingProtectionEngine.evaluateUsMwcb(
            TradingProtectionEngine.initialUsMwcb(date, time),
            mwcbObservation(sp500 = 93.0),
        ).state
        val reopening = TradingProtectionEngine.advanceUsMwcb(levelOne, time + 15.minutes).state
        val nasdaqReopened = TradingProtectionEngine.completeUsMwcbVenueReopening(
            reopening,
            Market.NASDAQ,
            time + 16.minutes,
        ).state

        val decision = TradingProtectionEngine.permission(
            TradingProtectionSnapshot(usMarketWideCircuitBreaker = nasdaqReopened),
            TradingProtectionRequest(
                market = Market.NASDAQ,
                action = TradingProtectionAction.EXECUTE_TRADE,
                stockId = "AAPL",
            ),
            time + 16.minutes,
        )

        assertTrue(decision.allowed)
        assertEquals(TradingExecutionMode.CONTINUOUS, decision.executionMode)
        assertNull(decision.controllingRestriction)
    }

    @Test
    fun usMwcbLevelThreeClosesMarketAtAnyRegularSessionTime() {
        val closed = TradingProtectionEngine.evaluateUsMwcb(
            TradingProtectionEngine.initialUsMwcb(date, time),
            mwcbObservation(easternTime = LocalTime(15, 59), sp500 = 80.0),
        )
        assertEquals(UsMwcbEvent.LEVEL_3_TRIGGERED, closed.event)
        assertEquals(UsMwcbPhase.CLOSED_FOR_DAY, closed.state.phase)
        assertTrue(closed.state.venueStatuses.values.all { it.phase == UsMwcbVenuePhase.CLOSED })
    }

    @Test
    fun usLuldBandsFollowTierPriceBucketsAndClosingWindow() {
        val tierOne = TradingProtectionEngine.calculateUsLuldBands(
            UsLuldTier.TIER_1,
            previousClose = 100.0,
            referencePrice = 100.0,
            easternTime = LocalTime(10, 0),
        )
        assertEquals(95.0, tierOne.lower)
        assertEquals(105.0, tierOne.upper)

        val tierOneLate = TradingProtectionEngine.calculateUsLuldBands(
            UsLuldTier.TIER_1,
            100.0,
            100.0,
            LocalTime(15, 35),
        )
        assertEquals(90.0, tierOneLate.lower)
        assertTrue(tierOneLate.doubledForClosingWindow)

        val tierTwoOverThreeLate = TradingProtectionEngine.calculateUsLuldBands(
            UsLuldTier.TIER_2,
            10.0,
            10.0,
            LocalTime(15, 59),
        )
        assertEquals(9.0, tierTwoOverThreeLate.lower)
        assertFalse(tierTwoOverThreeLate.doubledForClosingWindow)

        val subDollarLate = TradingProtectionEngine.calculateUsLuldBands(
            UsLuldTier.TIER_2,
            0.50,
            0.50,
            LocalTime(15, 35),
        )
        assertEquals(0.20, subDollarLate.lower)
        assertEquals(0.80, subDollarLate.upper)
    }

    @Test
    fun usLuldReferenceUpdatesOnlyAfterThirtySecondsAndOnePercentMove() {
        val initial = luldState()
        val tooSoon = TradingProtectionEngine.updateUsLuldReferencePrice(
            initial,
            candidateFiveMinuteMean = 102.0,
            at = time + 29.seconds,
            easternTime = LocalTime(10, 0),
        )
        assertEquals(UsLuldEvent.NONE, tooSoon.event)

        val tooSmall = TradingProtectionEngine.updateUsLuldReferencePrice(
            initial,
            candidateFiveMinuteMean = 100.99,
            at = time + 30.seconds,
            easternTime = LocalTime(10, 0),
        )
        assertEquals(UsLuldEvent.NONE, tooSmall.event)

        val updated = TradingProtectionEngine.updateUsLuldReferencePrice(
            initial,
            candidateFiveMinuteMean = 101.0,
            at = time + 30.seconds,
            easternTime = LocalTime(10, 0),
        )
        assertEquals(UsLuldEvent.REFERENCE_PRICE_UPDATED, updated.event)
        assertEquals(101.0, updated.state.referencePrice)
    }

    @Test
    fun usLuldClearsWithinFifteenSecondsOtherwisePausesAndReopensAfterFiveMinutes() {
        val entered = TradingProtectionEngine.evaluateUsLuld(
            luldState(),
            UsLuldObservation(time, LocalTime(10, 0), UsLuldLimitSide.UPPER),
        ).state
        val cleared = TradingProtectionEngine.evaluateUsLuld(
            entered,
            UsLuldObservation(
                time + 14.seconds,
                LocalTime(10, 0, 14),
                allLimitStateQuotationsCleared = true,
            ),
        )
        assertEquals(UsLuldEvent.LIMIT_STATE_CLEARED, cleared.event)
        assertEquals(UsLuldPhase.NORMAL, cleared.state.phase)

        val enteredAgain = TradingProtectionEngine.evaluateUsLuld(
            cleared.state,
            UsLuldObservation(time + 1.minutes, LocalTime(10, 1), UsLuldLimitSide.LOWER),
        ).state
        val paused = TradingProtectionEngine.evaluateUsLuld(
            enteredAgain,
            UsLuldObservation(
                time + 1.minutes + 15.seconds,
                LocalTime(10, 1, 15),
                allLimitStateQuotationsCleared = true,
            ),
        )
        assertEquals(UsLuldEvent.TRADING_PAUSE_STARTED, paused.event)
        assertEquals(UsLuldPhase.TRADING_PAUSE, paused.state.phase)
        assertEquals(time + 6.minutes + 15.seconds, paused.state.pauseEndsAt)

        val reopening = TradingProtectionEngine.evaluateUsLuld(
            paused.state,
            UsLuldObservation(time + 6.minutes + 15.seconds, LocalTime(10, 6, 15)),
        )
        assertEquals(UsLuldEvent.REOPENING_AUCTION_STARTED, reopening.event)
        val reopened = TradingProtectionEngine.completeUsLuldReopening(
            reopening.state,
            reopeningPrice = 98.0,
            at = time + 7.minutes,
            easternTime = LocalTime(10, 7),
        )
        assertEquals(UsLuldEvent.REOPENED, reopened.event)
        assertEquals(UsLuldPhase.NORMAL, reopened.state.phase)
        assertEquals(98.0, reopened.state.referencePrice)
    }

    @Test
    fun usLuldSupportsOneFiveMinuteExtensionAndCloseWindowDoesNotReopen() {
        val entered = TradingProtectionEngine.evaluateUsLuld(
            luldState(),
            UsLuldObservation(time, LocalTime(15, 49, 30), UsLuldLimitSide.LOWER),
        ).state
        val paused = TradingProtectionEngine.evaluateUsLuld(
            entered,
            UsLuldObservation(time + 15.seconds, LocalTime(15, 49, 45)),
        ).state
        val extended = TradingProtectionEngine.extendUsLuldPause(paused)
        assertEquals(UsLuldEvent.TRADING_PAUSE_EXTENDED, extended.event)
        assertEquals(1, extended.state.pauseExtensionCount)
        assertEquals(requireNotNull(paused.pauseEndsAt) + 5.minutes, extended.state.pauseEndsAt)
        assertFailsWith<IllegalArgumentException> { TradingProtectionEngine.extendUsLuldPause(extended.state) }

        val closeOnly = TradingProtectionEngine.evaluateUsLuld(
            extended.state,
            UsLuldObservation(time + 30.seconds, LocalTime(15, 50)),
        )
        assertEquals(UsLuldEvent.CLOSING_AUCTION_ONLY, closeOnly.event)
        assertEquals(UsLuldPhase.CLOSING_AUCTION_ONLY, closeOnly.state.phase)
        assertEquals(
            UsLuldPhase.CLOSED_FOR_DAY,
            TradingProtectionEngine.closeUsLuldSession(closeOnly.state).state.phase,
        )
    }

    @Test
    fun permissionTreatsDueScheduledInstrumentHaltAsActiveAtExactStart() {
        val halt = TradingProtectionEngine.startInstrumentTradingHalt(
            stockId = "005930",
            reason = TradingHaltReason.REGULATORY_ACTION,
            detail = "최초 투자위험 지정",
            startedAt = time,
            scheduledReleaseAt = time + 6.hours,
        )
        val snapshot = TradingProtectionSnapshot(
            scheduledInstrumentTradingHalts = mapOf("investment-danger" to halt),
        )

        val beforeStart = TradingProtectionEngine.permission(
            snapshot,
            TradingProtectionRequest(Market.KOSPI, TradingProtectionAction.SUBMIT_ORDER, "005930"),
            time - 1.seconds,
        )
        val atStart = TradingProtectionEngine.permission(
            snapshot,
            TradingProtectionRequest(Market.KOSPI, TradingProtectionAction.SUBMIT_ORDER, "005930"),
            time,
        )

        assertTrue(beforeStart.allowed)
        assertFalse(atStart.allowed)
        assertEquals(TradingExecutionMode.PAUSED, atStart.executionMode)
        assertEquals(TradingRestrictionSource.INSTRUMENT_TRADING_HALT, atStart.controllingRestriction?.source)
    }

    @Test
    fun permissionApiAppliesCircuitBreakerBeforeViAndSidecar() {
        val cb = triggerKrxLevelOne(Market.KOSPI)
        val vi = TradingProtectionEngine.evaluateKrxVi(
            TradingProtectionEngine.initialKrxVi("005930", Market.KOSPI),
            viObservation(potentialPrice = 103.0),
        ).state
        val sidecar = TradingProtectionEngine.evaluateKrxSidecar(
            TradingProtectionEngine.initialKrxSidecar(Market.KOSPI, date),
            sidecarObservation(conditionSatisfiedSince = time - 1.minutes),
        ).state
        val snapshot = TradingProtectionSnapshot(
            krxCircuitBreakers = mapOf(Market.KOSPI to cb),
            krxVolatilityInterruptions = mapOf("005930" to vi),
            krxSidecars = mapOf(Market.KOSPI to sidecar),
        )
        val cancel = TradingProtectionEngine.permission(
            snapshot,
            TradingProtectionRequest(
                market = Market.KOSPI,
                stockId = "005930",
                action = TradingProtectionAction.CANCEL_ORDER,
                isProgramOrder = true,
                programOrderSide = ProgramOrderSide.BUY,
            ),
            time,
        )
        assertTrue(cancel.allowed)
        assertEquals(TradingExecutionMode.PAUSED, cancel.executionMode)

        val execution = TradingProtectionEngine.permission(
            snapshot,
            TradingProtectionRequest(
                market = Market.KOSPI,
                stockId = "005930",
                action = TradingProtectionAction.EXECUTE_TRADE,
                isProgramOrder = true,
                programOrderSide = ProgramOrderSide.BUY,
            ),
            time,
        )
        assertFalse(execution.allowed)
        assertEquals(TradingRestrictionSource.KRX_MARKET_CIRCUIT_BREAKER, execution.controllingRestriction?.source)
        assertEquals(1, execution.restrictions.size)
    }

    @Test
    fun permissionApiRestrictsOnlyMatchingSidecarProgramSide() {
        val sidecar = TradingProtectionEngine.evaluateKrxSidecar(
            TradingProtectionEngine.initialKrxSidecar(Market.KOSPI, date),
            sidecarObservation(conditionSatisfiedSince = time - 1.minutes),
        ).state
        val snapshot = TradingProtectionSnapshot(krxSidecars = mapOf(Market.KOSPI to sidecar))
        val matching = TradingProtectionEngine.permission(
            snapshot,
            TradingProtectionRequest(
                Market.KOSPI,
                TradingProtectionAction.PROGRAM_TRADE_FLOW,
                "005930",
                isProgramOrder = true,
                programOrderSide = ProgramOrderSide.BUY,
            ),
            time,
        )
        assertEquals(TradingRestrictionSource.KRX_SIDECAR, matching.controllingRestriction?.source)
        val opposite = TradingProtectionEngine.permission(
            snapshot,
            TradingProtectionRequest(
                Market.KOSPI,
                TradingProtectionAction.PROGRAM_TRADE_FLOW,
                "005930",
                isProgramOrder = true,
                programOrderSide = ProgramOrderSide.SELL,
            ),
            time,
        )
        assertTrue(opposite.allowed)
    }

    @Test
    fun permissionApiEnforcesLuldBandsPauseAndUsMwcbAuctionEligibility() {
        val luld = luldState()
        val luldSnapshot = TradingProtectionSnapshot(usLuldStates = mapOf("AAPL" to luld))
        val outside = TradingProtectionEngine.permission(
            luldSnapshot,
            TradingProtectionRequest(
                Market.NASDAQ,
                TradingProtectionAction.EXECUTE_TRADE,
                "AAPL",
                proposedExecutionPrice = 106.0,
            ),
            time,
        )
        assertEquals(TradingRestrictionSource.US_LIMIT_UP_LIMIT_DOWN, outside.controllingRestriction?.source)
        val inside = TradingProtectionEngine.permission(
            luldSnapshot,
            TradingProtectionRequest(
                Market.NASDAQ,
                TradingProtectionAction.EXECUTE_TRADE,
                "AAPL",
                proposedExecutionPrice = 105.0,
            ),
            time,
        )
        assertTrue(inside.allowed)

        val mwcb = TradingProtectionEngine.evaluateUsMwcb(
            TradingProtectionEngine.initialUsMwcb(date, time),
            mwcbObservation(sp500 = 93.0),
        ).state
        val snapshot = TradingProtectionSnapshot(usMarketWideCircuitBreaker = mwcb)
        assertDenied(snapshot, Market.NASDAQ, "AAPL", TradingProtectionAction.SUBMIT_ORDER, time)
        val auctionEligible = TradingProtectionEngine.permission(
            snapshot,
            TradingProtectionRequest(
                Market.NASDAQ,
                TradingProtectionAction.SUBMIT_ORDER,
                "AAPL",
                isAuctionEligibleOrder = true,
            ),
            time,
        )
        assertTrue(auctionEligible.allowed)
        assertEquals(TradingExecutionMode.PAUSED, auctionEligible.executionMode)
    }

    @Test
    fun investmentAlertAloneIsInformationalAndDoesNotBlockTrading() {
        val alert = alertDesignation(
            designatedOn = date,
            releaseWindow = TradingDayWindow(date, nextDate),
        )
        val decision = TradingProtectionEngine.permission(
            TradingProtectionSnapshot(investmentAlerts = mapOf("005930" to alert)),
            TradingProtectionRequest(Market.KOSPI, TradingProtectionAction.SUBMIT_ORDER, "005930"),
            time,
        )
        assertTrue(decision.allowed)
    }

    private fun triggerKrxLevelOne(
        market: Market,
        indexValue: Double = 91.9,
    ): KrxCircuitBreakerState = TradingProtectionEngine.evaluateKrxCircuitBreaker(
        TradingProtectionEngine.initialKrxCircuitBreaker(market, date),
        cbObservation(
            market = market,
            indexValue = indexValue,
            conditionSatisfiedSince = time - 1.minutes,
        ),
    ).state

    private fun cbObservation(
        market: Market = Market.KOSPI,
        tradingDate: LocalDate = date,
        observedAt: Instant = time,
        indexValue: Double,
        previousClose: Double = 100.0,
        minutesUntilClose: Double = 300.0,
        conditionSatisfiedSince: Instant? = null,
    ) = KrxCircuitBreakerObservation(
        market = market,
        tradingDate = tradingDate,
        observedAt = observedAt,
        indexValue = indexValue,
        previousClose = previousClose,
        minutesUntilClose = minutesUntilClose,
        conditionSatisfiedSince = conditionSatisfiedSince,
    )

    private fun sidecarObservation(
        market: Market = Market.KOSPI,
        tradingDate: LocalDate = date,
        observedAt: Instant = time,
        futuresChangeRate: Double = 0.051,
        spotChangeRate: Double? = if (market == Market.KOSDAQ) 0.031 else null,
        minutesAfterOpen: Double = 60.0,
        minutesUntilClose: Double = 300.0,
        circuitBreakerPhase: KrxCircuitBreakerPhase = KrxCircuitBreakerPhase.NORMAL,
        conditionSatisfiedSince: Instant? = null,
    ) = KrxSidecarObservation(
        market = market,
        tradingDate = tradingDate,
        observedAt = observedAt,
        futuresChangeRate = futuresChangeRate,
        spotIndexChangeRate = spotChangeRate,
        minutesAfterOpen = minutesAfterOpen,
        minutesUntilClose = minutesUntilClose,
        circuitBreakerPhase = circuitBreakerPhase,
        conditionSatisfiedSince = conditionSatisfiedSince,
    )

    private fun viObservation(
        observedAt: Instant = time,
        potentialPrice: Double,
        circuitBreakerPhase: KrxCircuitBreakerPhase = KrxCircuitBreakerPhase.NORMAL,
    ) = KrxViObservation(
        stockId = "005930",
        market = Market.KOSPI,
        observedAt = observedAt,
        kind = KrxViKind.DYNAMIC,
        productClass = KrxViProductClass.KOSPI200_CONSTITUENT,
        session = KrxViSession.CONTINUOUS_AUCTION,
        referencePrice = 100.0,
        potentialExecutionPrice = potentialPrice,
        circuitBreakerPhase = circuitBreakerPhase,
    )

    private fun mwcbObservation(
        observedAt: Instant = time,
        easternTime: LocalTime = LocalTime(10, 0),
        sp500: Double,
    ) = UsMwcbObservation(
        tradingDate = date,
        observedAt = observedAt,
        easternTime = easternTime,
        sp500Value = sp500,
        previousClose = 100.0,
    )

    private fun luldState() = TradingProtectionEngine.initialUsLuld(
        stockId = "AAPL",
        primaryMarket = Market.NASDAQ,
        tradingDate = date,
        tier = UsLuldTier.TIER_1,
        previousClose = 100.0,
        referencePrice = 100.0,
        referencePriceEffectiveAt = time,
        easternTime = LocalTime(10, 0),
    )

    private fun alertDesignation(
        designatedOn: LocalDate,
        releaseWindow: TradingDayWindow,
        redesignationWindow: TradingDayWindow? = null,
    ) = InvestmentAlertDesignation(
        stockId = "005930",
        level = InvestmentAlertLevel.WARNING,
        reasonCodes = setOf("PRICE_SURGE"),
        summary = "주가 급등",
        designatedAt = time,
        designatedOn = designatedOn,
        releaseReviewWindow = releaseWindow,
        redesignationWindow = redesignationWindow,
    )

    private fun assertDenied(
        snapshot: TradingProtectionSnapshot,
        market: Market,
        stockId: String,
        action: TradingProtectionAction,
        at: Instant,
    ): TradingPermissionDecision {
        val decision = TradingProtectionEngine.permission(
            snapshot,
            TradingProtectionRequest(market, action, stockId),
            at,
        )
        assertFalse(decision.allowed)
        assertNotNull(decision.controllingRestriction)
        return decision
    }

    private fun assertAllowed(
        snapshot: TradingProtectionSnapshot,
        market: Market,
        stockId: String,
        action: TradingProtectionAction,
        at: Instant,
    ): TradingPermissionDecision {
        val decision = TradingProtectionEngine.permission(
            snapshot,
            TradingProtectionRequest(market, action, stockId),
            at,
        )
        assertTrue(decision.allowed)
        assertNull(decision.controllingRestriction)
        return decision
    }
}
