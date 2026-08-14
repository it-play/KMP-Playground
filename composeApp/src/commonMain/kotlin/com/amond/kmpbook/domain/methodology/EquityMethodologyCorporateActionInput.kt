package com.amond.kmpbook.domain.methodology

import com.amond.kmpbook.domain.model.fund.EquityMethodologyProfile
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioCorporateAction
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioCorporateActionKind
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioLimits

/** Bounded point-in-time input for one provider-specific corporate-action decision. */
class EquityMethodologyCorporateActionInput(
    val profile: EquityMethodologyProfile,
    val event: ReferencePortfolioCorporateAction,
    currentConstituents: List<EquityMethodologyCandidate>,
    universeCandidates: List<EquityMethodologyCandidate>,
) {
    val currentConstituents: List<EquityMethodologyCandidate> = buildList {
        addAll(currentConstituents)
    }
    val universeCandidates: List<EquityMethodologyCandidate> = buildList {
        addAll(universeCandidates)
    }

    init {
        require(event.effectiveDate >= profile.effectiveFrom)
        require(this.currentConstituents.isNotEmpty())
        require(this.currentConstituents.size <= ReferencePortfolioLimits.MAX_CONSTITUENTS)
        require(this.universeCandidates.isNotEmpty())
        require(this.universeCandidates.size <= EquityMethodologySelectionInput.MAX_CANDIDATES)
        val constituentIds = this.currentConstituents.map(EquityMethodologyCandidate::assetId)
        val universeIds = this.universeCandidates.map(EquityMethodologyCandidate::assetId)
        require(constituentIds.distinct().size == constituentIds.size)
        require(universeIds.distinct().size == universeIds.size)
        require(constituentIds.all(universeIds.toHashSet()::contains))
        require(event.primaryAssetId in constituentIds)
        require(event.secondaryAssetId == null || event.secondaryAssetId in universeIds)
        if (event.kind == ReferencePortfolioCorporateActionKind.SPIN_OFF) {
            require(event.secondaryAssetId !in constituentIds)
        }
    }
}
