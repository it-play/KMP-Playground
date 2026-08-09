package com.amond.kmpbook.domain.model.trading

import kotlinx.datetime.LocalDate

data class TradingDayWindow(
    val startsOn: LocalDate,
    val endsOnInclusive: LocalDate,
) {
    init {
        require(endsOnInclusive >= startsOn)
    }

    operator fun contains(date: LocalDate): Boolean = date in startsOn..endsOnInclusive
}
