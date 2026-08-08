package com.amond.kmpbook.domain.simulation

import com.amond.kmpbook.domain.model.DailyListingSurveillanceInput
import com.amond.kmpbook.domain.model.InstrumentType
import com.amond.kmpbook.domain.model.ListingFinalDispositionType
import com.amond.kmpbook.domain.model.ListingLifecycleEventKind
import com.amond.kmpbook.domain.model.ListingLifecycleProfileId
import com.amond.kmpbook.domain.model.ListingLifecycleReason
import com.amond.kmpbook.domain.model.ListingLifecycleState
import com.amond.kmpbook.domain.model.ListingLifecycleStatus
import com.amond.kmpbook.domain.model.ListingRecoveryCondition
import com.amond.kmpbook.domain.model.ListingRiskSeverity
import com.amond.kmpbook.domain.model.ListingRiskTag
import com.amond.kmpbook.domain.model.ListingRuleBasis
import com.amond.kmpbook.domain.model.Market
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ListingLifecycleEngineTest {
    private val engine = ListingLifecycleEngine()
    private val start = LocalDate(2026, 8, 10)

    @Test
    fun krxWarningCanBeCuredAndLaterRedesignatedWithoutLosingHistory() {
        val initial = initial(Market.KOSDAQ, InstrumentType.STOCK)
        val warning = engine.evaluate(
            initial,
            input(
                date = start,
                state = initial,
                tags = setOf(ListingRiskTag.ADMINISTRATIVE_ISSUE),
                severity = ListingRiskSeverity.MODERATE,
            ),
        )

        assertEquals(ListingLifecycleStatus.DEFICIENCY_NOTICE, warning.state.status)
        assertEquals(ListingLifecycleReason.KRX_ADMINISTRATIVE_ISSUE, warning.state.activeReason)
        assertEquals(1, warning.state.designationCount)
        assertTrue(warning.state.isTradable)
        assertTrue(warning.state.isOrderAllowed)
        assertTrue(warning.state.isIndexEligible)
        assertEquals(ListingLifecycleEventKind.DEFICIENCY_DESIGNATED, warning.ledgerEvents.single().kind)

        val cured = engine.evaluate(
            warning.state,
            input(
                date = start.plus(1, DateTimeUnit.DAY),
                state = warning.state,
                recovery = setOf(ListingRecoveryCondition.FINANCIAL_DEFICIENCY_RESOLVED),
            ),
        )
        assertEquals(ListingLifecycleStatus.LISTED, cured.state.status)
        assertNull(cured.state.activeReason)
        assertEquals(1, cured.state.designationCount)
        assertEquals(ListingLifecycleEventKind.DEFICIENCY_CURED, cured.ledgerEvents.single().kind)

        val redesignated = engine.evaluate(
            cured.state,
            input(
                date = start.plus(10, DateTimeUnit.DAY),
                state = cured.state,
                tags = setOf(ListingRiskTag.ADMINISTRATIVE_ISSUE),
            ),
        )
        assertEquals(ListingLifecycleStatus.DEFICIENCY_NOTICE, redesignated.state.status)
        assertEquals(2, redesignated.state.designationCount)
        assertEquals(ListingLifecycleEventKind.DEFICIENCY_REDESIGNATED, redesignated.ledgerEvents.single().kind)
        assertEquals(3L, redesignated.state.ledgerSequence)
    }

    @Test
    fun krxWarningEscalatesThroughSuspendedReviewAndDelistsWorthless() {
        var state = initial(Market.KOSPI, InstrumentType.STOCK)
        state = engine.evaluate(
            state,
            input(
                date = start,
                state = state,
                tags = setOf(ListingRiskTag.LISTING_MAINTENANCE_DEFICIENCY),
                severity = ListingRiskSeverity.HIGH,
            ),
        ).state
        val cureDeadline = assertNotNull(state.cureDeadline)

        val review = engine.evaluate(state, input(cureDeadline, state))
        state = review.state
        assertEquals(ListingLifecycleStatus.UNDER_REVIEW, state.status)
        assertFalse(state.isTradable)
        assertFalse(state.isOrderAllowed)
        assertTrue(state.isIndexEligible)
        assertEquals(ListingLifecycleEventKind.REVIEW_STARTED, review.ledgerEvents.single().kind)

        val suspension = engine.evaluate(state, input(assertNotNull(state.reviewDeadline), state))
        state = suspension.state
        assertEquals(ListingLifecycleStatus.TRADING_SUSPENDED, state.status)
        assertFalse(state.isTradable)
        assertEquals(ListingLifecycleEventKind.TRADING_SUSPENDED, suspension.ledgerEvents.single().kind)

        val scheduled = engine.evaluate(state, input(assertNotNull(state.reviewDeadline), state))
        state = scheduled.state
        assertEquals(ListingLifecycleStatus.DELISTING_SCHEDULED, state.status)
        assertFalse(state.tradingAllowedUntilDelisting)
        assertFalse(state.isTradable)
        assertEquals(ListingLifecycleEventKind.DELISTING_SCHEDULED, scheduled.ledgerEvents.single().kind)

        val delisted = engine.evaluate(state, input(assertNotNull(state.scheduledDelistingOn), state))
        assertEquals(ListingLifecycleStatus.DELISTED, delisted.state.status)
        assertTrue(delisted.state.isTerminal)
        assertFalse(delisted.state.isSettlementPending)
        assertEquals(
            ListingFinalDispositionType.WORTHLESS_DISPOSITION,
            delisted.state.finalDisposition?.type,
        )
        assertEquals(0.0, delisted.state.finalDisposition?.cashPerUnit)
        assertEquals(ListingLifecycleEventKind.DELISTED, delisted.ledgerEvents.single().kind)
    }

    @Test
    fun nasdaqMinimumBidNoticeRemainsTradableAndCuresAfterTenTradingDays() {
        var state = initial(Market.NASDAQ, InstrumentType.STOCK)
        repeat(29) { offset ->
            val result = engine.evaluate(
                state,
                input(start.plus(offset, DateTimeUnit.DAY), state, close = 0.80, marketCap = 80_000_000.0),
            )
            state = result.state
            assertEquals(ListingLifecycleStatus.LISTED, state.status)
            assertTrue(result.ledgerEvents.isEmpty())
        }
        val notice = engine.evaluate(
            state,
            input(start.plus(29, DateTimeUnit.DAY), state, close = 0.80, marketCap = 80_000_000.0),
        )
        state = notice.state
        assertEquals(ListingLifecycleStatus.DEFICIENCY_NOTICE, state.status)
        assertEquals(ListingLifecycleReason.US_MINIMUM_BID_PRICE, state.activeReason)
        assertEquals(start.plus(209, DateTimeUnit.DAY), state.cureDeadline)
        assertTrue(state.isTradable)
        assertTrue(state.isOrderAllowed)

        repeat(9) { offset ->
            state = engine.evaluate(
                state,
                input(start.plus(30 + offset, DateTimeUnit.DAY), state, close = 1.20, marketCap = 80_000_000.0),
            ).state
            assertEquals(ListingLifecycleStatus.DEFICIENCY_NOTICE, state.status)
        }
        val cured = engine.evaluate(
            state,
            input(start.plus(39, DateTimeUnit.DAY), state, close = 1.20, marketCap = 80_000_000.0),
        )
        assertEquals(ListingLifecycleStatus.LISTED, cured.state.status)
        assertEquals(ListingLifecycleEventKind.DEFICIENCY_CURED, cured.ledgerEvents.single().kind)
        assertEquals(1, cured.state.designationCount)
    }

    @Test
    fun usMarketCapDeficiencyExpiresIntoTradableDelistingNoticeThenSuspends() {
        var state = initial(Market.NYSE, InstrumentType.REIT)
        repeat(30) { offset ->
            state = engine.evaluate(
                state,
                input(
                    date = start.plus(offset, DateTimeUnit.DAY),
                    state = state,
                    close = 8.0,
                    marketCap = 10_000_000.0,
                ),
            ).state
        }
        assertEquals(ListingLifecycleStatus.DEFICIENCY_NOTICE, state.status)
        assertEquals(ListingLifecycleReason.US_MARKET_CAPITALIZATION, state.activeReason)

        val expired = engine.evaluate(
            state,
            input(assertNotNull(state.cureDeadline), state, close = 7.0, marketCap = 10_000_000.0),
        )
        state = expired.state
        assertEquals(ListingLifecycleStatus.DELISTING_SCHEDULED, state.status)
        assertTrue(state.tradingAllowedUntilDelisting)
        assertTrue(state.isTradable)
        assertTrue(state.isIndexEligible)

        val otc = engine.evaluate(
            state,
            input(
                date = assertNotNull(state.scheduledDelistingOn),
                state = state,
                close = 6.5,
                marketCap = 9_000_000.0,
                otc = true,
            ),
        )
        assertEquals(ListingLifecycleStatus.DELISTED, otc.state.status)
        assertEquals(ListingFinalDispositionType.OTC_TRANSFER, otc.state.finalDisposition?.type)
        assertFalse(otc.state.isTradable)
    }

    @Test
    fun stochasticUsListingWarningIsATradableNoticeNotAnImmediateSuspension() {
        val initial = initial(Market.NYSE, InstrumentType.STOCK)
        val warning = engine.evaluate(
            initial,
            input(
                date = start,
                state = initial,
                close = 25.0,
                marketCap = 1_000_000_000.0,
                tags = setOf(ListingRiskTag.LISTING_MAINTENANCE_DEFICIENCY),
                severity = ListingRiskSeverity.CRITICAL,
            ),
        )
        assertEquals(ListingLifecycleStatus.DEFICIENCY_NOTICE, warning.state.status)
        assertEquals(ListingLifecycleReason.US_LISTING_MAINTENANCE, warning.state.activeReason)
        assertTrue(warning.state.isTradable)

        val cleared = engine.evaluate(
            warning.state,
            input(
                date = start.plus(1, DateTimeUnit.DAY),
                state = warning.state,
                close = 25.0,
                marketCap = 1_000_000_000.0,
                recovery = setOf(ListingRecoveryCondition.FINANCIAL_DEFICIENCY_RESOLVED),
            ),
        )
        assertEquals(ListingLifecycleStatus.LISTED, cleared.state.status)
    }

    @Test
    fun criticalStockFailureCanEndWorthlessOrTransferToOtc() {
        val krxWorthless = driveCriticalStockToDisposition(
            initial = initial(Market.KOSDAQ, InstrumentType.STOCK),
            otc = false,
        )
        assertEquals(ListingLifecycleStatus.DELISTED, krxWorthless.status)
        assertEquals(ListingFinalDispositionType.WORTHLESS_DISPOSITION, krxWorthless.finalDisposition?.type)

        val usOtc = driveCriticalStockToDisposition(
            initial = initial(Market.NYSE_AMERICAN, InstrumentType.STOCK),
            otc = true,
        )
        assertEquals(ListingLifecycleStatus.DELISTED, usOtc.status)
        assertEquals(ListingFinalDispositionType.OTC_TRANSFER, usOtc.finalDisposition?.type)

        val soldDuringNotice = driveCriticalStockToDisposition(
            initial = initial(Market.KOSPI, InstrumentType.STOCK),
            otc = false,
            disposition = ListingFinalDispositionType.MARKET_SALE,
        )
        assertEquals(ListingLifecycleStatus.DELISTED, soldDuringNotice.status)
        assertEquals(ListingFinalDispositionType.MARKET_SALE, soldDuringNotice.finalDisposition?.type)
    }

    @Test
    fun etnLiquidityProviderFailureCanCureButSevereIndexFailureSuspendsImmediately() {
        val initial = initial(Market.KOSPI, InstrumentType.ETN)
        val lpWarning = engine.evaluate(
            initial,
            input(
                date = start,
                state = initial,
                tags = setOf(ListingRiskTag.LIQUIDITY_PROVIDER_FAILURE),
                severity = ListingRiskSeverity.MODERATE,
            ),
        )
        assertEquals(ListingLifecycleStatus.DEFICIENCY_NOTICE, lpWarning.state.status)
        assertEquals(ListingLifecycleReason.LIQUIDITY_PROVIDER_FAILURE, lpWarning.state.activeReason)

        val lpReplaced = engine.evaluate(
            lpWarning.state,
            input(
                date = start.plus(1, DateTimeUnit.DAY),
                state = lpWarning.state,
                recovery = setOf(ListingRecoveryCondition.LIQUIDITY_PROVIDER_REPLACED),
            ),
        )
        assertEquals(ListingLifecycleStatus.LISTED, lpReplaced.state.status)

        val indexFailure = engine.evaluate(
            lpReplaced.state,
            input(
                date = start.plus(2, DateTimeUnit.DAY),
                state = lpReplaced.state,
                tags = setOf(ListingRiskTag.UNDERLYING_INDEX_UNAVAILABLE),
                severity = ListingRiskSeverity.HIGH,
            ),
        )
        assertEquals(ListingLifecycleStatus.TRADING_SUSPENDED, indexFailure.state.status)
        assertFalse(indexFailure.state.isOrderAllowed)
    }

    @Test
    fun etfLiquidationStopsTradingThenWaitsForCashSettlement() {
        var state = initial(Market.CBOE_BZX, InstrumentType.ETF)
        val lastTradingDate = start.plus(3, DateTimeUnit.DAY)
        val announcement = engine.evaluate(
            state,
            input(
                date = start,
                state = state,
                tags = setOf(ListingRiskTag.ETF_LIQUIDATION_APPROVED),
                severity = ListingRiskSeverity.HIGH,
                scheduledDelisting = lastTradingDate,
            ),
        )
        state = announcement.state
        assertEquals(ListingLifecycleStatus.DELISTING_SCHEDULED, state.status)
        assertTrue(state.isTradable)
        assertTrue(state.isIndexEligible)

        val cashDue = start.plus(10, DateTimeUnit.DAY)
        val liquidation = engine.evaluate(
            state,
            input(
                date = lastTradingDate,
                state = state,
                scheduledSettlement = cashDue,
                disposition = ListingFinalDispositionType.CASH_LIQUIDATION,
                cashPerUnit = 42.75,
            ),
        )
        state = liquidation.state
        assertEquals(ListingLifecycleStatus.LIQUIDATION_PENDING, state.status)
        assertTrue(state.isSettlementPending)
        assertFalse(state.isTradable)
        assertEquals(42.75, state.finalDisposition?.cashPerUnit)
        assertEquals(cashDue, state.finalDisposition?.settlementDueOn)
        assertEquals(ListingLifecycleEventKind.LIQUIDATION_STARTED, liquidation.ledgerEvents.single().kind)

        val stillPending = engine.evaluate(
            state,
            input(cashDue.plus(-1, DateTimeUnit.DAY), state),
        )
        assertEquals(ListingLifecycleStatus.LIQUIDATION_PENDING, stillPending.state.status)
        assertTrue(stillPending.ledgerEvents.isEmpty())

        val paid = engine.evaluate(stillPending.state, input(cashDue, stillPending.state))
        assertEquals(ListingLifecycleStatus.TERMINATED, paid.state.status)
        assertTrue(paid.state.isTerminal)
        assertFalse(paid.state.isSettlementPending)
        assertEquals(ListingLifecycleEventKind.TERMINATED, paid.ledgerEvents.single().kind)
    }

    @Test
    fun etnMaturityEndsAsCashSettlementWhileIssuerFailureCanEndWorthless() {
        var maturity = initial(Market.NYSE_ARCA, InstrumentType.ETN)
        val terminationDate = start.plus(2, DateTimeUnit.DAY)
        maturity = engine.evaluate(
            maturity,
            input(
                date = start,
                state = maturity,
                tags = setOf(ListingRiskTag.ETN_MATURITY_OR_EARLY_REDEMPTION),
                scheduledDelisting = terminationDate,
            ),
        ).state
        maturity = engine.evaluate(
            maturity,
            input(
                date = terminationDate,
                state = maturity,
                scheduledSettlement = terminationDate.plus(2, DateTimeUnit.DAY),
                cashPerUnit = 19.80,
            ),
        ).state
        assertEquals(ListingLifecycleStatus.LIQUIDATION_PENDING, maturity.status)
        assertEquals(ListingFinalDispositionType.CASH_LIQUIDATION, maturity.finalDisposition?.type)
        val matured = engine.evaluate(
            maturity,
            input(assertNotNull(maturity.settlementDueOn), maturity),
        )
        assertEquals(ListingLifecycleStatus.TERMINATED, matured.state.status)

        var failed = initial(Market.CBOE_BZX, InstrumentType.ETN)
        failed = engine.evaluate(
            failed,
            input(
                date = start,
                state = failed,
                tags = setOf(ListingRiskTag.ISSUER_ELIGIBILITY_FAILURE),
                severity = ListingRiskSeverity.CRITICAL,
            ),
        ).state
        assertEquals(ListingLifecycleStatus.TRADING_SUSPENDED, failed.status)
        failed = engine.evaluate(failed, input(assertNotNull(failed.reviewDeadline), failed)).state
        failed = engine.evaluate(
            failed,
            input(
                date = assertNotNull(failed.scheduledDelistingOn),
                state = failed,
                disposition = ListingFinalDispositionType.WORTHLESS_DISPOSITION,
            ),
        ).state
        assertEquals(ListingLifecycleStatus.TERMINATED, failed.status)
        assertEquals(ListingFinalDispositionType.WORTHLESS_DISPOSITION, failed.finalDisposition?.type)
    }

    @Test
    fun orderlyProductTerminationSupersedesEveryRemediationStageOnItsEffectiveDate() {
        assertOrderlyTerminationPriority(
            effectiveOn = start,
            expectedTradingAllowed = false,
        )
    }

    @Test
    fun orderlyProductTerminationAllowsTradingBeforeItsEffectiveDateUnlessTradingIsAlreadySuspended() {
        assertOrderlyTerminationPriority(
            effectiveOn = start.plus(3, DateTimeUnit.DAY),
            expectedTradingAllowed = true,
        )
    }

    @Test
    fun replayAndSameDayRetryAreDeterministic() {
        val initial = initial(Market.NASDAQ, InstrumentType.STOCK)
        val inputs = buildList {
            repeat(30) { offset ->
                add(input(start.plus(offset, DateTimeUnit.DAY), initial, close = 0.75, marketCap = 50_000_000.0))
            }
            repeat(10) { offset ->
                add(input(start.plus(30 + offset, DateTimeUnit.DAY), initial, close = 1.10, marketCap = 50_000_000.0))
            }
        }
        val first = engine.replay(initial, inputs)
        val second = engine.replay(initial, inputs)
        assertEquals(first, second)
        assertEquals(
            listOf(
                ListingLifecycleEventKind.DEFICIENCY_DESIGNATED,
                ListingLifecycleEventKind.DEFICIENCY_CURED,
            ),
            first.ledgerEvents.map { it.kind },
        )
        assertEquals(ListingLifecycleStatus.LISTED, first.state.status)

        val sameDayRetry = engine.evaluate(first.state, inputs.last())
        assertEquals(first.state, sameDayRetry.state)
        assertTrue(sameDayRetry.ledgerEvents.isEmpty())
    }

    @Test
    fun everyMarketAndInstrumentTypeSelectsADeclaredSaveStableProfile() {
        Market.entries.forEach { market ->
            InstrumentType.entries.forEach { type ->
                val state = initial(market, type)
                val profile = ListingLifecyclePolicyCatalog[state.profileId]
                assertTrue(profile.supports(state), "$market/$type profile mismatch")
                assertTrue(profile.officialSourceUrls.all { it.startsWith("https://") })
                if (profile.ruleBasis != ListingRuleBasis.OFFICIAL_PUBLIC_RULE_SUMMARY) {
                    assertTrue(profile.id.name.contains("GAME_APPROXIMATION"))
                    assertFalse(profile.gameApproximationExplanation.isNullOrBlank())
                }
            }
        }
        assertEquals(ListingLifecycleProfileId.entries.toSet(), ListingLifecyclePolicyCatalog.all.keys)
    }

    @Test
    fun earlierOrderlyTerminationPreemptsScheduleWhileLaterNoticeCannotDelayIt() {
        val initial = initial(Market.NASDAQ, InstrumentType.ETN)
        val callDate = start.plus(30, DateTimeUnit.DAY)
        val accelerationDate = start.plus(8, DateTimeUnit.DAY)
        val call = engine.evaluate(
            initial,
            input(
                date = start,
                state = initial,
                tags = setOf(ListingRiskTag.ETN_MATURITY_OR_EARLY_REDEMPTION),
                scheduledDelisting = callDate,
                disposition = ListingFinalDispositionType.CASH_LIQUIDATION,
            ),
        )
        assertEquals(callDate, call.state.scheduledDelistingOn)

        val accelerated = engine.evaluate(
            call.state,
            input(
                date = start.plus(1, DateTimeUnit.DAY),
                state = call.state,
                tags = setOf(ListingRiskTag.ETN_MATURITY_OR_EARLY_REDEMPTION),
                scheduledDelisting = accelerationDate,
                disposition = ListingFinalDispositionType.CASH_LIQUIDATION,
            ),
        )
        assertEquals(accelerationDate, accelerated.state.scheduledDelistingOn)
        assertEquals(ListingLifecycleEventKind.DELISTING_SCHEDULED, accelerated.ledgerEvents.single().kind)

        val laterCall = engine.evaluate(
            accelerated.state,
            input(
                date = start.plus(2, DateTimeUnit.DAY),
                state = accelerated.state,
                tags = setOf(ListingRiskTag.ETN_MATURITY_OR_EARLY_REDEMPTION),
                scheduledDelisting = callDate.plus(1, DateTimeUnit.DAY),
                disposition = ListingFinalDispositionType.CASH_LIQUIDATION,
            ),
        )
        assertEquals(accelerationDate, laterCall.state.scheduledDelistingOn)
        assertTrue(laterCall.ledgerEvents.isEmpty())

        val pending = engine.evaluate(
            laterCall.state,
            input(
                date = accelerationDate,
                state = laterCall.state,
                tags = setOf(ListingRiskTag.ETN_MATURITY_OR_EARLY_REDEMPTION),
                scheduledDelisting = accelerationDate,
                disposition = ListingFinalDispositionType.CASH_LIQUIDATION,
                cashPerUnit = 42.0,
            ),
        )
        assertEquals(ListingLifecycleStatus.LIQUIDATION_PENDING, pending.state.status)
        assertEquals(42.0, pending.state.finalDisposition?.cashPerUnit)
    }

    private fun driveCriticalStockToDisposition(
        initial: ListingLifecycleState,
        otc: Boolean,
        disposition: ListingFinalDispositionType? = null,
    ): ListingLifecycleState {
        var state = engine.evaluate(
            initial,
            input(
                date = start,
                state = initial,
                tags = setOf(ListingRiskTag.BANKRUPTCY_OR_INSOLVENCY),
                severity = ListingRiskSeverity.CRITICAL,
            ),
        ).state
        assertEquals(ListingLifecycleStatus.TRADING_SUSPENDED, state.status)
        state = engine.evaluate(state, input(assertNotNull(state.reviewDeadline), state)).state
        assertEquals(ListingLifecycleStatus.DELISTING_SCHEDULED, state.status)
        return engine.evaluate(
            state,
            input(
                date = assertNotNull(state.scheduledDelistingOn),
                state = state,
                otc = otc,
                disposition = disposition,
            ),
        ).state
    }

    private fun assertOrderlyTerminationPriority(
        effectiveOn: LocalDate,
        expectedTradingAllowed: Boolean,
    ) {
        val priorStatuses = listOf(
            ListingLifecycleStatus.DEFICIENCY_NOTICE,
            ListingLifecycleStatus.UNDER_REVIEW,
            ListingLifecycleStatus.TRADING_SUSPENDED,
        )
        val terminationSignals = listOf(
            Triple(
                InstrumentType.ETF,
                ListingRiskTag.ETF_LIQUIDATION_APPROVED,
                ListingLifecycleReason.ETF_VOLUNTARY_LIQUIDATION,
            ),
            Triple(
                InstrumentType.ETN,
                ListingRiskTag.ETN_MATURITY_OR_EARLY_REDEMPTION,
                ListingLifecycleReason.ETN_MATURITY_OR_EARLY_REDEMPTION,
            ),
        )

        priorStatuses.forEach { priorStatus ->
            terminationSignals.forEach { (instrumentType, signal, expectedReason) ->
                val previous = initial(Market.KOSPI, instrumentType).copy(
                    status = priorStatus,
                    activeReason = ListingLifecycleReason.LOW_TRADING_LIQUIDITY,
                    designatedOn = start.plus(-5, DateTimeUnit.DAY),
                    cureDeadline = if (priorStatus == ListingLifecycleStatus.DEFICIENCY_NOTICE) {
                        start.plus(20, DateTimeUnit.DAY)
                    } else {
                        null
                    },
                    reviewDeadline = if (
                        priorStatus == ListingLifecycleStatus.UNDER_REVIEW ||
                        priorStatus == ListingLifecycleStatus.TRADING_SUSPENDED
                    ) {
                        start.plus(10, DateTimeUnit.DAY)
                    } else {
                        null
                    },
                    tradingAllowedUntilDelisting = priorStatus == ListingLifecycleStatus.DEFICIENCY_NOTICE,
                    lastEvaluatedTradingDate = start.plus(-1, DateTimeUnit.DAY),
                )

                val result = engine.evaluate(
                    previous,
                    input(
                        date = start,
                        state = previous,
                        tags = setOf(signal),
                        scheduledDelisting = effectiveOn,
                    ),
                )

                val context = "$priorStatus/$instrumentType/effectiveOn=$effectiveOn"
                val expectedTradingAllowedForStatus =
                    expectedTradingAllowed && priorStatus != ListingLifecycleStatus.TRADING_SUSPENDED
                assertEquals(ListingLifecycleStatus.DELISTING_SCHEDULED, result.state.status, context)
                assertEquals(expectedReason, result.state.activeReason, context)
                assertEquals(effectiveOn, result.state.scheduledDelistingOn, context)
                assertEquals(
                    expectedTradingAllowedForStatus,
                    result.state.tradingAllowedUntilDelisting,
                    context,
                )
                assertEquals(expectedTradingAllowedForStatus, result.state.isTradable, context)
                assertEquals(1, result.ledgerEvents.size, context)
                assertEquals(ListingLifecycleEventKind.DELISTING_SCHEDULED, result.ledgerEvents.single().kind, context)
                assertEquals(priorStatus, result.ledgerEvents.single().fromStatus, context)
            }
        }
    }

    private fun initial(market: Market, type: InstrumentType): ListingLifecycleState =
        engine.initialState("${market.name}:TEST-${type.name}", market, type)

    private fun input(
        date: LocalDate,
        state: ListingLifecycleState,
        close: Double? = null,
        marketCap: Double? = null,
        tags: Set<ListingRiskTag> = emptySet(),
        severity: ListingRiskSeverity = ListingRiskSeverity.NONE,
        recovery: Set<ListingRecoveryCondition> = emptySet(),
        scheduledDelisting: LocalDate? = null,
        scheduledSettlement: LocalDate? = null,
        disposition: ListingFinalDispositionType? = null,
        otc: Boolean = false,
        cashPerUnit: Double? = null,
    ): DailyListingSurveillanceInput = DailyListingSurveillanceInput(
        stockId = state.stockId,
        tradingDate = date,
        close = close,
        marketCapitalization = marketCap,
        tradedVolume = 100_000L,
        turnoverRate = 0.01,
        riskTags = tags,
        riskSeverity = severity,
        recoveryConditions = recovery,
        scheduledDelistingOn = scheduledDelisting,
        scheduledSettlementOn = scheduledSettlement,
        finalDispositionHint = disposition,
        otcTransferAvailable = otc,
        liquidationCashPerUnit = cashPerUnit,
    )
}
