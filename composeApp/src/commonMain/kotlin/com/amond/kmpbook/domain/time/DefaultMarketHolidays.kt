package com.amond.kmpbook.domain.time

import com.amond.kmpbook.domain.model.market.Market
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * Deterministic full-day exchange calendars for the bundled 2021–2040 history and campaign.
 *
 * Korean statutory and substitute holidays, regular election days, Korean
 * lunisolar holidays, KRX year-end closure, and the NYSE recurring holiday set
 * are computed from their rules. Unscheduled government or exchange closures
 * remain injectable through [GameCalendar].
 */
object DefaultMarketHolidays {
    /** 경계 계산을 포함해 기본 거래·금융 달력이 정본으로 제공되는 연도인지 판정한다. */
    fun supportsYear(year: Int): Boolean = year in CALENDAR_YEARS

    fun closedDates(market: Market, year: Int): Set<LocalDate> {
        require(supportsYear(year)) { "기본 휴장일 달력은 2021~2041년을 지원합니다." }
        return CLOSED_DATES_BY_YEAR.getValue(year).getValue(market)
    }

    fun closedDatesByMarket(year: Int): Map<Market, Set<LocalDate>> {
        require(supportsYear(year)) { "기본 휴장일 달력은 2021~2041년을 지원합니다." }
        return CLOSED_DATES_BY_YEAR.getValue(year)
    }

    /**
     * KSD를 포함한 한국 금융업무 공휴일이다. KRX만 쉬는 연말 폐장일은 포함하지 않는다.
     */
    fun koreanFinancialClosedDates(year: Int): Set<LocalDate> {
        require(supportsYear(year)) { "기본 한국 금융업무 달력은 2021~2041년을 지원합니다." }
        return koreanFinancialClosedDatesForCalendarYear(year)
    }

    /** KOFR 시작 전·캠페인 종료 뒤의 직전 영업일 계산에 필요한 경계연도를 포함한다. */
    internal fun koreanFinancialClosedDatesForCalendarYear(year: Int): Set<LocalDate> {
        require(supportsYear(year)) { "한국 금융업무 달력 범위를 벗어났습니다: $year" }
        return KOREAN_FINANCIAL_CLOSED_DATES_BY_YEAR.getValue(year)
    }

    private fun krx(year: Int): Set<LocalDate> {
        val financialHolidays = koreanFinancialClosedDatesForCalendarYear(year)
        val holidays = financialHolidays.toMutableSet()
        var yearEndClosure = LocalDate(year, 12, 31)
        while (yearEndClosure.dayOfWeek in WEEKEND || yearEndClosure in financialHolidays) {
            yearEndClosure = yearEndClosure.minus(1, DateTimeUnit.DAY)
        }
        holidays += yearEndClosure
        return holidays
    }

    private val CALENDAR_YEARS: IntRange = 2021..2041
    private val WEEKEND: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    /**
     * Callers share these collections, so read-only wrappers keep an unsafe cast from
     * corrupting later calendar lookups. They remain nested because they are an
     * implementation detail of this cache rather than standalone domain types.
     */
    private class CachedSet<E>(private val delegate: Set<E>) : Set<E> by delegate {
        override fun iterator(): Iterator<E> {
            val iterator = delegate.iterator()
            return object : Iterator<E> {
                override fun hasNext(): Boolean = iterator.hasNext()
                override fun next(): E = iterator.next()
            }
        }

        override fun equals(other: Any?): Boolean = delegate == other
        override fun hashCode(): Int = delegate.hashCode()
        override fun toString(): String = delegate.toString()
    }

    private class CachedMap<K, V>(private val delegate: Map<K, V>) : Map<K, V> by delegate {
        override val entries: Set<Map.Entry<K, V>>
            get() = delegate.entries
                .mapTo(linkedSetOf()) { entry -> CachedEntry(entry.key, entry.value) }
        override val keys: Set<K> get() = delegate.keys.toSet()
        override val values: Collection<V> get() = delegate.values.toList()

        override fun equals(other: Any?): Boolean = delegate == other
        override fun hashCode(): Int = delegate.hashCode()
        override fun toString(): String = delegate.toString()
    }

    private class CachedEntry<K, V>(
        override val key: K,
        override val value: V,
    ) : Map.Entry<K, V> {
        override fun equals(other: Any?): Boolean =
            other is Map.Entry<*, *> && key == other.key && value == other.value

        override fun hashCode(): Int = (key?.hashCode() ?: 0) xor (value?.hashCode() ?: 0)
        override fun toString(): String = "$key=$value"
    }

    private val KOREAN_FINANCIAL_CLOSED_DATES_BY_YEAR: Map<Int, Set<LocalDate>> = CachedMap(
        CALENDAR_YEARS.associateWith { year ->
            CachedSet(KoreanPublicHolidayCalendar.closedDates(year))
        },
    )

    private val CLOSED_DATES_BY_YEAR: Map<Int, Map<Market, Set<LocalDate>>> = CachedMap(
        CALENDAR_YEARS.associateWith { year ->
            val koreanDates: Set<LocalDate> = CachedSet(krx(year))
            val unitedStatesDates: Set<LocalDate> = CachedSet(NyseHolidayCalendar.closedDates(year))
            CachedMap(
                Market.entries.associateWith { market ->
                    if (market.isKorean) koreanDates else unitedStatesDates
                },
            )
        },
    )
}
