package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

data class TradingDayWindow(
    val startsOn: LocalDate,
    val endsOnInclusive: LocalDate,
) {
    init {
        require(endsOnInclusive >= startsOn)
    }

    operator fun contains(date: LocalDate): Boolean = date in startsOn..endsOnInclusive
}
