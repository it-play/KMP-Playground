package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

data class UsLuldTransition(
    val state: UsLuldState,
    val event: UsLuldEvent = UsLuldEvent.NONE,
)
