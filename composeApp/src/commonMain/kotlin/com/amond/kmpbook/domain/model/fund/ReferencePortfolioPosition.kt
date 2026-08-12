package com.amond.kmpbook.domain.model.fund

import kotlinx.datetime.LocalDate

/**
 * One non-tradable constituent position in a shared benchmark reference portfolio.
 *
 * Immutable display and classification data deliberately stays in the deterministic reference
 * asset repository. Persisted positions only carry the path-dependent values required to resume.
 */
data class ReferencePortfolioPosition(
    val assetId: String,
    val currentWeight: Double,
    val targetWeight: Double,
    val referenceFloatMarketValue: Double,
    val enteredOn: LocalDate,
    val selectionRank: Int,
) {
    init {
        require(ASSET_ID.matches(assetId))
        require(currentWeight.isFinite() && currentWeight in 0.0..1.0)
        require(targetWeight.isFinite() && targetWeight in 0.0..1.0)
        require(
            referenceFloatMarketValue.isFinite() &&
                referenceFloatMarketValue in MIN_REFERENCE_VALUE..MAX_REFERENCE_VALUE,
        )
        require(selectionRank in 1..MAX_SELECTION_RANK)
    }

    companion object {
        private const val MIN_REFERENCE_VALUE: Double = 1.0
        private const val MAX_REFERENCE_VALUE: Double = 1e20
        private const val MAX_SELECTION_RANK: Int = 1_000_000
        private val ASSET_ID = Regex("[A-Za-z0-9][A-Za-z0-9:._-]{0,199}")
    }
}
