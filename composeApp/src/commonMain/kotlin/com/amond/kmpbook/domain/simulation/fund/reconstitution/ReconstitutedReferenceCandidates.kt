package com.amond.kmpbook.domain.simulation.fund.reconstitution

import com.amond.kmpbook.domain.model.fund.EquityMethodologyPathState
import com.amond.kmpbook.domain.simulation.fund.RankedReferenceCandidate

/** Intermediate scheduled-review candidates shared by the reconstitution phases. */
internal data class ReconstitutedReferenceCandidates(
    val candidates: List<RankedReferenceCandidate>,
    val referenceMarketValueMultipliers: Map<String, Double>,
    val nextPathState: EquityMethodologyPathState,
)
