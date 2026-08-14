package com.amond.kmpbook.domain.methodology

import com.amond.kmpbook.domain.model.fund.EquityMethodologyProfile
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioLimits
import kotlinx.datetime.LocalDate

/** End-of-session weights presented to a provider for an event-driven constraint action. */
class EquityMethodologyConstraintInput(
    val profile: EquityMethodologyProfile,
    val observationDate: LocalDate,
    currentWeights: Map<String, Double>,
) {
    val currentWeights: Map<String, Double> = buildMap { putAll(currentWeights.toSortedMap()) }

    init {
        require(this.currentWeights.isNotEmpty())
        require(this.currentWeights.size <= ReferencePortfolioLimits.MAX_CONSTITUENTS)
        require(this.currentWeights.keys.all(ASSET_ID::matches))
        require(this.currentWeights.values.all { it.isFinite() && it >= 0.0 })
    }

    companion object {
        private val ASSET_ID = Regex("[A-Za-z0-9][A-Za-z0-9:._-]{0,199}")
    }
}
