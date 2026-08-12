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
    val positions: List<ReferencePortfolioPosition>,
    val addedAssetIds: List<String>,
    val removedAssetIds: List<String>,
) {
    init {
        require(PLAN_ID.matches(id))
        require(PORTFOLIO_ID.matches(portfolioId))
        require(portfolioId == ReferencePortfolioState.portfolioIdFor(benchmarkRef))
        require(selectionDate <= weightReferenceDate && weightReferenceDate < effectiveDate)
        require(positions.isNotEmpty() && positions.size <= EquityMethodologyProfile.MAX_CONSTITUENTS)
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
        val positionIds = positions.mapTo(hashSetOf(), ReferencePortfolioPosition::assetId)
        require(addedAssetIds.all(positionIds::contains))
        require(removedAssetIds.none(positionIds::contains))
        when (kind) {
            ReferencePortfolioActionKind.ANNUAL_RECONSTITUTION -> Unit
            ReferencePortfolioActionKind.QUARTERLY_REBALANCE,
            ReferencePortfolioActionKind.DAILY_CAP_REBALANCE,
            -> require(addedAssetIds.isEmpty() && removedAssetIds.isEmpty())
            ReferencePortfolioActionKind.EXTRAORDINARY_DELETION -> {
                require(addedAssetIds.isEmpty() && removedAssetIds.isNotEmpty())
            }
        }
    }

    companion object {
        private const val WEIGHT_EPSILON: Double = 1e-8
        private val PLAN_ID = Regex("[A-Za-z0-9][A-Za-z0-9:._-]{0,511}")
        private val ASSET_ID = Regex("[A-Za-z0-9][A-Za-z0-9:._-]{0,199}")
        private val PORTFOLIO_ID = Regex("[a-z0-9][a-z0-9:._-]{2,199}")
    }
}
