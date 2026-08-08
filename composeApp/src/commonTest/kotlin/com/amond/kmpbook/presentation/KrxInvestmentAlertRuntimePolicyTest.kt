package com.amond.kmpbook.presentation

import com.amond.kmpbook.domain.model.InvestmentAlertDesignation
import com.amond.kmpbook.domain.model.InvestmentAlertLevel
import com.amond.kmpbook.domain.model.InvestmentAlertReleaseRule
import com.amond.kmpbook.domain.model.InvestmentAlertStatus
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.TradingDayWindow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class KrxInvestmentAlertRuntimePolicyTest {
    @Test
    fun warningReleaseUsesSeparateFiveDayFortyFivePercentRule() {
        val start = LocalDate(2026, 8, 10)
        val points = (0..15).map { index ->
            point(start.plus(index, DateTimeUnit.DAY), if (index == 15) 150.0 else 100.0)
        }
        // Five trading observations ago is 100 and the latest is a 15-day high: +50% keeps the
        // warning even though it does not meet the original 5-day +60% designation threshold.
        assertFalse(krxInvestmentAlertReleaseCriteriaCleared(designation(InvestmentAlertLevel.WARNING), points))

        val cleared = points.dropLast(1) + point(start.plus(15, DateTimeUnit.DAY), 144.0)
        // 상승률 두 조건을 벗어나도 최근 15일 최고가이면 아직 해제하지 않는다.
        assertFalse(krxInvestmentAlertReleaseCriteriaCleared(designation(InvestmentAlertLevel.WARNING), cleared))
        val fullyCleared = cleared.toMutableList().also { it[10] = point(it[10].date, 150.0) }
            .dropLast(1) + point(start.plus(15, DateTimeUnit.DAY), 120.0)
        assertTrue(krxInvestmentAlertReleaseCriteriaCleared(designation(InvestmentAlertLevel.WARNING), fullyCleared))
    }

    @Test
    fun dangerReleaseRechecksDangerMaintenanceThresholds() {
        val start = LocalDate(2026, 8, 10)
        val held = (0..15).map { index ->
            val close = when {
                index == 15 -> 150.0
                index >= 12 -> 100.0
                else -> 90.0
            }
            point(start.plus(index, DateTimeUnit.DAY), close)
        }
        assertFalse(krxInvestmentAlertReleaseCriteriaCleared(designation(InvestmentAlertLevel.DANGER), held))

        val cleared = held.toMutableList().also { it[10] = point(it[10].date, 140.0) }
            .dropLast(1) + point(start.plus(15, DateTimeUnit.DAY), 130.0)
        assertTrue(krxInvestmentAlertReleaseCriteriaCleared(designation(InvestmentAlertLevel.DANGER), cleared))
    }

    @Test
    fun warningReleasePersistsItsOriginalReasonGroup() {
        val start = LocalDate(2026, 8, 10)
        val points = (0..15).map { index ->
            val close = when (index) {
                9 -> 160.0 // latest is not a 15-day high
                15 -> 150.0 // +50% over both 5D and 15D bases
                else -> 100.0
            }
            point(start.plus(index, DateTimeUnit.DAY), close)
        }
        assertFalse(
            krxInvestmentAlertReleaseCriteriaCleared(
                designation(
                    InvestmentAlertLevel.WARNING,
                    releaseRule = InvestmentAlertReleaseRule.WARNING_45_75,
                ),
                points,
            ),
        )
        assertTrue(
            krxInvestmentAlertReleaseCriteriaCleared(
                designation(
                    InvestmentAlertLevel.WARNING,
                    releaseRule = InvestmentAlertReleaseRule.WARNING_60_100,
                ),
                points,
            ),
        )
    }

    @Test
    fun mixedWarningGroundUsesThePersistedReleaseRule() {
        val standard = designation(
            InvestmentAlertLevel.WARNING,
            reasonCodes = setOf("WARNING_PRICE_INDEX_5D_60", "ACCOUNT_CONCENTRATION_5D_45"),
            releaseRule = InvestmentAlertReleaseRule.WARNING_60_100,
        )
        val strict = designation(
            InvestmentAlertLevel.WARNING,
            reasonCodes = setOf("WARNING_PRICE_INDEX_5D_60", "ACCOUNT_CONCENTRATION_5D_45"),
            releaseRule = InvestmentAlertReleaseRule.WARNING_45_75,
        )
        assertEquals(InvestmentAlertReleaseRule.WARNING_60_100, krxInvestmentAlertReleaseRule(standard))
        assertEquals(InvestmentAlertReleaseRule.WARNING_45_75, krxInvestmentAlertReleaseRule(strict))
    }

    @Test
    fun warningNoticeUsesRawRiseWhileDesignationAppliesTop100HighAndMarketRelativeGates() {
        val start = LocalDate(2026, 8, 10)
        val points = (0..15).map { index ->
            point(
                date = start.plus(index, DateTimeUnit.DAY),
                close = if (index == 15) 210.0 else 100.0,
                marketProxyClose = 100.0,
                marketCapRank = 101,
            )
        }
        assertEquals(
            setOf("WARNING_NOTICE_3D_100", "WARNING_NOTICE_5D_60", "WARNING_NOTICE_15D_100"),
            krxWarningNoticeReasonCodes(points),
        )
        assertEquals(
            krxWarningNoticeReasonCodes(points),
            krxWarningNoticeReasonCodes(points.withPriorDayRank(100)),
        )
        val noLongerHighest = points.toMutableList().also {
            it[14] = it[14].copy(close = 220.0)
        }
        assertEquals(krxWarningNoticeReasonCodes(points), krxWarningNoticeReasonCodes(noLongerHighest))

        val notice = designation(
            level = InvestmentAlertLevel.CAUTION,
            reasonCodes = krxWarningNoticeReasonCodes(points),
            escalationNoticeOn = points.last().date,
            escalationNoticeReasons = krxWarningNoticeReasonCodes(points),
        )
        assertEquals(
            setOf("WARNING_PRICE_INDEX_3D_100", "WARNING_PRICE_INDEX_5D_60", "WARNING_PRICE_INDEX_15D_100"),
            krxWarningDesignationReasonCodes(notice, points),
        )
        assertTrue(krxWarningDesignationReasonCodes(notice, points.withPriorDayRank(100)).isEmpty())
        assertEquals(
            setOf("WARNING_PRICE_INDEX_3D_100"),
            krxWarningDesignationReasonCodes(notice, points.withPriorDayRank(100), Market.KOSPI),
        )
        assertTrue(krxWarningDesignationReasonCodes(notice, noLongerHighest).isEmpty())

        val restrictedNotice = notice.copy(
            reasonCodes = setOf("WARNING_NOTICE_15D_100"),
            escalationNoticeReasons = setOf("WARNING_NOTICE_15D_100"),
        )
        assertEquals(
            setOf("WARNING_PRICE_INDEX_15D_100"),
            krxWarningDesignationReasonCodes(restrictedNotice, points),
        )
    }

    @Test
    fun dangerNoticeUsesRawRiseButDesignationRequiresHighAndRelativeMarketRise() {
        val start = LocalDate(2026, 8, 10)
        val points = (0..15).map { index ->
            point(
                date = start.plus(index, DateTimeUnit.DAY),
                close = if (index == 15) 210.0 else 100.0,
                marketProxyClose = 100.0,
                marketCapRank = 101,
            )
        }
        val noticeReasons = krxDangerNoticeReasonCodes(points)
        assertEquals(
            setOf("DANGER_NOTICE_3D_45", "DANGER_NOTICE_5D_60", "DANGER_NOTICE_15D_100"),
            noticeReasons,
        )
        val warning = designation(
            level = InvestmentAlertLevel.WARNING,
            reasonCodes = setOf("WARNING_PRICE_INDEX_5D_60"),
            escalationNoticeOn = points.last().date,
            escalationNoticeReasons = noticeReasons,
        )
        assertEquals(
            setOf("DANGER_PRICE_INDEX_3D_45", "DANGER_PRICE_INDEX_5D_60", "DANGER_PRICE_INDEX_15D_100"),
            krxDangerDesignationReasonCodes(warning, points),
        )
        assertEquals(
            setOf("DANGER_PRICE_INDEX_15D_100"),
            krxDangerDesignationReasonCodes(
                warning.copy(escalationNoticeReasons = setOf("DANGER_NOTICE_15D_100")),
                points,
            ),
        )

        val fastMarket = points.mapIndexed { index, value ->
            value.copy(marketProxyClose = if (index == 15) 130.0 else 100.0)
        }
        assertEquals(
            setOf("DANGER_PRICE_INDEX_15D_100"),
            krxDangerDesignationReasonCodes(warning, fastMarket),
        )
    }

    @Test
    fun noticeWindowCountsTenActualObservationsAndRedesignationRequiresAllFourConditions() {
        val start = LocalDate(2026, 8, 10)
        val tenObservations = (0..9).map { index ->
            point(
                start.plus(index, DateTimeUnit.DAY),
                100.0 + index,
                marketProxyClose = 100.0,
                marketCapRank = 101,
            )
        }
        val notice = designation(
            level = InvestmentAlertLevel.CAUTION,
            reasonCodes = setOf("WARNING_NOTICE_5D_60"),
            escalationNoticeOn = start,
            escalationNoticeReasons = setOf("WARNING_NOTICE_5D_60"),
        )
        assertTrue(krxEscalationNoticeJudgmentOpen(notice, tenObservations))
        assertFalse(
            krxEscalationNoticeJudgmentOpen(
                notice,
                tenObservations + point(start.plus(10, DateTimeUnit.DAY), 110.0),
            ),
        )

        val redesignationPoints = listOf(
            point(start, 100.0, marketCapRank = 101),
            point(start.plus(1, DateTimeUnit.DAY), 110.0, marketCapRank = 101),
            point(start.plus(2, DateTimeUnit.DAY), 141.0, marketCapRank = 101),
        )
        val redesignation = designation(
            level = InvestmentAlertLevel.CAUTION,
            reasonCodes = setOf("WARNING_RELEASE_REDESIGNATION"),
            preDesignationClose = 90.0,
            preReleaseClose = 100.0,
            redesignationWindow = TradingDayWindow(start, start.plus(10, DateTimeUnit.DAY)),
            status = InvestmentAlertStatus.RELEASED,
        )
        assertTrue(krxWarningRedesignationCriteriaSatisfied(redesignation, redesignationPoints))
        assertFalse(
            krxWarningRedesignationCriteriaSatisfied(
                redesignation,
                redesignationPoints.withPriorDayRank(100),
            ),
        )
        assertFalse(
            krxWarningRedesignationCriteriaSatisfied(
                redesignation.copy(preReleaseClose = 150.0),
                redesignationPoints,
            ),
        )
    }

    @Test
    fun reviewCountsActualStockTradingObservationsRatherThanCalendarDays() {
        val designationDate = LocalDate(2026, 8, 10)
        val observed = listOf(
            point(designationDate, 100.0),
            point(LocalDate(2026, 8, 11), 101.0),
            // 12th and 13th are absent because the security itself was halted.
            point(LocalDate(2026, 8, 14), 102.0),
        )
        assertEquals(3, krxInvestmentAlertObservedTradingDays(designation(InvestmentAlertLevel.WARNING), observed))
        assertEquals(2, krxInvestmentAlertObservedTradingDays(designation(InvestmentAlertLevel.DANGER), observed))
    }

    @Test
    fun warningAdditionalRiseUsesSecondActualObservationAfterDesignation() {
        val designation = designation(InvestmentAlertLevel.WARNING)
        val points = listOf(
            point(LocalDate(2026, 8, 7), 95.0),
            point(LocalDate(2026, 8, 10), 100.0),
            point(LocalDate(2026, 8, 11), 110.0),
            // 12th was halted; 13th is the second actual trading observation.
            point(LocalDate(2026, 8, 13), 140.0),
        )
        assertEquals(
            LocalDate(2026, 8, 13),
            krxWarningAdditionalRiseEvaluationDate(designation, points),
        )
        assertNull(
            krxWarningAdditionalRiseEvaluationDate(
                designation,
                points + point(LocalDate(2026, 8, 14), 141.0),
            ),
        )
    }

    private fun designation(
        level: InvestmentAlertLevel,
        releaseRule: InvestmentAlertReleaseRule = when (level) {
            InvestmentAlertLevel.CAUTION -> InvestmentAlertReleaseRule.CAUTION_PRICE_VOLUME
            InvestmentAlertLevel.WARNING -> InvestmentAlertReleaseRule.WARNING_60_100
            InvestmentAlertLevel.DANGER -> InvestmentAlertReleaseRule.DANGER_60_100
        },
        reasonCodes: Set<String> = setOf("TEST"),
        preDesignationClose: Double? = null,
        preReleaseClose: Double? = null,
        redesignationWindow: TradingDayWindow? = null,
        escalationNoticeOn: LocalDate? = null,
        escalationNoticeReasons: Set<String> = emptySet(),
        status: InvestmentAlertStatus = InvestmentAlertStatus.ACTIVE,
    ) = InvestmentAlertDesignation(
        stockId = "krx-alert-test",
        level = level,
        reasonCodes = reasonCodes,
        summary = "테스트 시장경보",
        designatedAt = Instant.parse("2026-08-07T06:30:00Z"),
        designatedOn = LocalDate(2026, 8, 10),
        releaseReviewWindow = TradingDayWindow(
            LocalDate(2026, 8, 20),
            LocalDate(2027, 8, 20),
        ),
        redesignationWindow = redesignationWindow,
        releaseRule = releaseRule,
        preDesignationClose = preDesignationClose,
        preReleaseClose = preReleaseClose,
        escalationNoticeOn = escalationNoticeOn,
        escalationNoticeReasons = escalationNoticeReasons,
        status = status,
        releasedAt = Instant.parse("2026-08-10T06:30:00Z").takeIf {
            status == InvestmentAlertStatus.RELEASED
        },
        releasedOn = LocalDate(2026, 8, 10).takeIf { status == InvestmentAlertStatus.RELEASED },
        releaseEffectiveOn = LocalDate(2026, 8, 11).takeIf { status == InvestmentAlertStatus.RELEASED },
        releaseReason = "테스트 해제".takeIf { status == InvestmentAlertStatus.RELEASED },
    )

    private fun point(
        date: LocalDate,
        close: Double,
        volume: Long = 1_000L,
        marketProxyClose: Double? = null,
        marketCapRank: Int? = null,
    ) = DailyTradingSurveillancePoint(
        date,
        close,
        volume,
        turnoverRate = 0.01,
        marketProxyClose = marketProxyClose,
        krxMarketCapRank = marketCapRank,
    )

    private fun List<DailyTradingSurveillancePoint>.withPriorDayRank(rank: Int) =
        toMutableList().also { points ->
            points[points.lastIndex - 1] = points[points.lastIndex - 1].copy(krxMarketCapRank = rank)
        }.toList()
}
