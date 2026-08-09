package com.amond.kmpbook.presentation.simulator

import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

internal data class DailyPriceTracker(
    var date: LocalDate,
    var basePrice: Double,
    var open: Double,
    var high: Double,
    var low: Double,
    var hasRegularTrading: Boolean,
)
