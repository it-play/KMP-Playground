package com.amond.kmpbook.domain.model.fund

/** Canonical calendar plus the evidence boundary for that calendar. */
class CompositeRebalanceSchedule(
    val calendar: CompositeRebalanceCalendar,
    months: Set<Int>,
    val origin: CompositeParameterOrigin,
) {
    val months: Set<Int> = months.sorted().toCollection(linkedSetOf()).toSet()

    init {
        require(this.months.all { it in 1..12 })
        when (calendar) {
            CompositeRebalanceCalendar.STATIC,
            CompositeRebalanceCalendar.DAILY,
            CompositeRebalanceCalendar.CONTINUOUS_ACTIVE,
            -> require(this.months.isEmpty())
            CompositeRebalanceCalendar.MONTHLY -> require(this.months == (1..12).toSet())
            CompositeRebalanceCalendar.QUARTERLY -> require(this.months.size == 4)
            CompositeRebalanceCalendar.SEMI_ANNUAL -> require(this.months.size == 2)
            CompositeRebalanceCalendar.ANNUAL -> require(this.months.size == 1)
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is CompositeRebalanceSchedule &&
            calendar == other.calendar && months == other.months && origin == other.origin

    override fun hashCode(): Int = 31 * (31 * calendar.hashCode() + months.hashCode()) + origin.hashCode()
}
