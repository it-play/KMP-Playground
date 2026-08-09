package com.amond.kmpbook.domain.model.protection.core

import com.amond.kmpbook.domain.model.protection.core.TradingPermissionDecision
import com.amond.kmpbook.domain.model.protection.core.TradingRestriction
import com.amond.kmpbook.domain.model.trading.TradingExecutionMode

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
