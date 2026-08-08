package com.amond.kmpbook.presentation

import com.amond.kmpbook.domain.model.CorporateActionKind
import com.amond.kmpbook.domain.model.CorporateActionSource
import com.amond.kmpbook.domain.model.InvestmentAlertDesignation
import com.amond.kmpbook.domain.model.InvestmentAlertLevel
import com.amond.kmpbook.domain.model.InvestmentAlertReleaseRule
import com.amond.kmpbook.domain.model.InvestmentAlertStatus
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.PendingCorporateAction
import com.amond.kmpbook.domain.model.TradingDayWindow
import com.amond.kmpbook.domain.model.TurnStep
import com.amond.kmpbook.domain.simulation.MarketMicrostructure
import com.amond.kmpbook.domain.time.GameCalendar
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class KrxCorporateActionAlertAdjustmentTest {
    @Test
    fun forwardSplitAdjustsAlertHistoryAndBaselinesWithoutChangingJudgments() {
        assertCorporateActionAdjustment(
            kind = CorporateActionKind.FORWARD_SPLIT,
            multiplier = 2.0,
            seed = 91_201L,
        )
    }

    @Test
    fun reverseSplitAdjustsAlertHistoryAndBaselinesWithoutChangingJudgments() {
        assertCorporateActionAdjustment(
            kind = CorporateActionKind.REVERSE_SPLIT,
            multiplier = 0.5,
            seed = 91_202L,
        )
    }

    private fun assertCorporateActionAdjustment(
        kind: CorporateActionKind,
        multiplier: Double,
        seed: Long,
    ) {
        val viewModel = SimulatorViewModel().apply { newGame(NewGameOptions(seed = seed)) }
        val stock = viewModel.currentState.stocks.first {
            it.market == Market.KOSPI && !it.isFundLike
        }
        val lastSurveillanceDate = LocalDate(2026, 8, 7)
        val points = surveillancePoints(lastSurveillanceDate)
        val designation = InvestmentAlertDesignation(
            stockId = stock.id,
            level = InvestmentAlertLevel.CAUTION,
            reasonCodes = setOf("WARNING_RELEASE_REDESIGNATION"),
            summary = "투자경고 해제 뒤 재지정 여부를 관찰하고 있어요",
            designatedAt = assertNotNull(
                GameCalendar.regularSessionWindow(stock.market, lastSurveillanceDate),
            ).closesAt,
            designatedOn = lastSurveillanceDate,
            releaseReviewWindow = TradingDayWindow(
                startsOn = lastSurveillanceDate,
                endsOnInclusive = LocalDate(2027, 8, 7),
            ),
            redesignationWindow = TradingDayWindow(
                startsOn = lastSurveillanceDate,
                endsOnInclusive = LocalDate(2026, 8, 31),
            ),
            releaseRule = InvestmentAlertReleaseRule.CAUTION_PRICE_VOLUME,
            preDesignationClose = 15_000.0,
            preReleaseClose = 15_500.0,
            redesignationReleaseRule = InvestmentAlertReleaseRule.WARNING_45_75,
            status = InvestmentAlertStatus.ACTIVE,
        )
        val riseBefore = listOf(3, 5, 15).associateWith { days -> rise(points, days) }
        val releaseBefore = krxInvestmentAlertReleaseCriteriaCleared(designation, points)
        val redesignationBefore = krxWarningRedesignationCriteriaSatisfied(designation, points)
        assertTrue(releaseBefore)
        assertTrue(redesignationBefore)

        val nextTradingDate = LocalDate(2026, 8, 10)
        val opening = assertNotNull(GameCalendar.regularSessionWindow(stock.market, nextTradingDate)).opensAt
        viewModel.setTimeForTesting(opening - 1.hours)
        val before = viewModel.currentState
        val protection = assertNotNull(before.tradingProtectionSnapshot)
        val action = PendingCorporateAction(
            id = "alert-adjustment:${kind.name}",
            stockId = stock.id,
            kind = kind,
            announcedAt = opening - 72.hours,
            effectiveNotBefore = opening,
            quantityMultiplier = multiplier,
            source = CorporateActionSource.CAMPAIGN_RULE,
            rationale = "투자경고 가격기준 기업행동 보정 회귀 테스트",
        )
        assertTrue(
            viewModel.restoreGame(
                before.copy(
                    dailyTradingSurveillance = before.dailyTradingSurveillance +
                        (stock.id to points),
                    tradingProtectionSnapshot = protection.copy(
                        investmentAlerts = protection.investmentAlerts + (stock.id to designation),
                    ),
                    pendingCorporateActions = before.pendingCorporateActions + action,
                ),
            ),
        )

        viewModel.advance(TurnStep.ONE_HOUR)

        val saved = viewModel.currentState
        assertEquals(opening, saved.currentTime)
        assertTrue(saved.pendingCorporateActions.none { it.id == action.id })
        assertEquals(action.id, saved.corporateActionLedger.last().id)
        val adjustedPoints = saved.dailyTradingSurveillance.getValue(stock.id)
        val adjustedDesignation = saved.tradingProtectionSnapshot.investmentAlerts[stock.id]
        assertNotNull(adjustedDesignation)

        points.zip(adjustedPoints).forEach { (original, adjusted) ->
            val expectedClose = MarketMicrostructure.roundNearest(stock, original.close / multiplier)
            assertEquals(original.date, adjusted.date)
            assertEquals(expectedClose, adjusted.close)
            assertEquals(original.close / multiplier, adjusted.close)
            assertEquals(round(original.volume * multiplier).toLong(), adjusted.volume)
            assertEquals(original.turnoverRate, adjusted.turnoverRate)
            assertEquals(original.marketProxyClose, adjusted.marketProxyClose)
            assertEquals(original.krxMarketCapRank, adjusted.krxMarketCapRank)
        }
        assertEquals(
            requireNotNull(designation.preDesignationClose) / multiplier,
            adjustedDesignation.preDesignationClose,
        )
        assertEquals(
            requireNotNull(designation.preReleaseClose) / multiplier,
            adjustedDesignation.preReleaseClose,
        )
        listOf(3, 5, 15).forEach { days ->
            assertEquals(riseBefore.getValue(days), rise(adjustedPoints, days), 1e-12)
        }
        assertEquals(
            releaseBefore,
            krxInvestmentAlertReleaseCriteriaCleared(adjustedDesignation, adjustedPoints),
        )
        assertEquals(
            redesignationBefore,
            krxWarningRedesignationCriteriaSatisfied(adjustedDesignation, adjustedPoints),
        )
        assertTrue(krxInvestmentAlertReleaseCriteriaCleared(adjustedDesignation, adjustedPoints))
        assertTrue(krxWarningRedesignationCriteriaSatisfied(adjustedDesignation, adjustedPoints))

        val restored = SimulatorViewModel()
        assertTrue(restored.restoreGame(saved))
        assertEquals(
            adjustedPoints,
            restored.currentState.dailyTradingSurveillance.getValue(stock.id),
        )
        assertEquals(
            adjustedDesignation,
            restored.currentState.tradingProtectionSnapshot.investmentAlerts[stock.id],
        )
    }

    private fun surveillancePoints(lastDate: LocalDate): List<DailyTradingSurveillancePoint> {
        val dates = buildList {
            var date = lastDate
            while (size < 16) {
                if (date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY) {
                    add(date)
                }
                date = date.plus(-1, DateTimeUnit.DAY)
            }
        }.reversed()
        val closes = listOf(
            11_000.0,
            11_500.0,
            12_000.0,
            12_500.0,
            13_000.0,
            13_500.0,
            14_000.0,
            14_500.0,
            15_000.0,
            14_500.0,
            14_000.0,
            13_500.0,
            13_500.0,
            12_500.0,
            16_000.0,
            18_000.0,
        )
        return dates.mapIndexed { index, date ->
            DailyTradingSurveillancePoint(
                date = date,
                close = closes[index],
                volume = 100_000L + index * 2_000L,
                turnoverRate = 0.010 + index * 0.0001,
                marketProxyClose = 2_650.0 + index * 10.0,
                krxMarketCapRank = 150 + index,
            )
        }
    }

    private fun rise(points: List<DailyTradingSurveillancePoint>, days: Int): Double {
        val latest = points.last().close
        val base = points[points.lastIndex - days].close
        return latest / base - 1.0
    }
}
