package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

data class KrxSidecarTransition(
    val state: KrxSidecarState,
    val event: KrxSidecarEvent = KrxSidecarEvent.NONE,
)
