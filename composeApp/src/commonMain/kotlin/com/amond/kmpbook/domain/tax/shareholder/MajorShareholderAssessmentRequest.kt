package com.amond.kmpbook.domain.tax.shareholder

import com.amond.kmpbook.domain.model.market.Market
import kotlinx.datetime.LocalDate

data class MajorShareholderAssessmentRequest(
    val market: Market,
    val assessedOn: LocalDate,
    val priorBusinessYearEndHoldings: List<ShareholderHoldingSnapshot>,
    /** Related persons/entities are aggregated only when the taxpayer belongs to the largest group. */
    val isLargestShareholderGroup: Boolean,
    /** Ratio immediately after a post-year-end acquisition; null means no acquisition crossing test. */
    val ownershipRatioAfterCurrentYearAcquisition: Double? = null,
) {
    init {
        require(market == Market.KOSPI || market == Market.KOSDAQ) {
            "Major-shareholder assessment only supports KOSPI and KOSDAQ."
        }
        require(priorBusinessYearEndHoldings.any { it.relation == ShareholderRelation.SELF }) {
            "At least one SELF holding snapshot is required."
        }
        require(
            ownershipRatioAfterCurrentYearAcquisition == null ||
                ownershipRatioAfterCurrentYearAcquisition in 0.0..1.0,
        ) { "Post-acquisition ownership must be a ratio from zero to one." }
    }
}
