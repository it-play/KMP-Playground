package com.amond.kmpbook.domain.model.protection.us

import com.amond.kmpbook.domain.model.protection.us.UsLuldLimitSide
import com.amond.kmpbook.domain.model.protection.us.UsLuldObservation
import kotlin.time.Instant
import kotlinx.datetime.LocalTime

data class UsLuldObservation(
    val observedAt: Instant,
    val easternTime: LocalTime,
    /** SIP-determined limit side; null means no Limit State quotation is present. */
    val limitSide: UsLuldLimitSide? = null,
    /** True only when the entire size of every limit-state quotation has executed or cancelled. */
    val allLimitStateQuotationsCleared: Boolean = false,
)
