package com.amond.kmpbook.domain.methodology

import com.amond.kmpbook.domain.model.fund.EquityMethodologyProfile
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioLimits
import kotlinx.datetime.LocalDate

/** Provider-scheduled observation input for an extraordinary removal decision. */
class EquityMethodologyRemovalInput(
    val profile: EquityMethodologyProfile,
    val observationDate: LocalDate,
    constituents: List<EquityMethodologyCandidate>,
) {
    val constituents: List<EquityMethodologyCandidate> = buildList { addAll(constituents) }

    init {
        require(constituents.isNotEmpty())
        require(constituents.size <= ReferencePortfolioLimits.MAX_CONSTITUENTS)
        val ids = constituents.mapTo(linkedSetOf(), EquityMethodologyCandidate::assetId)
        require(ids.size == constituents.size)
    }
}
