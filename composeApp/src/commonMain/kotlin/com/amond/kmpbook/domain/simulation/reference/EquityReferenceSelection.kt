package com.amond.kmpbook.domain.simulation.reference

import com.amond.kmpbook.domain.model.reference.EquityReferenceFactorExposure
import com.amond.kmpbook.domain.model.reference.EquityReferencePosition

internal data class EquityReferenceSelection(
    val positions: List<EquityReferencePosition>,
    val factorExposure: EquityReferenceFactorExposure,
    val eligibleCandidateCount: Int,
)
