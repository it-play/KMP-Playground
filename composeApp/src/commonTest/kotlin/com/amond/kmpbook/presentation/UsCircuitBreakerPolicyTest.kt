package com.amond.kmpbook.presentation

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UsCircuitBreakerPolicyTest {
    private val date = LocalDate(2026, 8, 7)

    @Test
    fun levelsOneAndTwoTriggerOnceBeforeTheCutoff() {
        val levelOne = decision(UsCircuitBreakerState(), -0.07, LocalTime(14, 0))
        assertEquals(1, levelOne.levelThisHour)
        assertEquals(0.75, levelOne.tradingFractionMultiplier)

        val repeated = decision(levelOne.state, -0.08, LocalTime(14, 30))
        assertEquals(0, repeated.levelThisHour)

        val levelTwo = decision(repeated.state, -0.13, LocalTime(15, 0))
        assertEquals(2, levelTwo.levelThisHour)
        assertEquals(setOf(1, 2), levelTwo.state.triggeredLevels)
    }

    @Test
    fun levelOneAndTwoDoNotHaltAtOrAfterThreeTwentyFive() {
        assertEquals(0, decision(UsCircuitBreakerState(), -0.19, LocalTime(15, 25)).levelThisHour)
    }

    @Test
    fun directThirteenPercentObservationStillPreservesLevelOrder() {
        val firstObservation = decision(UsCircuitBreakerState(), -0.14, LocalTime(13, 0))
        assertEquals(1, firstObservation.levelThisHour)
        assertEquals(setOf(1), firstObservation.state.triggeredLevels)

        val nextObservation = decision(firstObservation.state, -0.14, LocalTime(14, 0))
        assertEquals(2, nextObservation.levelThisHour)
        assertEquals(setOf(1, 2), nextObservation.state.triggeredLevels)
    }

    @Test
    fun levelThreeHaltsTheRestOfTheDayAndResetsNextTradingDate() {
        val levelThree = decision(UsCircuitBreakerState(), -0.20, LocalTime(15, 30))
        assertEquals(3, levelThree.levelThisHour)
        assertTrue(levelThree.state.haltedForDay)
        assertEquals(0.0, levelThree.tradingFractionMultiplier)

        val sameDay = decision(levelThree.state, -0.01, LocalTime(15, 45))
        assertEquals(3, sameDay.levelThisHour)

        val nextDate = LocalDate(2026, 8, 10)
        val reset = UsCircuitBreakerPolicy.evaluate(
            previous = sameDay.state,
            tradingDate = nextDate,
            localTime = LocalTime(10, 0),
            sp500SessionDate = nextDate,
            sp500ChangeRate = 0.0,
            hasCoreTrading = true,
        )
        assertEquals(0, reset.levelThisHour)
        assertFalse(reset.state.haltedForDay)
        assertTrue(reset.state.triggeredLevels.isEmpty())
    }

    @Test
    fun stalePriorSessionSp500ChangeCannotTriggerAtTheNextOpen() {
        val priorDate = LocalDate(2026, 8, 6)
        val result = UsCircuitBreakerPolicy.evaluate(
            previous = UsCircuitBreakerState(tradingDate = date),
            tradingDate = date,
            localTime = LocalTime(9, 0),
            sp500SessionDate = priorDate,
            sp500ChangeRate = -0.25,
            hasCoreTrading = true,
        )

        assertEquals(0, result.levelThisHour)
    }

    private fun decision(
        previous: UsCircuitBreakerState,
        change: Double,
        time: LocalTime,
    ) = UsCircuitBreakerPolicy.evaluate(
        previous = previous,
        tradingDate = date,
        localTime = time,
        sp500SessionDate = date,
        sp500ChangeRate = change,
        hasCoreTrading = true,
    )
}
