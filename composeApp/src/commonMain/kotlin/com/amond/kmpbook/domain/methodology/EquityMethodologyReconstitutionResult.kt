package com.amond.kmpbook.domain.methodology

import com.amond.kmpbook.domain.model.fund.EquityMethodologyPathState
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioLimits

/**
 * Complete output of one path-dependent scheduled constituent review.
 *
 * [referenceMarketValueMultipliers] is a bounded provider weighting multiplier applied once to
 * each selected security's raw float-market value before the provider's normal weighting-reference
 * adjustment and target-weight phases. It is not a standalone allocation fraction: it may combine
 * a fractional methodology packet with the provider's bounded EFF/IWF rounding adjustment.
 */
class EquityMethodologyReconstitutionResult(
    selections: List<EquityMethodologySelection>,
    referenceMarketValueMultipliers: Map<String, Double>,
    val nextPathState: EquityMethodologyPathState,
) {
    val selections: List<EquityMethodologySelection> = buildList { addAll(selections) }
    val referenceMarketValueMultipliers: Map<String, Double> = buildMap {
        putAll(referenceMarketValueMultipliers.toSortedMap())
    }

    init {
        require(this.selections.isNotEmpty())
        require(this.selections.size <= ReferencePortfolioLimits.MAX_CONSTITUENTS)
        require(this.selections.map(EquityMethodologySelection::assetId).distinct().size ==
            this.selections.size)
        require(this.referenceMarketValueMultipliers.keys ==
            this.selections.mapTo(linkedSetOf(), EquityMethodologySelection::assetId)) {
            "A reconstitution multiplier must exist for every selected security and no other ID."
        }
        require(this.referenceMarketValueMultipliers.values.all { multiplier ->
            multiplier.isFinite() && multiplier > 0.0 &&
                multiplier <= MAX_PROVIDER_WEIGHTING_MULTIPLIER
        }) {
            "A reconstitution provider weighting multiplier must be in " +
                "(0, $MAX_PROVIDER_WEIGHTING_MULTIPLIER]."
        }
    }

    companion object {
        const val MAX_PROVIDER_WEIGHTING_MULTIPLIER: Double = 2.0
    }
}
