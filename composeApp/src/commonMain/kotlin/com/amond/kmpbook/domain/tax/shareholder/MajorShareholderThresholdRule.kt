package com.amond.kmpbook.domain.tax.shareholder

import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.tax.core.EffectiveDateRange
import com.amond.kmpbook.domain.tax.core.RuleSource

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
