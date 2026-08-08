package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

data class TradingPermissionDecision(
    val allowed: Boolean,
    val executionMode: TradingExecutionMode,
    val controllingRestriction: TradingRestriction? = null,
    val restrictions: List<TradingRestriction> = emptyList(),
) {
    init {
        require((controllingRestriction == null) == restrictions.isEmpty())
        require(controllingRestriction == null || controllingRestriction == restrictions.first())
        require(allowed || restrictions.isNotEmpty())
    }
}
