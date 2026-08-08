package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

data class UsLuldObservation(
    val observedAt: Instant,
    val easternTime: LocalTime,
    /** SIP-determined limit side; null means no Limit State quotation is present. */
    val limitSide: UsLuldLimitSide? = null,
    /** True only when the entire size of every limit-state quotation has executed or cancelled. */
    val allLimitStateQuotationsCleared: Boolean = false,
)
