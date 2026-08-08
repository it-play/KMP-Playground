package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

data class KrxCircuitBreakerObservation(
    val market: Market,
    val tradingDate: LocalDate,
    val observedAt: Instant,
    val indexValue: Double,
    val previousClose: Double,
    /** Whole or fractional minutes remaining until the scheduled regular-session close. */
    val minutesUntilClose: Double,
    /**
     * Optional intrabar estimate of when every condition for the candidate stage became true.
     * An hourly simulator should linearly interpolate the threshold crossing and pass that time;
     * null makes the engine conservatively begin persistence at [observedAt].
     */
    val conditionSatisfiedSince: Instant? = null,
) {
    init {
        require(market == Market.KOSPI || market == Market.KOSDAQ)
        require(indexValue > 0.0 && indexValue.isFinite())
        require(previousClose > 0.0 && previousClose.isFinite())
        require(minutesUntilClose >= 0.0 && minutesUntilClose.isFinite())
        require(conditionSatisfiedSince == null || conditionSatisfiedSince <= observedAt)
    }

    val declineRate: Double get() = (previousClose - indexValue) / previousClose
}
