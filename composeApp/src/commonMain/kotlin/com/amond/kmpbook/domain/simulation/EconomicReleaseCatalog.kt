package com.amond.kmpbook.domain.simulation

import com.amond.kmpbook.domain.model.EconomicReleaseVintage
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.ScheduleBasis
import com.amond.kmpbook.domain.model.ScheduledEventKind
import com.amond.kmpbook.domain.model.ScheduledEventOccurrence
import com.amond.kmpbook.domain.time.DefaultMarketHolidays
import com.amond.kmpbook.domain.time.GameCalendar
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Pure long-range economic calendar for the 2026–2040 game.
 *
 * Recurrence assumptions intentionally stay simple and auditable: monthly releases use a fixed
 * weekday ordinal, weekly claims use Thursday, GDP uses three consecutive monthly vintages,
 * and the two central banks use eight fixed meeting months. Only dates in [officialDateOverrides]
 * carry the OFFICIAL label; every recurrence-derived date, including a 2026 date, is PROJECTED.
 */
object EconomicReleaseCatalog {
    const val FIRST_YEAR: Int = 2026
    const val LAST_YEAR: Int = 2040
    val OFFICIAL_SOURCE_URLS: Map<String, String> = linkedMapOf(
        "BLS Employment Situation" to "https://www.bls.gov/schedule/news_release/empsit.htm",
        "BLS CPI" to "https://www.bls.gov/schedule/news_release/cpi.htm",
        "BEA release schedule" to "https://www.bea.gov/news/schedule",
        "Federal Reserve FOMC calendar" to "https://www.federalreserve.gov/monetarypolicy/fomccalendars.htm",
        "U.S. Department of Labor weekly claims" to "https://www.dol.gov/ui/data.pdf",
        "KOSTAT 2026 release calendar" to "https://sri.kostat.go.kr/ansk/file/schedule_2026.pdf",
        "Bank of Korea 2026 meeting calendar" to
            "https://www.bok.or.kr/portal/bbs/B0000502/view.do?menuNo=200690&nttId=10094300",
    )

    private val usMarkets = Market.entries.filterTo(linkedSetOf()) { it.isUnitedStates }
    private val koreanMarkets = setOf(Market.KOSPI, Market.KOSDAQ)
    private val usZone = GameCalendar.NEW_YORK_TIME_ZONE
    private val koreaZone = GameCalendar.KOREA_TIME_ZONE
    private val usMorning = LocalTime(8, 30)
    private val koreaMorning = LocalTime(8, 0)
    private val fomcMonths = setOf(1, 3, 4, 6, 7, 9, 11, 12)
    private val bokMonths = setOf(1, 2, 4, 5, 7, 8, 10, 11)

