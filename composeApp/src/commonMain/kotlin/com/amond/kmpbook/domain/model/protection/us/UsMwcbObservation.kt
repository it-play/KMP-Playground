package com.amond.kmpbook.domain.model.protection.us

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

data class UsMwcbObservation(
    val tradingDate: LocalDate,
    val observedAt: Instant,
    val easternTime: LocalTime,
    val regularSessionClose: LocalTime,
    val sp500Value: Double,
    val previousClose: Double,
) {
    init {
        require(sp500Value > 0.0 && sp500Value.isFinite())
        require(previousClose > 0.0 && previousClose.isFinite())
    }

    val declineRate: Double get() = (previousClose - sp500Value) / previousClose
}
