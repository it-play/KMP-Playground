package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

enum class TriggeringQuotationDisposition {
    UNAFFECTED,
    NOT_EXECUTED_AND_ENTERED_INTO_CALL_AUCTION,
}
