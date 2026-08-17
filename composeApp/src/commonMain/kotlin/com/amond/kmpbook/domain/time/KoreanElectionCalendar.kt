package com.amond.kmpbook.domain.time

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** 임기만료 선거일을 산출하고 이미 확정된 보궐·조기 선거일을 경계 fixture로 합친다. */
internal object KoreanElectionCalendar {
    fun regularElectionDates(
        year: Int,
        publicHolidays: Set<LocalDate>,
    ): Set<LocalDate> {
        val dates = CONFIRMED_EXTRAORDINARY_ELECTION_DATES
            .filterTo(mutableSetOf()) { date -> date.year == year }
        if (year >= FIRST_LOCAL_ELECTION_YEAR &&
            (year - FIRST_LOCAL_ELECTION_YEAR) % LOCAL_ELECTION_TERM_YEARS == 0
        ) {
            dates += electionDate(
                termExpiresOn = LocalDate(year, 6, 30),
                thresholdDays = 30,
                publicHolidays = publicHolidays,
            )
        }
        if (year >= FIRST_ASSEMBLY_ELECTION_YEAR &&
            (year - FIRST_ASSEMBLY_ELECTION_YEAR) % ASSEMBLY_TERM_YEARS == 0
        ) {
            dates += electionDate(
                termExpiresOn = LocalDate(year, 5, 29),
                thresholdDays = 50,
                publicHolidays = publicHolidays,
            )
        }
        if (year >= FIRST_PRESIDENTIAL_ELECTION_YEAR &&
            (year - FIRST_PRESIDENTIAL_ELECTION_YEAR) % PRESIDENTIAL_TERM_YEARS == 0
        ) {
            dates += electionDate(
                termExpiresOn = LocalDate(year, 6, 3),
                thresholdDays = 70,
                publicHolidays = publicHolidays,
            )
        }
        return dates
    }

    private fun electionDate(
        termExpiresOn: LocalDate,
        thresholdDays: Int,
        publicHolidays: Set<LocalDate>,
    ): LocalDate {
        val threshold = termExpiresOn.minus(thresholdDays, DateTimeUnit.DAY)
        var candidate = threshold.plus(1, DateTimeUnit.DAY)
        while (candidate.dayOfWeek != DayOfWeek.WEDNESDAY) {
            candidate = candidate.plus(1, DateTimeUnit.DAY)
        }
        while (candidateOrNeighbourIsPublicHoliday(candidate, publicHolidays)) {
            candidate = candidate.plus(7, DateTimeUnit.DAY)
        }
        return candidate
    }

    private fun candidateOrNeighbourIsPublicHoliday(
        candidate: LocalDate,
        publicHolidays: Set<LocalDate>,
    ): Boolean = (-1..1).any { offset ->
        val date = candidate.plus(offset, DateTimeUnit.DAY)
        date.dayOfWeek == DayOfWeek.SUNDAY || date in publicHolidays
    }

    private const val FIRST_LOCAL_ELECTION_YEAR: Int = 2026
    private const val LOCAL_ELECTION_TERM_YEARS: Int = 4
    private const val FIRST_ASSEMBLY_ELECTION_YEAR: Int = 2028
    private const val ASSEMBLY_TERM_YEARS: Int = 4
    private const val FIRST_PRESIDENTIAL_ELECTION_YEAR: Int = 2030
    private const val PRESIDENTIAL_TERM_YEARS: Int = 5
    private val CONFIRMED_EXTRAORDINARY_ELECTION_DATES: Set<LocalDate> =
        setOf(LocalDate(2025, 6, 3))
}
