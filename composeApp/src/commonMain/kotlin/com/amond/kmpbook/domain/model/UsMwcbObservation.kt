package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

data class UsMwcbObservation(
    val tradingDate: LocalDate,
    val observedAt: Instant,
    val easternTime: LocalTime,
    val sp500Value: Double,
    val previousClose: Double,
) {
    init {
        require(sp500Value > 0.0 && sp500Value.isFinite())
        require(previousClose > 0.0 && previousClose.isFinite())
    }

    val declineRate: Double get() = (previousClose - sp500Value) / previousClose
}
