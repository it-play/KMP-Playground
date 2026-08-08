package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

data class UsMwcbTransition(
    val state: UsMwcbState,
    val event: UsMwcbEvent = UsMwcbEvent.NONE,
)
