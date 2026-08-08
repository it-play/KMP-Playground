package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

data class TradingHaltOrderPolicy(
    val acceptsNewOrders: Boolean,
    val allowsCancellation: Boolean,
    val allowsExecution: Boolean,
    val allowsContinuousTrading: Boolean = false,
)
