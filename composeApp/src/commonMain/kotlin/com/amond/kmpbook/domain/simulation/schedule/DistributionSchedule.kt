package com.amond.kmpbook.domain.simulation.schedule

import com.amond.kmpbook.domain.model.instrument.DistributionFrequency
import com.amond.kmpbook.domain.model.instrument.DistributionCalendar
import com.amond.kmpbook.domain.model.instrument.DistributionEventSchedule
import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.time.DefaultMarketHolidays
import com.amond.kmpbook.domain.time.GameCalendar
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Canonical frozen-scenario cash-distribution calendar shared by runtime and persistence checks. */
object DistributionSchedule {
    const val DISTRIBUTION_DAY: Int = 15

    fun isDistributionDate(
        date: LocalDate,
        frequency: DistributionFrequency,
        calendar: DistributionCalendar = DistributionCalendar.FIXED_DAY_15,
    ): Boolean = when (calendar) {
        DistributionCalendar.FIXED_DAY_15 -> when (frequency) {
            DistributionFrequency.NONE -> false
            DistributionFrequency.WEEKLY -> date.dayOfWeek == DayOfWeek.FRIDAY
            DistributionFrequency.MONTHLY -> date.day == DISTRIBUTION_DAY
            DistributionFrequency.QUARTERLY ->
                date.day == DISTRIBUTION_DAY && date.month in QUARTER_END_MONTHS
            DistributionFrequency.SEMIANNUAL ->
                date.day == DISTRIBUTION_DAY && date.month in SEMIANNUAL_MONTHS
            DistributionFrequency.ANNUAL ->
                date.day == DISTRIBUTION_DAY && date.month == Month.DECEMBER
        }
        DistributionCalendar.KRX_MONTH_END -> {
            val recordDate = lastKrxBusinessDateOfMonth(date)
            isEligibleMonth(recordDate.month, frequency) &&
                date == previousBusinessDate(Market.KOSPI, recordDate)
        }
        DistributionCalendar.VANGUARD_VOO_EX_DATE ->
            VanguardVooDistributionCalendar.isDistributionDate(date, frequency)
        DistributionCalendar.VANGUARD_VTV_EX_DATE ->
            VanguardVtvDistributionCalendar.isDistributionDate(date, frequency)
        DistributionCalendar.SCHWAB_EQUITY_ETF_EX_DATE ->
            SchwabEquityEtfDistributionCalendar.isDistributionDate(date, frequency)
        DistributionCalendar.KRX_PRECEDING_BUSINESS_DAY_15 -> {
            val recordDate = precedingKrxBusinessDateOnOrBefore(
                LocalDate(date.year, date.month.ordinal + 1, DISTRIBUTION_DAY),
            )
            frequency == DistributionFrequency.MONTHLY && date == previousBusinessDate(
                Market.KOSPI,
                recordDate,
            )
        }
    }

    /**
     * [date]에 분배락이 발생하는 상품 일정을 반환한다. 카탈로그의 공표 일정이 달력
     * projection보다 우선하며, 미래 미공표 건은 상품별 영업일 지급 지연을 적용한다.
     */
    fun eventOnExDate(stock: StockDefinition, date: LocalDate): DistributionEventSchedule? {
        stock.behavior.distributionPolicy.announcedDistributions
            .singleOrNull { announcement -> announcement.exDate == date }
            ?.let { announcement ->
                return DistributionEventSchedule(
                    exDate = announcement.exDate,
                    recordDate = announcement.recordDate,
                    payDate = announcement.payDate,
                    declaredGrossPerUnit = announcement.declaredGrossPerUnit,
                    skip = announcement.skip,
                    isAnnounced = true,
                )
            }
        if (!isDistributionDate(date, stock.behavior.distributionFrequency, stock.behavior.distributionCalendar)) {
            return null
        }
        val recordDate = when (stock.behavior.distributionCalendar) {
            DistributionCalendar.KRX_MONTH_END,
            DistributionCalendar.KRX_PRECEDING_BUSINESS_DAY_15,
            -> nextBusinessDate(stock.market, date)
            else -> date
        }
        return DistributionEventSchedule(
            exDate = date,
            recordDate = recordDate,
            payDate = addBusinessDays(
                stock.market,
                recordDate,
                stock.behavior.distributionPolicy.projectedPaymentLagBusinessDays,
            ),
            declaredGrossPerUnit = null,
            skip = false,
            isAnnounced = false,
        )
    }

    private fun isEligibleMonth(month: Month, frequency: DistributionFrequency): Boolean = when (frequency) {
        DistributionFrequency.NONE -> false
        DistributionFrequency.WEEKLY -> false
        DistributionFrequency.MONTHLY -> true
        DistributionFrequency.QUARTERLY -> month in QUARTER_END_MONTHS
        DistributionFrequency.SEMIANNUAL -> month in SEMIANNUAL_MONTHS
        DistributionFrequency.ANNUAL -> month == Month.DECEMBER
    }

    private fun lastKrxBusinessDateOfMonth(date: LocalDate): LocalDate {
        val firstOfNextMonth = if (date.month == Month.DECEMBER) {
            LocalDate(date.year + 1, 1, 1)
        } else {
            LocalDate(date.year, date.month.ordinal + 2, 1)
        }
        return previousBusinessDate(Market.KOSPI, firstOfNextMonth)
    }

    private fun precedingKrxBusinessDateOnOrBefore(date: LocalDate): LocalDate {
        var candidate = date
        while (!isBusinessDate(Market.KOSPI, candidate)) {
            candidate = candidate.minus(1, DateTimeUnit.DAY)
        }
        return candidate
    }

    fun addBusinessDays(market: Market, date: LocalDate, days: Int): LocalDate {
        require(days >= 0)
        var candidate = date
        repeat(days) { candidate = nextBusinessDate(market, candidate) }
        return candidate
    }

    fun previousBusinessDate(market: Market, date: LocalDate): LocalDate {
        var candidate = date.minus(1, DateTimeUnit.DAY)
        while (!isBusinessDate(market, candidate)) candidate = candidate.minus(1, DateTimeUnit.DAY)
        return candidate
    }

    fun nextBusinessDate(market: Market, date: LocalDate): LocalDate {
        var candidate = date.plus(1, DateTimeUnit.DAY)
        while (!isBusinessDate(market, candidate)) candidate = candidate.plus(1, DateTimeUnit.DAY)
        return candidate
    }

    fun isBusinessDate(market: Market, date: LocalDate): Boolean =
        !GameCalendar.isWeekend(date) && date !in DefaultMarketHolidays.closedDates(market, date.year)

    private val QUARTER_END_MONTHS: Set<Month> = setOf(
        Month.MARCH,
        Month.JUNE,
        Month.SEPTEMBER,
        Month.DECEMBER,
    )
    private val SEMIANNUAL_MONTHS: Set<Month> = setOf(Month.JUNE, Month.DECEMBER)
}