    fun occurrencesForYear(year: Int): List<ScheduledEventOccurrence> {
        if (year !in FIRST_YEAR..LAST_YEAR) return emptyList()
        val occurrences = buildList {
            for (month in 1..12) {
                add(
                    occurrence(
                        seriesId = "us-employment",
                        kind = ScheduledEventKind.US_EMPLOYMENT,
                        title = "미국 고용보고서 발표",
                        description = "비농업 고용과 실업률의 게임 수치가 공개됩니다.",
                        localDate = releaseDate(
                            "us-employment", year, month,
                            nthWeekdayOfMonth(year, month, DayOfWeek.FRIDAY, 1),
                        ),
                        localTime = usMorning,
                        zone = usZone,
                        markets = usMarkets,
                        referencePeriod = previousMonthLabel(year, month),
                    ),
                )
                add(
                    occurrence(
                        seriesId = "us-cpi",
                        kind = ScheduledEventKind.US_CPI,
                        title = "미국 소비자물가 발표",
                        description = "헤드라인과 근원 CPI의 게임 수치가 공개됩니다.",
                        localDate = releaseDate(
                            "us-cpi", year, month,
                            nthWeekdayOfMonth(year, month, DayOfWeek.WEDNESDAY, 2),
                        ),
                        localTime = usMorning,
                        zone = usZone,
                        markets = usMarkets,
                        referencePeriod = previousMonthLabel(year, month),
                    ),
                )
                add(
                    occurrence(
                        seriesId = "us-pce",
                        kind = ScheduledEventKind.US_PCE,
                        title = "미국 PCE 물가 발표",
                        description = "개인소비지출 물가의 게임 수치가 공개됩니다.",
                        localDate = releaseDate(
                            "us-pce", year, month,
                            lastWeekdayOfMonth(year, month, DayOfWeek.FRIDAY),
                        ),
                        localTime = usMorning,
                        zone = usZone,
                        markets = usMarkets,
                        referencePeriod = previousMonthLabel(year, month),
                    ),
                )
                add(
                    occurrence(
                        seriesId = "us-retail-sales",
                        kind = ScheduledEventKind.US_RETAIL_SALES,
                        title = "미국 소매판매 발표",
                        description = "소비 흐름을 보여주는 소매판매 게임 수치가 공개됩니다.",
                        localDate = releaseDate(
                            "us-retail-sales", year, month,
                            nthWeekdayOfMonth(year, month, DayOfWeek.WEDNESDAY, 3),
                        ),
                        localTime = usMorning,
                        zone = usZone,
                        markets = usMarkets,
                        referencePeriod = previousMonthLabel(year, month),
                    ),
                )
                add(
                    occurrence(
                        seriesId = "kr-cpi",
                        kind = ScheduledEventKind.KR_CPI,
                        title = "한국 소비자물가 발표",
                        description = "소비자물가 상승률의 게임 수치가 공개됩니다.",
                        localDate = releaseDate(
                            "kr-cpi", year, month,
                            nthWeekdayOfMonth(year, month, DayOfWeek.TUESDAY, 1),
                        ),
                        localTime = koreaMorning,
                        zone = koreaZone,
                        markets = koreanMarkets,
                        referencePeriod = previousMonthLabel(year, month),
                    ),
                )
                add(
                    occurrence(
                        seriesId = "kr-employment",
                        kind = ScheduledEventKind.KR_EMPLOYMENT,
                        title = "한국 고용동향 발표",
                        description = "취업자와 실업률의 게임 수치가 공개됩니다.",
                        localDate = releaseDate(
                            "kr-employment", year, month,
                            nthWeekdayOfMonth(year, month, DayOfWeek.WEDNESDAY, 2),
                        ),
                        localTime = koreaMorning,
                        zone = koreaZone,
                        markets = koreanMarkets,
                        referencePeriod = previousMonthLabel(year, month),
                    ),
                )

                if (month in fomcMonths) {
                    add(
                        occurrence(
                            seriesId = "us-fomc",
                            kind = ScheduledEventKind.US_FOMC,
                            title = "FOMC 금리 결정",
                            description = "연방기금금리와 정책 문구의 게임 결과가 공개됩니다.",
                            localDate = releaseDate(
                                "us-fomc", year, month,
                                nthWeekdayOfMonth(year, month, DayOfWeek.WEDNESDAY, 3),
                            ),
                            localTime = LocalTime(14, 0),
                            zone = usZone,
                            markets = usMarkets,
                            referencePeriod = "${year}년 ${month}월 회의",
                        ),
                    )
                }
                if (month in bokMonths) {
                    add(
                        occurrence(
                            seriesId = "kr-bok",
                            kind = ScheduledEventKind.KR_BOK,
                            title = "한국은행 기준금리 결정",
                            description = "기준금리와 통화정책 방향의 게임 결과가 공개됩니다.",
                            localDate = releaseDate(
                                "kr-bok", year, month,
                                nthWeekdayOfMonth(year, month, DayOfWeek.THURSDAY, 2),
                            ),
                            localTime = LocalTime(10, 0),
                            zone = koreaZone,
                            markets = koreanMarkets,
                            referencePeriod = "${year}년 ${month}월 회의",
                        ),
                    )
                }
                if (month % 3 == 1) {
                    add(
                        occurrence(
                            seriesId = "kr-gdp",
                            kind = ScheduledEventKind.KR_GDP,
                            title = "한국 GDP 발표",
                            description = "분기 실질 국내총생산의 게임 수치가 공개됩니다.",
                            localDate = releaseDate(
                                "kr-gdp", year, month,
                                nthWeekdayOfMonth(year, month, DayOfWeek.THURSDAY, 4),
                            ),
                            localTime = koreaMorning,
                            zone = koreaZone,
                            markets = koreanMarkets,
                            referencePeriod = previousQuarterLabel(year, month),
                        ),
                    )
                }

                val vintage = when ((month - 1) % 3) {
                    0 -> EconomicReleaseVintage.ADVANCE
                    1 -> EconomicReleaseVintage.SECOND
                    else -> EconomicReleaseVintage.THIRD
                }
                add(
                    occurrence(
                        seriesId = "us-gdp-${vintage.name.lowercase()}",
                        kind = ScheduledEventKind.US_GDP,
                        title = "미국 GDP ${vintage.displayName} 발표",
                        description = "분기 실질 국내총생산 ${vintage.displayName} 게임 수치가 공개됩니다.",
                        localDate = releaseDate(
                            "us-gdp-${vintage.name.lowercase()}", year, month,
                            nthWeekdayOfMonth(year, month, DayOfWeek.THURSDAY, 4),
                        ),
                        localTime = usMorning,
                        zone = usZone,
                        markets = usMarkets,
                        referencePeriod = previousQuarterLabel(year, month),
                        vintage = vintage,
                    ),
                )
            }

            var date = LocalDate(year, 1, 1)
            val finalDate = LocalDate(year, 12, 31)
            while (date <= finalDate) {
                if (date.dayOfWeek == DayOfWeek.THURSDAY) {
                    add(
                        occurrence(
                            seriesId = "us-weekly-claims",
                            kind = ScheduledEventKind.US_WEEKLY_CLAIMS,
                            title = "미국 주간 신규 실업수당 청구 발표",
                            description = "신규 실업수당 청구 건수의 게임 수치가 공개됩니다.",
                            localDate = adjustProjectedReleaseDate(date, Market.NASDAQ),
                            localTime = usMorning,
                            zone = usZone,
                            markets = usMarkets,
                            referencePeriod = "${date} 주간",
                        ),
                    )
                }
                date = date.plus(1, DateTimeUnit.DAY)
            }
        }.sortedWith(compareBy(ScheduledEventOccurrence::scheduledAt, ScheduledEventOccurrence::id))

        check(occurrences.map(ScheduledEventOccurrence::id).distinct().size == occurrences.size)
        return occurrences
    }

