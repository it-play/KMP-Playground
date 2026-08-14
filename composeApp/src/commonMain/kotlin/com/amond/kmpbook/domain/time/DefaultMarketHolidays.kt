package com.amond.kmpbook.domain.time

import com.amond.kmpbook.domain.model.market.Market
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Deterministic exchange-holiday pack for the frozen 2026–2040 scenario.
 *
 * It covers recurring KRX and U.S. full-day closures. One-off closures,
 * temporary election holidays, and U.S. early closes stay injectable through
 * [GameCalendar] rather than being guessed. Korean lunar dates are embedded as
 * scenario data so a platform-specific lunar calendar is not required.
 */
object DefaultMarketHolidays {
    fun closedDates(market: Market, year: Int): Set<LocalDate> {
        require(year in SUPPORTED_YEARS) { "기본 휴장일 팩은 2026~2040년을 지원합니다." }
        return CLOSED_DATES_BY_YEAR.getValue(year).getValue(market)
    }

    fun closedDatesByMarket(year: Int): Map<Market, Set<LocalDate>> {
        require(year in SUPPORTED_YEARS) { "기본 휴장일 팩은 2026~2040년을 지원합니다." }
        return CLOSED_DATES_BY_YEAR.getValue(year)
    }

    /**
     * 한국 금융업무 공휴일이다. KRX만 쉬는 연말 최종 거래일은 포함하지 않으므로
     * 결제·지표금리처럼 거래소와 다른 업무 시계를 가진 엔진에서 사용한다.
     */
    fun koreanFinancialClosedDates(year: Int): Set<LocalDate> {
        require(year in SUPPORTED_YEARS) { "기본 한국 금융업무 휴일 팩은 2026~2040년을 지원합니다." }
        return KOREAN_FINANCIAL_CLOSED_DATES_BY_YEAR.getValue(year)
    }

    private fun krx(year: Int): Set<LocalDate> {
        val holidays = koreanFinancialHolidays(year).toMutableSet()

        // KRX만 마지막 평일에 폐장한다. KOFR는 이 날에도 산출될 수 있다.
        holidays += lastWeekdayOfYear(year)
        return holidays
    }

    private fun koreanFinancialHolidays(year: Int): Set<LocalDate> {
        val holidays = mutableSetOf<LocalDate>()

        // Fixed public holidays. New Year's Day and Memorial Day currently do
        // not receive a substitute day under the frozen scenario.
        holidays += LocalDate(year, 1, 1)
        // Korean financial institutions and KOFR input markets close on Labor Day.
        holidays += LocalDate(year, 5, 1)
        holidays += LocalDate(year, 6, 6)
        val substituteEligible = listOf(
            LocalDate(year, 3, 1),
            LocalDate(year, 5, 5),
            LocalDate(year, 8, 15),
            LocalDate(year, 10, 3),
            LocalDate(year, 10, 9),
            LocalDate(year, 12, 25),
        )
        substituteEligible.forEach { addWithKoreanSubstitute(holidays, it) }

        val lunar = koreanLunarAnchors(year)
        val lunarNewYear = (-1..1).map { lunar.newYear.plus(it, DateTimeUnit.DAY) }
        val chuseok = (-1..1).map { lunar.chuseok.plus(it, DateTimeUnit.DAY) }
        holidays += lunarNewYear
        holidays += chuseok
        addGroupSubstitute(holidays, lunarNewYear)
        addGroupSubstitute(holidays, chuseok)
        addWithKoreanSubstitute(holidays, lunar.buddhasBirthday)

        return holidays.filterTo(mutableSetOf()) { it.year == year }
    }

    private fun unitedStates(year: Int): Set<LocalDate> {
        val holidays = mutableSetOf<LocalDate>()
        // Include neighbouring years because a Saturday New Year's Day can be
        // observed on December 31 of the preceding year.
        for (sourceYear in (year - 1)..(year + 1)) {
            holidays += observedUsHoliday(LocalDate(sourceYear, 1, 1))
            holidays += nthWeekday(sourceYear, 1, DayOfWeek.MONDAY, 3) // MLK Day
            holidays += nthWeekday(sourceYear, 2, DayOfWeek.MONDAY, 3) // Presidents' Day
            holidays += easterSunday(sourceYear).minus(2, DateTimeUnit.DAY) // Good Friday
            holidays += lastWeekday(sourceYear, 5, DayOfWeek.MONDAY) // Memorial Day
            holidays += observedUsHoliday(LocalDate(sourceYear, 6, 19)) // Juneteenth
            holidays += observedUsHoliday(LocalDate(sourceYear, 7, 4))
            holidays += nthWeekday(sourceYear, 9, DayOfWeek.MONDAY, 1) // Labor Day
            holidays += nthWeekday(sourceYear, 11, DayOfWeek.THURSDAY, 4) // Thanksgiving
            holidays += observedUsHoliday(LocalDate(sourceYear, 12, 25))
        }
        return holidays.filterTo(mutableSetOf()) { it.year == year }
    }

    private fun addWithKoreanSubstitute(target: MutableSet<LocalDate>, holiday: LocalDate) {
        target += holiday
        if (holiday.dayOfWeek == DayOfWeek.SATURDAY || holiday.dayOfWeek == DayOfWeek.SUNDAY) {
            var substitute = holiday.plus(1, DateTimeUnit.DAY)
            while (substitute.dayOfWeek in WEEKEND || substitute in target) {
                substitute = substitute.plus(1, DateTimeUnit.DAY)
            }
            target += substitute
        }
    }

