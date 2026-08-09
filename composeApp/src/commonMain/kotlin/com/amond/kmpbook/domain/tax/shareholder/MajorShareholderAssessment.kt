package com.amond.kmpbook.domain.tax.shareholder

import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.tax.core.RuleSource

data class MajorShareholderAssessment(
    val isMajorShareholder: Boolean,
    val market: Market,
    val assessedOwnershipRatio: Double,
    val assessedMarketValueKrw: Long,
    val ownershipThreshold: Double,
    val marketValueThresholdKrw: Long,
    val metByPriorYearEndOwnership: Boolean,
    val metByPriorYearEndMarketValue: Boolean,
    val metByCurrentYearAcquisition: Boolean,
    val source: RuleSource,
    val notes: List<String>,
)
