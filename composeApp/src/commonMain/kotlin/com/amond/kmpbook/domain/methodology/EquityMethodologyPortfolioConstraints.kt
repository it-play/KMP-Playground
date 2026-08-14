package com.amond.kmpbook.domain.methodology

import com.amond.kmpbook.domain.model.fund.ReferencePortfolioLimits

/** Provider-declared bounds that the execution host independently revalidates. */
data class EquityMethodologyPortfolioConstraints(
    val minimumConstituentCount: Int,
    val maximumConstituentCount: Int,
    val scheduledSelectionCount: Int? = null,
    val individualWeightCap: Double? = null,
    val sectorWeightCap: Double? = null,
) {
    init {
        require(minimumConstituentCount in 1..ReferencePortfolioLimits.MAX_CONSTITUENTS)
        require(maximumConstituentCount in minimumConstituentCount..ReferencePortfolioLimits.MAX_CONSTITUENTS)
        require(scheduledSelectionCount == null || scheduledSelectionCount in
            minimumConstituentCount..maximumConstituentCount)
        require(individualWeightCap == null || individualWeightCap.isFinite() && individualWeightCap in 0.0..1.0)
        require(sectorWeightCap == null || sectorWeightCap.isFinite() && sectorWeightCap in 0.0..1.0)
        require(individualWeightCap == null || sectorWeightCap == null || sectorWeightCap >= individualWeightCap)
    }
}
