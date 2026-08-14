package com.amond.kmpbook.domain.methodology

import kotlinx.datetime.LocalDate
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioLimits

/** Provider decision at one extraordinary review, including its canonical effective date. */
class EquityMethodologyRemovalDecision(
    val effectiveDate: LocalDate,
    removedAssetIds: Set<String>,
) {
    val removedAssetIds: Set<String> = buildSet { addAll(removedAssetIds.sorted()) }

    init {
        require(this.removedAssetIds.isNotEmpty())
        require(this.removedAssetIds.size <= ReferencePortfolioLimits.MAX_CONSTITUENTS)
        require(this.removedAssetIds.all(ASSET_ID::matches))
    }

    companion object {
        private val ASSET_ID = Regex("[A-Za-z0-9][A-Za-z0-9:._-]{0,199}")
    }
}
