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
    val methodologyPathState: EquityMethodologyPathState,
    val addedAssetIds: List<String>,
    val removedAssetIds: List<String>,
    /** Live target baseline fixed when this staged transition was most recently compiled. */
    val transitionBaselineWeights: Map<String, Double>?,
    /**
     * Immutable float-market-value weighting basis fixed at [weightReferenceDate], detached from
     * later position drift. Corporate additions may normalize it by target/current weight so drift
     * already present at the announcement close can be replayed exactly.
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
                    transitionBaselineWeights == null &&
                    selectionIncumbentAssetIds != null && selectionAvailabilityDate != null,
            )
            ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION_TRANSITION -> require(
                corporateAction == null && weightReferenceMarketValues == null &&
                    hasValidTransitionBaselineWeights(positionIds) &&
                    selectionIncumbentAssetIds == null && selectionAvailabilityDate == null,
            )
            ReferencePortfolioActionKind.CORPORATE_ACTION_TRANSITION -> require(
                corporateAction?.kind in setOf(
                    ReferencePortfolioCorporateActionKind.MERGER,
                    ReferencePortfolioCorporateActionKind.TERMINAL_REMOVAL,
                ) &&
                    effectiveDate >= requireNotNull(corporateAction).effectiveDate &&
                    removedAssetIds.isEmpty() &&
                    weightReferenceMarketValues == null &&
                    hasValidTransitionBaselineWeights(positionIds) &&
                    selectionIncumbentAssetIds == null && selectionAvailabilityDate == null,
            )
            ReferencePortfolioActionKind.SCHEDULED_REWEIGHT -> require(
                addedAssetIds.isEmpty() && removedAssetIds.isEmpty() && corporateAction == null &&
                    hasValidWeightReferenceMarketValues(orderedPositionIds) &&
                    transitionBaselineWeights == null &&
                    selectionIncumbentAssetIds == null && selectionAvailabilityDate == null,
            )
            ReferencePortfolioActionKind.CONSTRAINT_REWEIGHT -> require(
                addedAssetIds.isEmpty() && removedAssetIds.isEmpty() && corporateAction == null &&
                    hasValidConstraintWeightInput(orderedPositionIds) &&
                    transitionBaselineWeights == null &&
                    selectionIncumbentAssetIds == null && selectionAvailabilityDate == null,
            )
            ReferencePortfolioActionKind.EXTRAORDINARY_REMOVAL -> {
                require(
                    addedAssetIds.isEmpty() && removedAssetIds.isNotEmpty() &&
                        corporateAction == null && weightReferenceMarketValues == null &&
                        transitionBaselineWeights == null &&
                        selectionIncumbentAssetIds == null && selectionAvailabilityDate == null,
                )
            }
            ReferencePortfolioActionKind.CONSTITUENT_MERGER -> require(
                corporateAction?.kind == ReferencePortfolioCorporateActionKind.MERGER &&
                    removedAssetIds.isNotEmpty() &&
                    hasReplacementWeightReferenceInput(orderedPositionIds) &&
                    transitionBaselineWeights == null &&
                    selectionIncumbentAssetIds == null &&
                    selectionAvailabilityDate == null,
            )
            ReferencePortfolioActionKind.SPIN_OFF_ADDITION -> require(
                corporateAction?.kind == ReferencePortfolioCorporateActionKind.SPIN_OFF &&
                    addedAssetIds == listOfNotNull(corporateAction.secondaryAssetId) &&
                    removedAssetIds.isEmpty() &&
                    hasValidWeightReferenceMarketValues(orderedPositionIds) &&
                    transitionBaselineWeights == null &&
                    selectionIncumbentAssetIds == null && selectionAvailabilityDate == null,
            )
            ReferencePortfolioActionKind.SPIN_OFF_REMOVAL -> require(
                corporateAction?.kind == ReferencePortfolioCorporateActionKind.SPIN_OFF &&
                    addedAssetIds.isEmpty() &&
                    removedAssetIds == listOfNotNull(corporateAction.secondaryAssetId) &&
                    weightReferenceMarketValues == null && selectionIncumbentAssetIds == null &&
                    transitionBaselineWeights == null &&
                    selectionAvailabilityDate == null,
            )
            ReferencePortfolioActionKind.TERMINAL_REMOVAL -> require(
                corporateAction?.kind == ReferencePortfolioCorporateActionKind.TERMINAL_REMOVAL &&
                    removedAssetIds == listOf(corporateAction.primaryAssetId) &&
                    hasReplacementWeightReferenceInput(orderedPositionIds) &&
                    transitionBaselineWeights == null &&
                    selectionIncumbentAssetIds == null &&
                    selectionAvailabilityDate == null,
            )
        }
    }

    private fun hasValidWeightReferenceMarketValues(orderedPositionIds: List<String>): Boolean =
        weightReferenceMarketValues?.let { marketValues ->
            marketValues.keys.toList() == orderedPositionIds &&
                marketValues.values.all { value -> value.isFinite() && value > 0.0 }
        } == true

    private fun hasReplacementWeightReferenceInput(orderedPositionIds: List<String>): Boolean =
        if (addedAssetIds.isEmpty()) {
            if (corporateAction != null && effectiveDate > corporateAction.effectiveDate) {
                hasValidWeightReferenceMarketValues(orderedPositionIds)
            } else {
                weightReferenceMarketValues == null
            }
        } else {
            hasValidWeightReferenceMarketValues(orderedPositionIds)
        }

    private fun hasValidConstraintWeightInput(orderedPositionIds: List<String>): Boolean =
        hasValidWeightReferenceMarketValues(orderedPositionIds) &&
            abs(requireNotNull(weightReferenceMarketValues).values.sum() - 1.0) <= WEIGHT_EPSILON

    private fun hasValidTransitionBaselineWeights(positionIds: Set<String>): Boolean =
        transitionBaselineWeights?.let { weights ->
            weights.isNotEmpty() &&
                weights.size <= ReferencePortfolioLimits.MAX_CONSTITUENTS &&
                weights.keys.toList() == weights.keys.sorted() &&
                weights.keys.all { assetId -> assetId in positionIds && ASSET_ID.matches(assetId) } &&
                weights.values.all { weight -> weight.isFinite() && weight > 0.0 } &&
                abs(weights.values.sum() - 1.0) <= WEIGHT_EPSILON
        } == true

    companion object {
        private const val WEIGHT_EPSILON: Double = 1e-8
        private val PLAN_ID = Regex("[A-Za-z0-9][A-Za-z0-9:._-]{0,511}")
        private val ASSET_ID = Regex("[A-Za-z0-9][A-Za-z0-9:._-]{0,199}")
        private val PORTFOLIO_ID = Regex("[a-z0-9][a-z0-9:._-]{2,199}")
    }
}