    private fun addGroupSubstitute(target: MutableSet<LocalDate>, group: List<LocalDate>) {
        if (group.none { it.dayOfWeek in WEEKEND }) return
        var substitute = group.maxOrNull()!!.plus(1, DateTimeUnit.DAY)
        while (substitute.dayOfWeek in WEEKEND || substitute in target) {
            substitute = substitute.plus(1, DateTimeUnit.DAY)
        }
        target += substitute
    }

    private fun observedUsHoliday(date: LocalDate): LocalDate = when (date.dayOfWeek) {
        DayOfWeek.SATURDAY -> date.minus(1, DateTimeUnit.DAY)
        DayOfWeek.SUNDAY -> date.plus(1, DateTimeUnit.DAY)
        else -> date
    }

    private fun nthWeekday(year: Int, month: Int, weekday: DayOfWeek, occurrence: Int): LocalDate {
        var date = LocalDate(year, month, 1)
        while (date.dayOfWeek != weekday) date = date.plus(1, DateTimeUnit.DAY)
        return date.plus((occurrence - 1) * 7, DateTimeUnit.DAY)
    }

    private fun lastWeekday(year: Int, month: Int, weekday: DayOfWeek): LocalDate {
        var date = if (month == 12) LocalDate(year + 1, 1, 1) else LocalDate(year, month + 1, 1)
        date = date.minus(1, DateTimeUnit.DAY)
        while (date.dayOfWeek != weekday) date = date.minus(1, DateTimeUnit.DAY)
        return date
    }

    private fun lastWeekdayOfYear(year: Int): LocalDate {
        var date = LocalDate(year, 12, 31)
        while (date.dayOfWeek in WEEKEND) date = date.minus(1, DateTimeUnit.DAY)
        return date
    }

    /** Anonymous Gregorian computus, returned as Gregorian Easter Sunday. */
    private fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate(year, month, day)
    }

    private data class LunarAnchors(
        val newYear: LocalDate,
        val buddhasBirthday: LocalDate,
        val chuseok: LocalDate,
    )

    private fun koreanLunarAnchors(year: Int): LunarAnchors = when (year) {
        2026 -> LunarAnchors(LocalDate(2026, 2, 17), LocalDate(2026, 5, 24), LocalDate(2026, 9, 25))
        2027 -> LunarAnchors(LocalDate(2027, 2, 7), LocalDate(2027, 5, 13), LocalDate(2027, 9, 15))
        2028 -> LunarAnchors(LocalDate(2028, 1, 27), LocalDate(2028, 5, 2), LocalDate(2028, 10, 3))
        2029 -> LunarAnchors(LocalDate(2029, 2, 13), LocalDate(2029, 5, 20), LocalDate(2029, 9, 22))
        2030 -> LunarAnchors(LocalDate(2030, 2, 3), LocalDate(2030, 5, 9), LocalDate(2030, 9, 12))
        2031 -> LunarAnchors(LocalDate(2031, 1, 23), LocalDate(2031, 5, 28), LocalDate(2031, 10, 1))
        2032 -> LunarAnchors(LocalDate(2032, 2, 11), LocalDate(2032, 5, 16), LocalDate(2032, 9, 19))
        2033 -> LunarAnchors(LocalDate(2033, 1, 31), LocalDate(2033, 5, 6), LocalDate(2033, 9, 8))
        2034 -> LunarAnchors(LocalDate(2034, 2, 19), LocalDate(2034, 5, 25), LocalDate(2034, 9, 27))
        2035 -> LunarAnchors(LocalDate(2035, 2, 8), LocalDate(2035, 5, 15), LocalDate(2035, 9, 16))
        2036 -> LunarAnchors(LocalDate(2036, 1, 28), LocalDate(2036, 5, 3), LocalDate(2036, 10, 4))
        2037 -> LunarAnchors(LocalDate(2037, 2, 15), LocalDate(2037, 5, 22), LocalDate(2037, 9, 24))
        2038 -> LunarAnchors(LocalDate(2038, 2, 4), LocalDate(2038, 5, 11), LocalDate(2038, 9, 13))
        2039 -> LunarAnchors(LocalDate(2039, 1, 24), LocalDate(2039, 4, 30), LocalDate(2039, 10, 2))
        2040 -> LunarAnchors(LocalDate(2040, 2, 12), LocalDate(2040, 5, 18), LocalDate(2040, 9, 21))
        else -> error("기본 휴장일 팩 범위를 벗어났습니다: $year")
    }

    private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    private val SUPPORTED_YEARS = 2026..2040

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
        SUPPORTED_YEARS.associateWith { year -> CachedSet(koreanFinancialHolidays(year)) },
    )

    private val CLOSED_DATES_BY_YEAR: Map<Int, Map<Market, Set<LocalDate>>> = CachedMap(
        SUPPORTED_YEARS.associateWith { year ->
            val koreanDates: Set<LocalDate> = CachedSet(krx(year))
            val unitedStatesDates: Set<LocalDate> = CachedSet(unitedStates(year))
            CachedMap(
                Market.entries.associateWith { market ->
                    if (market.isKorean) koreanDates else unitedStatesDates
                },
            )
        },
    )
}
