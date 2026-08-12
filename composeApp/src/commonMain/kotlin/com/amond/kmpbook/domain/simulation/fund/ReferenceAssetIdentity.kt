package com.amond.kmpbook.domain.simulation.fund

import com.amond.kmpbook.domain.model.fund.MethodologyEquitySector
import com.amond.kmpbook.domain.model.market.Sector

/** Immutable metadata resolved from the campaign's deterministic, non-tradable asset repository. */
internal data class ReferenceAssetIdentity(
    val assetId: String,
    val displaySymbol: String,
    val displayName: String,
    val sector: Sector,
    val methodologySector: MethodologyEquitySector,
)
