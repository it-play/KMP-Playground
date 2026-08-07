package com.amond.kmpbook.domain.simulation

import com.amond.kmpbook.domain.data.StockCatalog
import com.amond.kmpbook.domain.model.EconomicReleaseVintage
import com.amond.kmpbook.domain.model.ScheduleBasis
import com.amond.kmpbook.domain.model.ScheduledEventKind
import com.amond.kmpbook.domain.time.DefaultMarketHolidays
import com.amond.kmpbook.domain.time.GameCalendar
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ScheduledEventEngineTest {
    @Test
    fun economicCalendarCoversEveryRequiredSeriesAndThreeGdpVintages() {
        val occurrences = EconomicReleaseCatalog.occurrencesForYear(2028)
        val kinds = occurrences.mapTo(mutableSetOf()) { it.kind }

        assertTrue(kinds.containsAll(ScheduledEventKind.entries.filter { it != ScheduledEventKind.EARNINGS }))
        assertEquals(
            setOf(
                EconomicReleaseVintage.ADVANCE,
                EconomicReleaseVintage.SECOND,
                EconomicReleaseVintage.THIRD,
            ),
            occurrences.filter { it.kind == ScheduledEventKind.US_GDP }
                .mapNotNullTo(mutableSetOf()) { it.vintage },
        )
        assertEquals(12, occurrences.count { it.kind == ScheduledEventKind.US_GDP })
        assertEquals(8, occurrences.count { it.kind == ScheduledEventKind.US_FOMC })
        assertEquals(8, occurrences.count { it.kind == ScheduledEventKind.KR_BOK })
        assertTrue(EconomicReleaseCatalog.OFFICIAL_SOURCE_URLS.isNotEmpty())
        assertTrue(EconomicReleaseCatalog.OFFICIAL_SOURCE_URLS.values.all { it.startsWith("https://") })
    }

    @Test
    fun everyCompanyStockHasExactlyFourSeedIndependentEarningsAndEtfsHaveNone() {
        val first = EarningsCalendarCatalog.occurrencesForYear(2031, StockCatalog.all)
        val second = ScheduledEventEngine(99L).occurrencesForYear(2031, StockCatalog.all)
            .filter { it.kind == ScheduledEventKind.EARNINGS }

        assertEquals(552, StockCatalog.all.size)
        assertEquals(44, StockCatalog.stocks.size)
        assertEquals(500, StockCatalog.etfs.size)
        assertEquals(508, StockCatalog.fundLike.size)
        assertEquals(StockCatalog.stocks.size * 4, first.size)
        assertTrue(first.groupingBy { it.affectedStockIds.single() }.eachCount().values.all { it == 4 })
        assertTrue(first.none { occurrence ->
            occurrence.affectedStockIds.any { id -> StockCatalog.findById(id)?.hasCorporateEarnings != true }
        })
        assertEquals(first, second)
        assertEquals(first.map { it.id }.distinct().size, first.size)
    }

    @Test
    fun occurrenceIntervalsAreHalfOpenAndHaveNoArtificialCap() {
        val engine = ScheduledEventEngine(7L)
        val occurrence = engine.upcoming(GameCalendar.startInstant, limit = 1).single()
        val atStart = engine.occurrencesBetween(occurrence.scheduledAt, occurrence.scheduledAt + 1.hours)
        val endingAtStart = engine.occurrencesBetween(occurrence.scheduledAt - 1.hours, occurrence.scheduledAt)

        assertTrue(atStart.any { it.id == occurrence.id })
        assertFalse(endingAtStart.any { it.id == occurrence.id })

        val wholeYear = engine.occurrencesBetween(
            LocalDateTime(2028, 1, 1, 0, 0).toInstant(GameCalendar.KOREA_TIME_ZONE),
            LocalDateTime(2029, 1, 1, 0, 0).toInstant(GameCalendar.KOREA_TIME_ZONE),
        )
        assertTrue(wholeYear.size > 200)
    }

    @Test
    fun scheduleAndIdsIgnoreSeedWhileOutcomeIsOccurrenceKeyed() {
        val firstEngine = ScheduledEventEngine(101L)
        val secondEngine = ScheduledEventEngine(202L)
        val occurrence = firstEngine.upcoming(GameCalendar.startInstant, limit = 1).single()

        assertEquals(
            firstEngine.upcoming(GameCalendar.startInstant, limit = 12),
            secondEngine.upcoming(GameCalendar.startInstant, limit = 12),
        )
        val narrow = firstEngine.generate(occurrence.scheduledAt, occurrence.scheduledAt + 1.hours)
            .emissions.first { it.occurrence.id == occurrence.id }
        val broad = firstEngine.generate(occurrence.scheduledAt - 24.hours, occurrence.scheduledAt + 24.hours)
            .emissions.first { it.occurrence.id == occurrence.id }
        assertEquals(narrow, broad)
        assertEquals(narrow.newsEvent.id, secondEngine.emissionFor(occurrence).newsEvent.id)
        assertNotEquals(narrow.outcome.metrics, secondEngine.outcomeFor(occurrence).metrics)
    }

    @Test
    fun newYorkReleaseTimeStaysAtEightThirtyAcrossDst() {
        val claims = EconomicReleaseCatalog.occurrencesForYear(2027)
            .filter { it.kind == ScheduledEventKind.US_WEEKLY_CLAIMS }
        val winter = claims.first {
            it.scheduledAt.toLocalDateTime(GameCalendar.NEW_YORK_TIME_ZONE).date.month.ordinal + 1 == 1
        }
        val summer = claims.first {
            it.scheduledAt.toLocalDateTime(GameCalendar.NEW_YORK_TIME_ZONE).date.month.ordinal + 1 == 7
        }

        assertEquals(LocalTime(8, 30), winter.scheduledAt.toLocalDateTime(GameCalendar.NEW_YORK_TIME_ZONE).time)
        assertEquals(LocalTime(8, 30), summer.scheduledAt.toLocalDateTime(GameCalendar.NEW_YORK_TIME_ZONE).time)
        assertEquals(13, winter.scheduledAt.toLocalDateTime(TimeZone.UTC).hour)
        assertEquals(12, summer.scheduledAt.toLocalDateTime(TimeZone.UTC).hour)
    }

    @Test
    fun explicitPublishedDatesAreOfficialAndRuleDatesRemainProjected() {
        val events2026 = EconomicReleaseCatalog.occurrencesForYear(2026)
        val septemberFomc = events2026.single {
            it.kind == ScheduledEventKind.US_FOMC &&
                it.scheduledAt.toLocalDateTime(GameCalendar.NEW_YORK_TIME_ZONE).date == LocalDate(2026, 9, 16)
        }
        val projectedClaims = events2026.first { it.kind == ScheduledEventKind.US_WEEKLY_CLAIMS }
        val augustBok = events2026.single {
            it.kind == ScheduledEventKind.KR_BOK &&
                it.scheduledAt.toLocalDateTime(GameCalendar.KOREA_TIME_ZONE).date == LocalDate(2026, 8, 27)
        }

        assertEquals(ScheduleBasis.OFFICIAL, septemberFomc.scheduleBasis)
        assertEquals(ScheduleBasis.OFFICIAL, augustBok.scheduleBasis)
        assertEquals(ScheduleBasis.PROJECTED, projectedClaims.scheduleBasis)
        assertTrue(EconomicReleaseCatalog.occurrencesForYear(2027).all {
            it.scheduleBasis == ScheduleBasis.PROJECTED
        })
    }

    @Test
    fun projectedGdpDoesNotRemainOnThanksgiving() {
        val gdp = EconomicReleaseCatalog.occurrencesForYear(2027).single {
            it.kind == ScheduledEventKind.US_GDP &&
                it.vintage == EconomicReleaseVintage.SECOND &&
                it.scheduledAt.toLocalDateTime(GameCalendar.NEW_YORK_TIME_ZONE).date.month.ordinal + 1 == 11
        }
        val localDate = gdp.scheduledAt.toLocalDateTime(GameCalendar.NEW_YORK_TIME_ZONE).date

        assertEquals(DayOfWeek.WEDNESDAY, localDate.dayOfWeek)
        assertFalse(localDate in DefaultMarketHolidays.closedDates(gdp.affectedMarkets.first(), 2027))
    }

    @Test
    fun premarketAndAfterHoursEarningsCarryShockToNextRegularSession() {
        val engine = ScheduledEventEngine(303L)
        val earnings = EarningsCalendarCatalog.occurrencesForYear(2028)
            .filter { it.affectedMarkets.single().isUnitedStates }
        val premarket = earnings.first {
            it.scheduledAt.toLocalDateTime(GameCalendar.NEW_YORK_TIME_ZONE).time == LocalTime(8, 0)
        }
        val afterHours = earnings.first {
            it.scheduledAt.toLocalDateTime(GameCalendar.NEW_YORK_TIME_ZONE).time == LocalTime(16, 15)
        }
        val premarketEmission = engine.emissionFor(premarket)
        val afterHoursEmission = engine.emissionFor(afterHours)
        val premarketImpactLocal = premarketEmission.impactStartsAt.toLocalDateTime(GameCalendar.NEW_YORK_TIME_ZONE)
        val afterHoursReleaseLocal = afterHours.scheduledAt.toLocalDateTime(GameCalendar.NEW_YORK_TIME_ZONE)
        val afterHoursImpactLocal = afterHoursEmission.impactStartsAt.toLocalDateTime(GameCalendar.NEW_YORK_TIME_ZONE)

        assertEquals(LocalTime(9, 30), premarketImpactLocal.time)
        assertTrue(premarketEmission.impactStartsAt > premarket.scheduledAt)
        assertEquals(LocalTime(9, 30), afterHoursImpactLocal.time)
        assertTrue(afterHoursImpactLocal.date > afterHoursReleaseLocal.date)
        assertTrue(
            engine.impactEventsBetween(
                premarketEmission.impactStartsAt - 1.hours,
                premarketEmission.impactStartsAt + 1.hours,
            ).any { it.id == premarket.id },
        )
    }

    @Test
    fun scheduledOutcomeShowsActualConsensusUnitsAndEarningsHasTwoMetrics() {
        val engine = ScheduledEventEngine(404L)
        val economic = engine.upcoming(GameCalendar.startInstant, limit = 20)
            .first { it.kind != ScheduledEventKind.EARNINGS }
        val earnings = engine.upcoming(GameCalendar.startInstant, limit = 200)
            .first { it.kind == ScheduledEventKind.EARNINGS }
        val economicEmission = engine.emissionFor(economic)
        val earningsEmission = engine.emissionFor(earnings)

        assertTrue(economicEmission.newsEvent.description.contains("실제"))
        assertTrue(economicEmission.newsEvent.description.contains("예상"))
        assertTrue(economicEmission.outcome.metrics.single().unit.isNotBlank())
        assertEquals(listOf("EPS", "매출"), earningsEmission.outcome.metrics.map { it.label })
        assertTrue(earningsEmission.newsEvent.description.contains("EPS 실제"))
        assertTrue(earningsEmission.newsEvent.description.contains("매출 실제"))
    }

    @Test
    fun scheduledDriftPreservesShockAcrossFullEffectWindow() {
        val engine = ScheduledEventEngine(505L)
        val occurrence = engine.upcoming(GameCalendar.startInstant, limit = 200)
            .first { engine.outcomeFor(it).impact.shockReturn != 0.0 }
        val event = engine.emissionFor(occurrence).impactEvent
        val accumulated = EventShockCalculator.returnBetween(event, event.startsAt, event.endsAt)

        assertEquals(event.impact.shockReturn, accumulated, 1e-10)
        assertEquals(event.impact.shockReturn > 0.0, accumulated > 0.0)
    }

    @Test
    fun upcomingIsOrderedBoundedAndStopsAt2040Horizon() {
        val engine = ScheduledEventEngine(606L)
        val preview = engine.upcoming(GameCalendar.startInstant, limit = 12)
        val late = LocalDateTime(2040, 12, 1, 0, 0).toInstant(GameCalendar.KOREA_TIME_ZONE)
        val finalEvents = engine.upcoming(late, limit = 500)

        assertEquals(12, preview.size)
        assertEquals(preview.sortedWith(compareBy({ it.scheduledAt }, { it.id })), preview)
        assertTrue(finalEvents.isNotEmpty())
        assertTrue(finalEvents.all { it.scheduledAt in late..GameCalendar.endInstant })
        assertTrue(EconomicReleaseCatalog.occurrencesForYear(2040).isNotEmpty())
        assertTrue(engine.upcoming(GameCalendar.endInstant + 1.hours).isEmpty())
    }
}
