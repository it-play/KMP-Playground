package com.amond.kmpbook.domain.simulation

import com.amond.kmpbook.domain.model.InstrumentType
import com.amond.kmpbook.domain.model.ListingLifecycleReason
import com.amond.kmpbook.domain.model.ListingLifecycleStatus
import com.amond.kmpbook.domain.model.ListingRecoveryCondition
import com.amond.kmpbook.domain.model.Market
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ListingRemediationPolicyTest {
    private val startedOn = LocalDate(2026, 8, 10)

    @Test
    fun eventExpiryAloneNeverCreatesAnImmediateCure() {
        val state = state(ListingLifecycleReason.KRX_LISTING_MAINTENANCE)

        val beforeReview = ListingRemediationPolicy.evaluate(
            state = state,
            tradingDate = LocalDate(2026, 8, 14),
            stageStartedOn = startedOn,
            campaignSeed = 1L,
            sourceRiskActive = false,
            alreadyDecidedForStage = false,
        )
        val sourceStillActive = ListingRemediationPolicy.evaluate(
            state = state,
            tradingDate = LocalDate(2026, 8, 15),
            stageStartedOn = startedOn,
            campaignSeed = 1L,
            sourceRiskActive = true,
            alreadyDecidedForStage = false,
        )

        assertEquals(ListingRemediationDecisionStatus.PENDING, beforeReview.status)
        assertEquals(ListingRemediationDecisionStatus.PENDING, sourceStillActive.status)
        assertNull(beforeReview.recoveryCondition)
        assertNull(sourceStillActive.recoveryCondition)
    }

    @Test
    fun sameCampaignAndStageAlwaysProduceTheSameExplicitDecision() {
        val state = state(ListingLifecycleReason.AUDIT_OR_DISCLOSURE_FAILURE)
            .copy(
                status = ListingLifecycleStatus.UNDER_REVIEW,
                cureDeadline = null,
                reviewDeadline = LocalDate(2026, 8, 20),
            )

        val first = ListingRemediationPolicy.evaluate(
            state = state,
            tradingDate = LocalDate(2026, 8, 12),
            stageStartedOn = startedOn,
            campaignSeed = 404L,
            sourceRiskActive = false,
            alreadyDecidedForStage = false,
        )
        val second = ListingRemediationPolicy.evaluate(
            state = state,
            tradingDate = LocalDate(2026, 8, 12),
            stageStartedOn = startedOn,
            campaignSeed = 404L,
            sourceRiskActive = false,
            alreadyDecidedForStage = false,
        )

        assertEquals(first, second)
        if (first.status == ListingRemediationDecisionStatus.CURED) {
            assertEquals(ListingRecoveryCondition.AUDIT_OR_DISCLOSURE_CURED, first.recoveryCondition)
        } else {
            assertEquals(ListingRemediationDecisionStatus.NOT_CURED, first.status)
            assertNull(first.recoveryCondition)
        }
    }

    @Test
    fun bankruptcyAndQuantitativeDeficienciesCannotInventAQualitativeCure() {
        listOf(
            ListingLifecycleReason.BANKRUPTCY_OR_INSOLVENCY,
            ListingLifecycleReason.US_MINIMUM_BID_PRICE,
            ListingLifecycleReason.US_MARKET_CAPITALIZATION,
            ListingLifecycleReason.LOW_TRADING_LIQUIDITY,
        ).forEach { reason ->
            val decision = ListingRemediationPolicy.evaluate(
                state = state(reason),
                tradingDate = LocalDate(2026, 9, 1),
                stageStartedOn = startedOn,
                campaignSeed = 9L,
                sourceRiskActive = false,
                alreadyDecidedForStage = false,
            )
            assertEquals(ListingRemediationDecisionStatus.NOT_APPLICABLE, decision.status)
            assertNull(decision.recoveryCondition)
        }
    }

    @Test
    fun anAlreadyRecordedStageDecisionIsNotRepeated() {
        val decision = ListingRemediationPolicy.evaluate(
            state = state(ListingLifecycleReason.LIQUIDITY_PROVIDER_FAILURE),
            tradingDate = LocalDate(2026, 9, 1),
            stageStartedOn = startedOn,
            campaignSeed = 99L,
            sourceRiskActive = false,
            alreadyDecidedForStage = true,
        )

        assertEquals(ListingRemediationDecisionStatus.PENDING, decision.status)
        assertNull(decision.recoveryCondition)
    }

    private fun state(reason: ListingLifecycleReason) = ListingLifecycleEngine().initialState(
        stockId = "TEST",
        market = Market.KOSPI,
        instrumentType = InstrumentType.STOCK,
    ).copy(
        status = ListingLifecycleStatus.DEFICIENCY_NOTICE,
        activeReason = reason,
        designatedOn = startedOn,
        cureDeadline = LocalDate(2026, 9, 9),
        designationCount = 1,
    )
}
