package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

data class TradingRestriction(
    val source: TradingRestrictionSource,
    val code: String,
    val message: String,
    val endsAt: Instant? = null,
) {
    init {
        require(code.isNotBlank() && message.isNotBlank())
    }
}
