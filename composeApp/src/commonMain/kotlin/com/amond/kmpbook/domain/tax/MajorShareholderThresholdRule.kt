package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Market
import kotlinx.datetime.LocalDate

data class MajorShareholderThresholdRule(
    val market: Market,
    val minimumOwnershipRatio: Double,
    val minimumMarketValueKrw: Long,
    val effectiveRange: EffectiveDateRange,
    val source: RuleSource,
) {
    init {
        require(minimumOwnershipRatio in 0.0..1.0) { "The ownership threshold must be a ratio." }
        require(minimumMarketValueKrw > 0L) { "The market-value threshold must be positive." }
    }
}