    /** Returns every release in the half-open interval [from, to). */
    fun occurrencesBetween(from: Instant, to: Instant): List<ScheduledEventOccurrence> {
        require(to >= from) { "Scheduled event interval cannot run backwards" }
        if (from == to) return emptyList()
        val firstYear = (from.toLocalDateTime(koreaZone).year - 1).coerceAtLeast(FIRST_YEAR)
        val lastYear = (to.toLocalDateTime(koreaZone).year + 1).coerceAtMost(LAST_YEAR)
        if (firstYear > lastYear) return emptyList()
        return (firstYear..lastYear)
            .flatMap(::occurrencesForYear)
            .filter { it.scheduledAt >= from && it.scheduledAt < to }
            .sortedWith(compareBy(ScheduledEventOccurrence::scheduledAt, ScheduledEventOccurrence::id))
    }

    fun upcoming(from: Instant, limit: Int = 12): List<ScheduledEventOccurrence> {
        require(limit >= 0)
        if (limit == 0 || from > GameCalendar.endInstant) return emptyList()
        val startYear = minOf(
            from.toLocalDateTime(koreaZone).year,
            from.toLocalDateTime(usZone).year,
        ).coerceAtLeast(FIRST_YEAR)
        val result = mutableListOf<ScheduledEventOccurrence>()
        for (year in startYear..LAST_YEAR) {
            result += occurrencesForYear(year).filter {
                it.scheduledAt >= from && it.scheduledAt <= GameCalendar.endInstant
            }
            result.sortWith(compareBy(ScheduledEventOccurrence::scheduledAt, ScheduledEventOccurrence::id))
            if (result.size >= limit) return result.take(limit)
        }
        return result.take(limit)
    }

    /** Domain-friendly alias used by callers that treat calendar rows as events. */
    fun eventsBetween(from: Instant, to: Instant): List<ScheduledEventOccurrence> =
        occurrencesBetween(from, to)

    private fun occurrence(
        seriesId: String,
        kind: ScheduledEventKind,
        title: String,
        description: String,
        localDate: LocalDate,
        localTime: LocalTime,
        zone: TimeZone,
        markets: Set<Market>,
        referencePeriod: String,
        vintage: EconomicReleaseVintage? = null,
    ): ScheduledEventOccurrence {
        val vintageSuffix = vintage?.let { ":${it.name.lowercase()}" }.orEmpty()
        return ScheduledEventOccurrence(
            id = "${ScheduledEventOccurrence.ID_PREFIX}economic:$seriesId:$localDate$vintageSuffix",
            seriesId = seriesId,
            kind = kind,
            title = title,
            description = description,
            scheduledAt = LocalDateTime(localDate, localTime).toInstant(zone),
            timeZoneId = zone.id,
            scheduleBasis = if (localDate in officialDateOverrides[seriesId].orEmpty()) {
                ScheduleBasis.OFFICIAL
            } else {
                ScheduleBasis.PROJECTED
            },
            referencePeriod = referencePeriod,
            vintage = vintage,
            affectedMarkets = markets,
        )
    }

