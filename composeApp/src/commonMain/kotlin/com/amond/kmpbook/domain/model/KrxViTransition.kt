package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

data class KrxViTransition(
    val state: KrxViState,
    val event: KrxViEvent = KrxViEvent.NONE,
    val triggeringQuotationDisposition: TriggeringQuotationDisposition = TriggeringQuotationDisposition.UNAFFECTED,
)
