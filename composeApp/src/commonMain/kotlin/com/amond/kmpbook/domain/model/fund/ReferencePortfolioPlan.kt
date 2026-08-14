package com.amond.kmpbook.domain.model.fund

import kotlin.math.abs
import kotlinx.datetime.LocalDate

/** A benchmark composition plan fixed at a reference close and held until its effective open. */
data class ReferencePortfolioPlan(
    val id: String,
    val portfolioId: String,
    val benchmarkRef: BenchmarkRef,
    val kind: ReferencePortfolioActionKind,
    val selectionDate: LocalDate,
    val weightReferenceDate: LocalDate,
    val effectiveDate: LocalDate,
    /** Membership that was eligible for incumbent treatment when the selection was fixed. */
    val selectionIncumbentAssetIds: List<String>?,
    /** Latest availability boundary used while reproducing the fixed selection. */
    val selectionAvailabilityDate: LocalDate?,
    val positions: List<ReferencePortfolioPosition>,
    val addedAssetIds: List<String>,
    val removedAssetIds: List<String>,
    /**
     * Immutable float-market-value inputs observed at [weightReferenceDate], detached from position
     * drift.
     */
    val weightReferenceMarketValues: Map<String, Double>?,
    val corporateAction: ReferencePortfolioCorporateAction? = null,
) {
    init {
        require(PLAN_ID.matches(id))
        require(PORTFOLIO_ID.matches(portfolioId))
        require(portfolioId == ReferencePortfolioState.portfolioIdFor(benchmarkRef))
        require(selectionDate <= weightReferenceDate && weightReferenceDate < effectiveDate)
        require(positions.isNotEmpty() && positions.size <= ReferencePortfolioLimits.MAX_CONSTITUENTS)
        require(positions == positions.sortedBy(ReferencePortfolioPosition::assetId))
        require(positions.map(ReferencePortfolioPosition::assetId).distinct().size == positions.size)
        require(positions.map(ReferencePortfolioPosition::selectionRank).distinct().size == positions.size)
        require(positions.all { it.enteredOn <= effectiveDate })
        require(abs(positions.sumOf(ReferencePortfolioPosition::currentWeight) - 1.0) <= WEIGHT_EPSILON)
        require(abs(positions.sumOf(ReferencePortfolioPosition::targetWeight) - 1.0) <= WEIGHT_EPSILON)
        require(addedAssetIds == addedAssetIds.distinct().sorted())
        require(removedAssetIds == removedAssetIds.distinct().sorted())
        require(addedAssetIds.none(removedAssetIds::contains))
        require((addedAssetIds + removedAssetIds).all(ASSET_ID::matches))
        require(
            selectionIncumbentAssetIds?.let { incumbentAssetIds ->
                incumbentAssetIds.size <= ReferencePortfolioLimits.MAX_CONSTITUENTS &&
                    incumbentAssetIds == incumbentAssetIds.distinct().sorted() &&
                    incumbentAssetIds.all(ASSET_ID::matches)
            } != false,
        )
        require(
            selectionAvailabilityDate?.let { availabilityDate ->
                availabilityDate in selectionDate..effectiveDate
            } != false,
        )
        require(
            (selectionIncumbentAssetIds == null) == (selectionAvailabilityDate == null),
        )
        val orderedPositionIds = positions.map(ReferencePortfolioPosition::assetId)
        val positionIds = orderedPositionIds.toHashSet()
        require(addedAssetIds.all(positionIds::contains))
        require(removedAssetIds.none(positionIds::contains))
        when (kind) {
            ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION -> require(
                corporateAction == null &&
                    hasValidWeightReferenceMarketValues(orderedPositionIds) &&
                    selectionIncumbentAssetIds != null && selectionAvailabilityDate != null,
            )
            ReferencePortfolioActionKind.SCHEDULED_REWEIGHT,
            ReferencePortfolioActionKind.CONSTRAINT_REWEIGHT,
            -> require(
                addedAssetIds.isEmpty() && removedAssetIds.isEmpty() && corporateAction == null &&
                    hasValidWeightReferenceMarketValues(orderedPositionIds) &&
                    selectionIncumbentAssetIds == null && selectionAvailabilityDate == null,
            )
            ReferencePortfolioActionKind.EXTRAORDINARY_REMOVAL -> {
                require(
                    addedAssetIds.isEmpty() && removedAssetIds.isNotEmpty() &&
                        corporateAction == null && weightReferenceMarketValues == null &&
                        selectionIncumbentAssetIds == null && selectionAvailabilityDate == null,
                )
            }
            ReferencePortfolioActionKind.CONSTITUENT_MERGER -> require(
                corporateAction?.kind == ReferencePortfolioCorporateActionKind.MERGER &&
                    addedAssetIds.isEmpty() && removedAssetIds.isNotEmpty() &&
                    weightReferenceMarketValues == null && selectionIncumbentAssetIds == null &&
                    selectionAvailabilityDate == null,
            )
            ReferencePortfolioActionKind.SPIN_OFF_ADDITION -> require(
                corporateAction?.kind == ReferencePortfolioCorporateActionKind.SPIN_OFF &&
                    addedAssetIds == listOfNotNull(corporateAction.secondaryAssetId) &&
                    removedAssetIds.isEmpty() && weightReferenceMarketValues == null &&
                    selectionIncumbentAssetIds == null && selectionAvailabilityDate == null,
            )
            ReferencePortfolioActionKind.SPIN_OFF_REMOVAL -> require(
                corporateAction?.kind == ReferencePortfolioCorporateActionKind.SPIN_OFF &&
                    addedAssetIds.isEmpty() &&
                    removedAssetIds == listOfNotNull(corporateAction.secondaryAssetId) &&
                    weightReferenceMarketValues == null && selectionIncumbentAssetIds == null &&
                    selectionAvailabilityDate == null,
            )
            ReferencePortfolioActionKind.TERMINAL_REMOVAL -> require(
                corporateAction?.kind == ReferencePortfolioCorporateActionKind.TERMINAL_REMOVAL &&
                    addedAssetIds.isEmpty() && removedAssetIds == listOf(corporateAction.primaryAssetId) &&
                    weightReferenceMarketValues == null && selectionIncumbentAssetIds == null &&
                    selectionAvailabilityDate == null,
            )
        }
    }

    private fun hasValidWeightReferenceMarketValues(orderedPositionIds: List<String>): Boolean =
        weightReferenceMarketValues?.let { marketValues ->
            marketValues.keys.toList() == orderedPositionIds &&
                marketValues.values.all { value -> value.isFinite() && value > 0.0 }
        } == true

    companion object {
        private const val WEIGHT_EPSILON: Double = 1e-8
        private val PLAN_ID = Regex("[A-Za-z0-9][A-Za-z0-9:._-]{0,511}")
        private val ASSET_ID = Regex("[A-Za-z0-9][A-Za-z0-9:._-]{0,199}")
        private val PORTFOLIO_ID = Regex("[a-z0-9][a-z0-9:._-]{2,199}")
    }
}
