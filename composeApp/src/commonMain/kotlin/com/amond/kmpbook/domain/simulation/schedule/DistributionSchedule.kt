package com.amond.kmpbook.domain.simulation.schedule

import com.amond.kmpbook.domain.model.instrument.DistributionFrequency
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

/** Canonical frozen-scenario cash-distribution calendar shared by runtime and persistence checks. */
object DistributionSchedule {
    const val DISTRIBUTION_DAY: Int = 15

    fun isDistributionDate(date: LocalDate, frequency: DistributionFrequency): Boolean =
        when (frequency) {
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

    private val QUARTER_END_MONTHS: Set<Month> = setOf(
        Month.MARCH,
        Month.JUNE,
        Month.SEPTEMBER,
        Month.DECEMBER,
    )
    private val SEMIANNUAL_MONTHS: Set<Month> = setOf(Month.JUNE, Month.DECEMBER)
}
