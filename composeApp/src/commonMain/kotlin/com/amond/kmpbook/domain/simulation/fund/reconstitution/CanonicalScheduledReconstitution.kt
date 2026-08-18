package com.amond.kmpbook.domain.simulation.fund.reconstitution

import com.amond.kmpbook.domain.model.fund.EquityMethodologyPathState
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioPosition
import kotlinx.datetime.LocalDate

/** Canonical replay output used by save validation for path-dependent scheduled reviews. */
internal data class CanonicalScheduledReconstitution(
    val selectionRanks: Map<String, Int>,
    val referenceMarketValueMultipliers: Map<String, Double>,
    val nextPathState: EquityMethodologyPathState,
    val selectionAvailabilityDate: LocalDate,
    val weightReferenceMarketValues: Map<String, Double>?,
    val targetWeights: Map<String, Double>?,
    val canonicalFinalPositions: List<ReferencePortfolioPosition>?,
    val canonicalTransitionPositionsByEffectiveDate:
        Map<LocalDate, List<ReferencePortfolioPosition>>,
)
