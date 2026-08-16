package com.amond.kmpbook.domain.methodology

import com.amond.kmpbook.domain.model.fund.ReferencePortfolioLimits
import kotlinx.datetime.LocalDate

/** Immutable membership and value-transfer treatment returned by a registered methodology. */
class EquityMethodologyCorporateActionDecision(
    removedAssetIds: Set<String> = emptySet(),
    addedAssetIds: Set<String> = emptySet(),
    val survivingAcquirerAssetId: String? = null,
    val transferredValueFraction: Double = 0.0,
    val followUpRemovalDate: LocalDate? = null,
) {
    val removedAssetIds: Set<String> = buildSet { addAll(removedAssetIds.sorted()) }
    val addedAssetIds: Set<String> = buildSet { addAll(addedAssetIds.sorted()) }

    init {
        require(this.removedAssetIds.isNotEmpty() || this.addedAssetIds.isNotEmpty())
        require(this.removedAssetIds.size <= ReferencePortfolioLimits.MAX_CONSTITUENTS)
        require(this.addedAssetIds.size <= ReferencePortfolioLimits.MAX_CONSTITUENTS)
        require(this.removedAssetIds.none(this.addedAssetIds::contains))
        require((this.removedAssetIds + this.addedAssetIds).all(ASSET_ID::matches))
        require(survivingAcquirerAssetId == null || ASSET_ID.matches(survivingAcquirerAssetId))
        require(survivingAcquirerAssetId !in this.removedAssetIds)
        require(transferredValueFraction.isFinite() && transferredValueFraction in 0.0..1.0)
        require(
            survivingAcquirerAssetId != null || this.addedAssetIds.isNotEmpty() ||
                transferredValueFraction == 0.0,
        )
        require(followUpRemovalDate == null || this.addedAssetIds.isNotEmpty())
    }

    companion object {
        private val ASSET_ID = Regex("[A-Za-z0-9][A-Za-z0-9:._-]{0,199}")
    }
}
