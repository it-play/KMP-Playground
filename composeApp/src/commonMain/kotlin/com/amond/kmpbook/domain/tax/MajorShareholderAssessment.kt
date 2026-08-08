package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.Market
import kotlinx.datetime.LocalDate

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