    private fun previousMonthLabel(year: Int, month: Int): String = if (month == 1) {
        "${year - 1}년 12월"
    } else {
        "${year}년 ${month - 1}월"
    }

    private fun previousQuarterLabel(year: Int, releaseMonth: Int): String {
        val quarter = (releaseMonth - 1) / 3
        return if (quarter == 0) "${year - 1}년 4분기" else "${year}년 ${quarter}분기"
    }

    private fun releaseDate(seriesId: String, year: Int, month: Int, projected: LocalDate): LocalDate {
        val official = officialDateOverrides[seriesId]
            ?.firstOrNull { it.year == year && it.month.ordinal + 1 == month }
        val referenceMarket = if (seriesId.startsWith("kr-")) Market.KOSPI else Market.NASDAQ
        return official ?: adjustProjectedReleaseDate(projected, referenceMarket)
    }

    /** Projected releases move to the preceding exchange business day when their rule hits a closure. */
    private fun adjustProjectedReleaseDate(projected: LocalDate, market: Market): LocalDate {
        var date = projected
        while (
            GameCalendar.isWeekend(date) ||
            date in if (date.year in FIRST_YEAR..LAST_YEAR) {
                DefaultMarketHolidays.closedDates(market, date.year)
            } else {
                emptySet()
            }
        ) {
            date = date.minus(1, DateTimeUnit.DAY)
        }
        return date
    }

    /**
     * Explicit published-date fixtures, verified 2026-08-07 against the BLS and KOSTAT 2026
     * release calendars, BEA release schedule, Federal Reserve FOMC calendar, and Bank of Korea
     * monetary-policy meeting notices. Rules outside this table stay projected.
     */
    private val officialDateOverrides: Map<String, Set<LocalDate>> = mapOf(
        "us-employment" to dates2026("08-07", "09-04", "10-02", "11-06", "12-04"),
        "us-cpi" to dates2026("08-12", "09-11", "10-14", "11-10", "12-10"),
        "us-pce" to dates2026("08-26", "09-30", "10-29", "11-25", "12-23"),
        "us-gdp-second" to dates2026("08-26", "11-25"),
        "us-gdp-third" to dates2026("09-30", "12-23"),
        "us-gdp-advance" to dates2026("10-29"),
        "us-fomc" to dates2026(
            "01-28", "03-18", "04-29", "06-17", "07-29", "09-16", "10-28", "12-09",
        ),
        "kr-cpi" to dates2026("09-02", "10-02", "11-03", "12-02"),
        "kr-employment" to dates2026("09-09", "10-16", "11-11", "12-16"),
        "kr-bok" to dates2026("08-27", "10-22", "11-26"),
    )

    private fun dates2026(vararg monthDays: String): Set<LocalDate> = monthDays.mapTo(mutableSetOf()) {
        val month = it.substringBefore('-').toInt()
        val day = it.substringAfter('-').toInt()
        LocalDate(2026, month, day)
    }
}

internal fun nthWeekdayOfMonth(
    year: Int,
    month: Int,
    dayOfWeek: DayOfWeek,
    ordinal: Int,
): LocalDate {
    require(ordinal in 1..5)
    val first = LocalDate(year, month, 1)
    val offset = (dayOfWeek.ordinal - first.dayOfWeek.ordinal + 7) % 7
    val result = first.plus(offset + (ordinal - 1) * 7, DateTimeUnit.DAY)
    require(result.month.ordinal + 1 == month) { "Month does not contain the requested weekday ordinal" }
    return result
}

internal fun lastWeekdayOfMonth(year: Int, month: Int, dayOfWeek: DayOfWeek): LocalDate {
    var date = LocalDate(year, month, daysInMonth(year, month))
    while (date.dayOfWeek != dayOfWeek) date = date.plus(-1, DateTimeUnit.DAY)
    return date
}

internal fun nextWeekday(date: LocalDate): LocalDate {
    var result = date
    while (result.dayOfWeek == DayOfWeek.SATURDAY || result.dayOfWeek == DayOfWeek.SUNDAY) {
        result = result.plus(1, DateTimeUnit.DAY)
    }
    return result
}

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if (isLeapYear(year)) 29 else 28
    else -> error("Invalid month $month")
}

private fun isLeapYear(year: Int): Boolean = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
