package com.amond.kmpbook.domain.time

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/** 확정된 KRX 연초 개장식·수능일 지연을 포함한 날짜별 정규장 시각이다. */
internal object KrxSessionCalendar {
    fun regularSessionOpen(date: LocalDate): LocalTime =
        if (date in DELAYED_SESSION_DATES) TEN_AM_OPEN else ORDINARY_OPEN

    fun regularSessionClose(date: LocalDate): LocalTime =
        if (date in CSAT_SESSION_DATES) CSAT_CLOSE else ORDINARY_CLOSE

    private val ORDINARY_OPEN: LocalTime = LocalTime(9, 0)
    private val ORDINARY_CLOSE: LocalTime = LocalTime(15, 30)
    private val TEN_AM_OPEN: LocalTime = LocalTime(10, 0)
    private val CSAT_CLOSE: LocalTime = LocalTime(16, 30)

    private val NEW_YEAR_SESSION_DATES: Set<LocalDate> = setOf(
        LocalDate(2022, 1, 3),
        LocalDate(2023, 1, 2),
        LocalDate(2024, 1, 2),
        LocalDate(2025, 1, 2),
        LocalDate(2026, 1, 2),
    )

    private val CSAT_SESSION_DATES: Set<LocalDate> = setOf(
        LocalDate(2021, 11, 18),
        LocalDate(2022, 11, 17),
        LocalDate(2023, 11, 16),
        LocalDate(2024, 11, 14),
        LocalDate(2025, 11, 13),
    )

    private val DELAYED_SESSION_DATES: Set<LocalDate> =
        NEW_YEAR_SESSION_DATES + CSAT_SESSION_DATES
}
