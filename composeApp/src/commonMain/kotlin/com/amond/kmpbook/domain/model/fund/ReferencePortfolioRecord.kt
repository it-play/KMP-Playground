package com.amond.kmpbook.domain.model.fund

import kotlinx.datetime.LocalDate

/** Immutable composition-revision record owned by a shared reference portfolio. */
data class ReferencePortfolioRecord(
    val id: String,
    val portfolioId: String,
    val benchmarkRef: BenchmarkRef,
    val kind: ReferencePortfolioActionKind,
    val selectionDate: LocalDate,
    val weightReferenceDate: LocalDate,
    val effectiveDate: LocalDate,
    val addedAssetIds: List<String>,
    val removedAssetIds: List<String>,
    val beforeCompositionHash: String,
    val afterCompositionHash: String,
    val turnoverRate: Double,
    val resultingConstituentCount: Int,
    val revision: Long,
    val corporateAction: ReferencePortfolioCorporateAction? = null,
) {
    init {
        require(RECORD_ID.matches(id))
        require(PORTFOLIO_ID.matches(portfolioId))
        require(portfolioId == ReferencePortfolioState.portfolioIdFor(benchmarkRef))
        require(selectionDate <= weightReferenceDate && weightReferenceDate < effectiveDate)
        require(addedAssetIds.size <= ReferencePortfolioLimits.MAX_CONSTITUENTS)
        require(removedAssetIds.size <= ReferencePortfolioLimits.MAX_CONSTITUENTS)
        require(addedAssetIds == addedAssetIds.distinct().sorted())
        require(removedAssetIds == removedAssetIds.distinct().sorted())
        require(addedAssetIds.none(removedAssetIds::contains))
        require((addedAssetIds + removedAssetIds).all(ASSET_ID::matches))
        require(COMPOSITION_HASH.matches(beforeCompositionHash))
        require(COMPOSITION_HASH.matches(afterCompositionHash))
        require(turnoverRate.isFinite() && turnoverRate in 0.0..1.0)
        require(resultingConstituentCount in 1..ReferencePortfolioLimits.MAX_CONSTITUENTS)
        require(revision > 0L)
        when (kind) {
            ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION -> require(corporateAction == null)
            ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION_TRANSITION ->
                require(corporateAction == null)
            ReferencePortfolioActionKind.CORPORATE_ACTION_TRANSITION -> require(
                corporateAction?.kind in setOf(
                    ReferencePortfolioCorporateActionKind.MERGER,
                    ReferencePortfolioCorporateActionKind.TERMINAL_REMOVAL,
                ) && removedAssetIds.isEmpty(),
            )
            ReferencePortfolioActionKind.SCHEDULED_REWEIGHT,
            ReferencePortfolioActionKind.CONSTRAINT_REWEIGHT,
            -> require(
                addedAssetIds.isEmpty() && removedAssetIds.isEmpty() && corporateAction == null,
            )
            ReferencePortfolioActionKind.EXTRAORDINARY_REMOVAL -> {
                require(addedAssetIds.isEmpty() && removedAssetIds.isNotEmpty() && corporateAction == null)
            }
            ReferencePortfolioActionKind.CONSTITUENT_MERGER -> require(
                corporateAction?.kind == ReferencePortfolioCorporateActionKind.MERGER &&
                    removedAssetIds.isNotEmpty(),
            )
            ReferencePortfolioActionKind.SPIN_OFF_ADDITION -> require(
                corporateAction?.kind == ReferencePortfolioCorporateActionKind.SPIN_OFF &&
                    addedAssetIds == listOfNotNull(corporateAction.secondaryAssetId) &&
                    removedAssetIds.isEmpty(),
            )
            ReferencePortfolioActionKind.SPIN_OFF_REMOVAL -> require(
                corporateAction?.kind == ReferencePortfolioCorporateActionKind.SPIN_OFF &&
                    addedAssetIds.isEmpty() &&
                    removedAssetIds == listOfNotNull(corporateAction.secondaryAssetId),
            )
            ReferencePortfolioActionKind.TERMINAL_REMOVAL -> require(
                corporateAction?.kind == ReferencePortfolioCorporateActionKind.TERMINAL_REMOVAL &&
                    removedAssetIds == listOf(corporateAction.primaryAssetId),
            )
        }
    }

    companion object {
        private val RECORD_ID = Regex("[A-Za-z0-9][A-Za-z0-9:._-]{0,511}")
        private val ASSET_ID = Regex("[A-Za-z0-9][A-Za-z0-9:._-]{0,199}")
        private val PORTFOLIO_ID = Regex("[a-z0-9][a-z0-9:._-]{2,199}")
        private val COMPOSITION_HASH = Regex("[0-9a-f]{16}")
    }
}
