package com.amond.kmpbook.domain.simulation

import com.amond.kmpbook.domain.data.StockCatalog
import com.amond.kmpbook.domain.model.ScheduleBasis
import com.amond.kmpbook.domain.model.ScheduledEventKind
import com.amond.kmpbook.domain.model.ScheduledEventOccurrence
import com.amond.kmpbook.domain.model.StockDefinition
import com.amond.kmpbook.domain.time.GameCalendar
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Seed-independent quarterly earnings calendar. Every listed stock receives exactly four releases
 * per calendar year in February, May, August, and November. A stable stock/quarter hash spreads
 * releases across weekdays and between pre/after-market slots (07:30/16:00 KST and
 * 08:00/16:15 New York time).
 */
object EarningsCalendarCatalog {
    const val RELEASES_PER_STOCK_PER_YEAR: Int = 4

    private val releaseMonths = listOf(2, 5, 8, 11)

    fun occurrencesForYear(
        year: Int,
        stocks: List<StockDefinition> = StockCatalog.definitions,
    ): List<ScheduledEventOccurrence> {
        if (year !in EconomicReleaseCatalog.FIRST_YEAR..EconomicReleaseCatalog.LAST_YEAR) return emptyList()
        require(stocks.map(StockDefinition::id).distinct().size == stocks.size) {
            "Earnings calendar requires unique stock ids"
        }
        val earningsStocks = stocks.filter(StockDefinition::hasCorporateEarnings)
        val occurrences = earningsStocks.sortedBy(StockDefinition::id).flatMap { stock ->
            releaseMonths.mapIndexed { releaseIndex, month ->
                val fiscal = fiscalPeriod(year, releaseIndex)
                val dayOffset = (
                    DeterministicRandom.stableHash64("${stock.id}:$year:$releaseIndex").toULong() %
                        EARNINGS_DAY_SPAN.toUInt()
                    ).toInt()
                val isBeforeMarket = (
                    DeterministicRandom.stableHash64("${stock.id}:$year:$releaseIndex:session").toULong() and 1uL
                    ) == 0uL
                val localDate = nextWeekday(LocalDate(year, month, FIRST_EARNINGS_DAY + dayOffset))
                val zone = GameCalendar.timeZoneFor(stock.market)
                val localTime = when {
                    stock.market.isKorean && isBeforeMarket -> LocalTime(7, 30)
                    stock.market.isKorean -> LocalTime(16, 0)
                    isBeforeMarket -> LocalTime(8, 0)
                    else -> LocalTime(16, 15)
                }
                val sessionLabel = if (isBeforeMarket) "장전" else "장후"
                ScheduledEventOccurrence(
                    id = "${ScheduledEventOccurrence.ID_PREFIX}earnings:${stock.id}:${fiscal.first}:q${fiscal.second}",
                    seriesId = "earnings:${stock.id}",
                    kind = ScheduledEventKind.EARNINGS,
                    title = "${stock.name} ${fiscal.first}년 ${fiscal.second}분기 실적 발표 · $sessionLabel",
                    description = "${stock.name}의 EPS와 매출 게임 수치가 ${sessionLabel}에 공개됩니다.",
                    scheduledAt = LocalDateTime(localDate, localTime).toInstant(zone),
                    timeZoneId = zone.id,
                    // These are simulator recurrence dates, never issuer-published fixtures.
                    scheduleBasis = ScheduleBasis.PROJECTED,
                    referencePeriod = "${fiscal.first}년 ${fiscal.second}분기",
                    affectedMarkets = setOf(stock.market),
                    affectedStockIds = setOf(stock.id),
                )
            }
        }.sortedWith(compareBy(ScheduledEventOccurrence::scheduledAt, ScheduledEventOccurrence::id))

        check(occurrences.map(ScheduledEventOccurrence::id).distinct().size == occurrences.size)
        check(occurrences.groupingBy { it.affectedStockIds.single() }.eachCount().values.all {
            it == RELEASES_PER_STOCK_PER_YEAR
        })
        return occurrences
    }

    /** Returns every earnings release in the half-open interval [from, to). */
    fun occurrencesBetween(
        from: Instant,
        to: Instant,
        stocks: List<StockDefinition> = StockCatalog.definitions,
    ): List<ScheduledEventOccurrence> {
        require(to >= from) { "Scheduled event interval cannot run backwards" }
        if (from == to) return emptyList()
        val firstYear = (from.toLocalDateTime(GameCalendar.KOREA_TIME_ZONE).year - 1)
            .coerceAtLeast(EconomicReleaseCatalog.FIRST_YEAR)
        val lastYear = (to.toLocalDateTime(GameCalendar.KOREA_TIME_ZONE).year + 1)
            .coerceAtMost(EconomicReleaseCatalog.LAST_YEAR)
        if (firstYear > lastYear) return emptyList()
        return (firstYear..lastYear)
            .flatMap { occurrencesForYear(it, stocks) }
            .filter { it.scheduledAt >= from && it.scheduledAt < to }
            .sortedWith(compareBy(ScheduledEventOccurrence::scheduledAt, ScheduledEventOccurrence::id))
    }

    fun upcoming(
        from: Instant,
        stocks: List<StockDefinition> = StockCatalog.definitions,
        limit: Int = 12,
    ): List<ScheduledEventOccurrence> {
        require(limit >= 0)
        if (limit == 0 || from > GameCalendar.endInstant) return emptyList()
        val startYear = minOf(
            from.toLocalDateTime(GameCalendar.KOREA_TIME_ZONE).year,
            from.toLocalDateTime(GameCalendar.NEW_YORK_TIME_ZONE).year,
        ).coerceAtLeast(EconomicReleaseCatalog.FIRST_YEAR)
        val result = mutableListOf<ScheduledEventOccurrence>()
        for (year in startYear..EconomicReleaseCatalog.LAST_YEAR) {
            result += occurrencesForYear(year, stocks).filter {
                it.scheduledAt >= from && it.scheduledAt <= GameCalendar.endInstant
            }
            result.sortWith(compareBy(ScheduledEventOccurrence::scheduledAt, ScheduledEventOccurrence::id))
            if (result.size >= limit) return result.take(limit)
        }
        return result.take(limit)
    }

    fun eventsBetween(
        from: Instant,
        to: Instant,
        stocks: List<StockDefinition> = StockCatalog.definitions,
    ): List<ScheduledEventOccurrence> = occurrencesBetween(from, to, stocks)

    private fun fiscalPeriod(releaseYear: Int, releaseIndex: Int): Pair<Int, Int> = when (releaseIndex) {
        0 -> (releaseYear - 1) to 4
        1 -> releaseYear to 1
        2 -> releaseYear to 2
        3 -> releaseYear to 3
        else -> error("Invalid quarterly release index $releaseIndex")
    }

    private const val FIRST_EARNINGS_DAY: Int = 5
    private const val EARNINGS_DAY_SPAN: Int = 15
}
